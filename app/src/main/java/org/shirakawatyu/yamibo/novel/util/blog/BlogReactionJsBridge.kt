package org.shirakawatyu.yamibo.novel.util.blog

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.shirakawatyu.yamibo.novel.global.GlobalData
import java.lang.ref.WeakReference

internal class BlogReactionJsBridge(webView: WebView) {
    private val webViewRef = WeakReference(webView)

    @JavascriptInterface
    fun load(ownerUid: String, blogId: String, requestId: String) {
        launchForCurrentBlog(ownerUid, blogId, requestId, allowOwnBlog = true) {
            runCatching {
                BlogReactionRemoteClient.fetchSnapshot(ownerUid, blogId)
            }.fold(
                onSuccess = { snapshot -> deliver(requestId, snapshotPayload(snapshot)) },
                onFailure = { error -> deliver(requestId, errorPayload(error)) }
            )
        }
    }

    @JavascriptInterface
    fun react(ownerUid: String, blogId: String, clickId: String, requestId: String) {
        launchForCurrentBlog(ownerUid, blogId, requestId) {
            runCatching {
                BlogReactionRemoteClient.addReaction(ownerUid, blogId, clickId)
            }.fold(
                onSuccess = { update ->
                    deliver(requestId, snapshotPayload(update.snapshot, update.message))
                },
                onFailure = { error -> deliver(requestId, errorPayload(error)) }
            )
        }
    }

    private fun launchForCurrentBlog(
        ownerUid: String,
        blogId: String,
        requestId: String,
        allowOwnBlog: Boolean = false,
        action: () -> Unit
    ) {
        val webView = webViewRef.get() ?: return
        webView.post {
            if (
                !allowOwnBlog &&
                GlobalData.currentUid.isNotBlank() &&
                GlobalData.currentUid == ownerUid
            ) {
                deliver(requestId, mapOf("error" to "不能给自己的日志表态"))
                return@post
            }
            if (!matchesCurrentMobileBlog(webView.url, ownerUid, blogId)) {
                deliver(requestId, mapOf("error" to "当前页面无法使用表态功能"))
                return@post
            }
            ioScope.launch { action() }
        }
    }

    private fun snapshotPayload(
        snapshot: BlogReactionSnapshot,
        message: String = ""
    ): Map<String, Any> = linkedMapOf(
        "totalCount" to snapshot.totalCount,
        "options" to snapshot.options.map { option ->
            linkedMapOf(
                "clickId" to option.clickId,
                "label" to option.label,
                "count" to option.count
            )
        },
        "message" to message
    )

    private fun errorPayload(error: Throwable): Map<String, String> = mapOf(
        "error" to (error.message?.takeIf(String::isNotBlank) ?: "表态功能暂时不可用")
    )

    private fun deliver(requestId: String, payload: Any) {
        val requestJson = JSON.toJSONString(requestId)
        val payloadJson = JSON.toJSONString(payload)
        webViewRef.get()?.post {
            webViewRef.get()?.evaluateJavascript(
                "window.__yamiboBlogReactionReceive && " +
                        "window.__yamiboBlogReactionReceive($requestJson, $payloadJson);",
                null
            )
        }
    }

    private companion object {
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun matchesCurrentMobileBlog(
            currentUrl: String?,
            ownerUid: String,
            blogId: String
        ): Boolean {
            val url = currentUrl?.toHttpUrlOrNull() ?: return false
            return url.host.equals("bbs.yamibo.com", ignoreCase = true) &&
                    url.encodedPath == "/home.php" &&
                    url.queryParameter("mod").equals("space", ignoreCase = true) &&
                    url.queryParameter("do").equals("blog", ignoreCase = true) &&
                    !url.queryParameter("mobile").equals("no", ignoreCase = true) &&
                    url.queryParameter("uid") == ownerUid &&
                    url.queryParameter("id") == blogId
        }
    }
}

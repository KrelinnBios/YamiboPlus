package org.shirakawatyu.yamibo.novel.util.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import com.alibaba.fastjson2.JSON
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.module.YamiboWebViewClient
import org.shirakawatyu.yamibo.novel.util.WebViewPool
import org.shirakawatyu.yamibo.novel.util.YamiboSession

data class BrowserExtraction(
    val payload: String,
    val url: String,
    val cookie: String
)

class ForumVerificationRequiredException(
    val targetUrl: String
) : IOException("论坛需要先完成网页验证")

/**
 * 以论坛页面为数据源的最小浏览器运行时。
 *
 * 不向页面新增 JavaScript bridge，只通过 evaluateJavascript 的返回值接收提取结果；
 * 主框架导航严格限制在 yamibo.com，Cookie/localStorage 继续由现有 WebView 会话维护。
 */
object YamiboBrowserEngine {
    private const val DEFAULT_TIMEOUT_MS = 20_000L
    private const val POLL_INTERVAL_MS = 350L

    suspend fun extract(
        context: Context,
        url: String,
        extractorScript: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): BrowserExtraction = withContext(Dispatchers.Main.immediate) {
        require(isAllowedForumUrl(url)) { "浏览器数据源拒绝访问非百合会地址" }

        val webView = WebViewPool.acquire(context)
        val rendererGone = AtomicBoolean(false)
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView as? ViewGroup
        var mainFrameError: String? = null
        var mainFrameNeedsVerification = false
        var finalUrl = url

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }

        if (decorView != null && webView.parent == null) {
            webView.visibility = View.INVISIBLE
            decorView.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        YamiboSession.syncToWebView(url)
        webView.webViewClient = object : YamiboWebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (request?.isForMainFrame == true && !isAllowedForumUrl(target)) {
                    mainFrameError = "页面跳转到了不受信任的地址"
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameError = error?.description?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: "论坛网页加载失败"
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (tryRecoverWaf405(view, request, errorResponse)) {
                    mainFrameError = null
                    mainFrameNeedsVerification = false
                    return
                }
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    mainFrameError = "论坛网页请求失败: HTTP $statusCode"
                    mainFrameNeedsVerification = statusCode == 405
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                rendererGone.set(true)
                (webView.parent as? ViewGroup)?.removeView(webView)
                WebViewPool.discard(webView)
                return true
            }
        }

        val startedAt = SystemClock.elapsedRealtime()
        try {
            webView.resumeTimers()
            webView.loadUrl(url)

            while (!rendererGone.get() && SystemClock.elapsedRealtime() - startedAt < timeoutMs) {
                delay(POLL_INTERVAL_MS)
                finalUrl = webView.url?.takeIf(String::isNotBlank) ?: finalUrl
                if (!isAllowedForumUrl(finalUrl)) {
                    throw IOException("页面跳转到了不受信任的地址")
                }
                val payload = withTimeoutOrNull(1_500L) {
                    webView.evaluateForString(extractorScript)
                }.orEmpty()
                if (payload.isBlank()) continue

                val envelope = runCatching { JSON.parseObject(payload) }.getOrNull() ?: continue
                when (envelope.getString("status")) {
                    "ready" -> return@withContext BrowserExtraction(
                        payload = payload,
                        url = finalUrl,
                        cookie = YamiboSession.cookieFor(finalUrl)
                    )
                    "verification" -> throw ForumVerificationRequiredException(finalUrl)
                    "error" -> throw IOException(
                        envelope.getString("message")?.takeIf(String::isNotBlank)
                            ?: "论坛页面无法解析"
                    )
                    "loading" -> if (mainFrameNeedsVerification) {
                        throw ForumVerificationRequiredException(finalUrl)
                    }
                }
            }

            if (rendererGone.get()) throw IOException("论坛网页渲染进程已退出")
            if (mainFrameNeedsVerification) {
                throw ForumVerificationRequiredException(finalUrl)
            }
            throw IOException(mainFrameError ?: "论坛网页数据提取超时")
        } finally {
            if (!rendererGone.get()) {
                webView.stopLoading()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.visibility = View.VISIBLE
                WebViewPool.release(webView)
            }
        }
    }

    internal fun isAllowedForumUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", ignoreCase = true) &&
            (host == "yamibo.com" || host.endsWith(".yamibo.com"))
    }.getOrDefault(false)

    private suspend fun WebView.evaluateForString(script: String): String =
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) { result ->
                if (!continuation.isActive) return@evaluateJavascript
                val value = runCatching { JSON.parse(result) as? String }
                    .getOrNull()
                    ?: result?.trim('"')
                        ?.replace("\\u003C", "<")
                        ?.replace("\\\"", "\"")
                        .orEmpty()
                continuation.resume(value)
            }
        }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

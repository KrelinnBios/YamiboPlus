package org.shirakawatyu.yamibo.novel.util.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.module.YamiboWebViewClient
import org.shirakawatyu.yamibo.novel.util.WebViewPool
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class AuthenticatedWebViewPage(
    val html: String,
    val url: String,
    val cookie: String
)

object AuthenticatedWebViewPageLoader {
    private const val DEFAULT_TIMEOUT_MS = 18_000L
    private const val POLL_INTERVAL_MS = 350L

    suspend fun fetch(
        context: Context,
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): AuthenticatedWebViewPage? = withContext(Dispatchers.Main.immediate) {
        val webView = WebViewPool.acquire(context)
        val rendererGone = AtomicBoolean(false)
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView as? ViewGroup

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }

        if (decorView != null && webView.parent == null) {
            decorView.addView(
                webView,
                FrameLayout.LayoutParams(1, 1).apply {
                    leftMargin = -10_000
                    topMargin = -10_000
                }
            )
        }

        YamiboSession.syncToWebView(url)
        webView.webViewClient = object : YamiboWebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    rendererGone.set(true)
                }
                super.onReceivedError(view, request, error)
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

        var latestHtml = ""
        var finalUrl = url
        val startTime = SystemClock.elapsedRealtime()

        try {
            webView.resumeTimers()
            webView.loadUrl(url)

            while (!rendererGone.get() && SystemClock.elapsedRealtime() - startTime < timeoutMs) {
                delay(POLL_INTERVAL_MS)
                finalUrl = webView.url?.takeIf(String::isNotBlank) ?: finalUrl
                val html = withTimeoutOrNull(1_500L) {
                    webView.outerHtml()
                }.orEmpty()
                if (html.isNotBlank()) {
                    latestHtml = html
                    if (isSettledForumPage(html)) {
                        return@withContext AuthenticatedWebViewPage(
                            html = html,
                            url = finalUrl,
                            cookie = YamiboSession.cookieFor(finalUrl)
                        )
                    }
                }
            }

            latestHtml.takeIf(String::isNotBlank)?.let {
                AuthenticatedWebViewPage(
                    html = it,
                    url = finalUrl,
                    cookie = YamiboSession.cookieFor(finalUrl)
                )
            }
        } finally {
            if (!rendererGone.get()) {
                webView.stopLoading()
                (webView.parent as? ViewGroup)?.removeView(webView)
                WebViewPool.release(webView)
            }
        }
    }

    private suspend fun WebView.outerHtml(): String {
        return suspendCancellableCoroutine { continuation ->
            evaluateJavascript(
                "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})();"
            ) { result ->
                if (!continuation.isActive) return@evaluateJavascript
                val html = runCatching { JSON.parse(result) as? String }
                    .getOrNull()
                    ?: result?.trim('"')
                        ?.replace("\\u003C", "<")
                        ?.replace("\\\"", "\"")
                        .orEmpty()
                continuation.resume(html)
            }
        }
    }

    private fun isSettledForumPage(html: String): Boolean {
        val normalized = html.lowercase()
        return normalized.contains("postmessage_") ||
            normalized.contains("id=\"thread_subject\"") ||
            normalized.contains("class=\"message") ||
            normalized.contains("阅读权限") ||
            normalized.contains("无权访问") ||
            normalized.contains("权限不足") ||
            normalized.contains("您所在的用户组") ||
            normalized.contains("formhash")
    }

    private tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}

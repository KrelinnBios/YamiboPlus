package org.shirakawatyu.yamibo.novel.util

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.shirakawatyu.yamibo.novel.YamiboApplication
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object Waf405RecoveryPolicy {
    const val CHALLENGE_URL =
        "https://bbs.yamibo.com/home.php?mod=space&do=profile&mycenter=1"
    const val CHALLENGE_TIMEOUT_MS = 18_000L
    const val RECENT_SUCCESS_GRACE_MS = 15_000L
    const val SAME_PAGE_RETRY_GUARD_MS = 30_000L

    fun shouldRecover(
        statusCode: Int,
        method: String,
        isMainFrame: Boolean,
        isYamiboUrl: Boolean
    ): Boolean = statusCode == 405 &&
            method.equals("GET", ignoreCase = true) &&
            isMainFrame &&
            isYamiboUrl

    fun shouldRefreshForResponse(statusCode: Int, method: String): Boolean =
        method.equals("GET", ignoreCase = true) && statusCode == 405

    fun hasRecentSuccess(lastSuccessMs: Long, nowMs: Long): Boolean =
        lastSuccessMs > 0L && nowMs - lastSuccessMs in 0..RECENT_SUCCESS_GRACE_MS

    fun isSamePageRetryGuarded(
        previousUrl: String?,
        previousAttemptMs: Long,
        url: String,
        nowMs: Long
    ): Boolean = previousUrl == url &&
            previousAttemptMs > 0L &&
            nowMs - previousAttemptMs in 0..SAME_PAGE_RETRY_GUARD_MS
}

/**
 * 仅在论坛明确返回 WAF 405 时，短暂加载一次个人主页以刷新 WebView 共享 Cookie。
 *
 * 不做启动或定时预热，避免挑战页和正常论坛页面争抢 WebView/网络资源。并发失败会合并为
 * 同一次挑战；完成后自动重试原请求，挑战页随即销毁。
 */
object Waf405RecoveryManager {
    private const val READY_POLL_INTERVAL_MS = 500L
    private const val CHALLENGE_405_RETRY_DELAY_MS = 600L

    private const val FORUM_READY_JS = """
        (function() {
            if (!document || !document.documentElement) return false;
            return !!(
                document.querySelector('meta[name="generator"][content*="Discuz"]') ||
                document.querySelector('input[name="formhash"]') ||
                document.getElementById('wp') ||
                document.getElementById('ct') ||
                document.querySelector('.threadlist')
            );
        })();
    """

    private data class VisibleRetry(
        val webViewRef: WeakReference<WebView>,
        val url: String
    )

    private class RefreshSignal {
        val latch = CountDownLatch(1)

        @Volatile
        var succeeded = false

        val visibleRetries = mutableListOf<VisibleRetry>()
    }

    private data class VisibleAttempt(val url: String, val atMs: Long)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val signalLock = Any()
    private val visibleAttempts = WeakHashMap<WebView, VisibleAttempt>()

    @Volatile
    private var ownerRef: WeakReference<Activity>? = null
    private var webView: WebView? = null
    private var readinessRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null
    private var pageGeneration = 0
    private var challenge405RetryCount = 0

    @Volatile
    private var lastSuccessfulRefreshMs = 0L

    @Volatile
    private var activeSignal: RefreshSignal? = null

    fun start(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { start(activity) }
            return
        }
        ownerRef = WeakReference(activity)
    }

    fun stop(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stop(activity) }
            return
        }
        if (ownerRef?.get() !== activity) return
        ownerRef = null
        completeRefresh(activeSignal, succeeded = false)
    }

    /** WebView 主文档 405：挑战成功或超时后自动重试原 URL 一次。 */
    fun recoverWebView(webView: WebView, failedUrl: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false

        val owner = ownerRef?.get()
            ?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return false
        val now = SystemClock.elapsedRealtime()
        val previous = visibleAttempts[webView]
        if (Waf405RecoveryPolicy.isSamePageRetryGuarded(
                previous?.url,
                previous?.atMs ?: 0L,
                failedUrl,
                now
            )
        ) {
            return false
        }
        visibleAttempts[webView] = VisibleAttempt(failedUrl, now)

        if (Waf405RecoveryPolicy.hasRecentSuccess(lastSuccessfulRefreshMs, now)) {
            runCatching { webView.stopLoading() }
            mainHandler.post { retryVisibleWebView(VisibleRetry(WeakReference(webView), failedUrl)) }
            return true
        }

        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal().also { activeSignal = it }
        }
        signal.visibleRetries += VisibleRetry(WeakReference(webView), failedUrl)
        if (webView === this.webView) return false
        if (this.webView == null && createHiddenWebView(owner) == null) {
            signal.visibleRetries.removeAll { it.webViewRef.get() === webView && it.url == failedUrl }
            synchronized(signalLock) {
                if (signal === activeSignal && signal.visibleRetries.isEmpty()) activeSignal = null
            }
            return false
        }
        if (signal === activeSignal && timeoutRunnable == null) beginRefresh(signal)
        runCatching { webView.stopLoading() }
        return true
    }

    /** OkHttp 405：后台线程等待同一次挑战，调用方据结果决定是否重放 GET。 */
    fun refreshAndWait(timeoutMs: Long = Waf405RecoveryPolicy.CHALLENGE_TIMEOUT_MS): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        if (Waf405RecoveryPolicy.hasRecentSuccess(
                lastSuccessfulRefreshMs,
                SystemClock.elapsedRealtime()
            )
        ) {
            return true
        }
        val owner = ownerRef?.get()
            ?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return false
        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal().also {
                activeSignal = it
                mainHandler.post {
                    if (it !== activeSignal) return@post
                    if (createHiddenWebView(owner) != null) {
                        if (timeoutRunnable == null) beginRefresh(it)
                    }
                    else completeRefresh(it, succeeded = false)
                }
            }
        }
        return try {
            signal.latch.await(timeoutMs, TimeUnit.MILLISECONDS) && signal.succeeded
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHiddenWebView(activity: Activity): WebView? {
        webView?.let { return it }
        val challengeUserAgent = runCatching {
            WebSettings.getDefaultUserAgent(activity)
        }.getOrDefault(RequestConfig.UA)
        YamiboApplication.systemUserAgent = challengeUserAgent

        val hiddenWebView = runCatching {
            WebView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1).apply {
                    leftMargin = -10_000
                    topMargin = -10_000
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    userAgentString = challengeUserAgent
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        pageGeneration++
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pollUntilForumPageReady(pageGeneration)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true) {
                            if (errorResponse?.statusCode == 405 && challenge405RetryCount == 0) {
                                challenge405RetryCount++
                                mainHandler.postDelayed({
                                    if (view === webView && activeSignal != null) {
                                        view?.loadUrl(
                                            Waf405RecoveryPolicy.CHALLENGE_URL,
                                            mapOf("Cache-Control" to "no-cache")
                                        )
                                    }
                                }, CHALLENGE_405_RETRY_DELAY_MS)
                            } else {
                                completeRefresh(activeSignal, succeeded = false)
                            }
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        completeRefresh(activeSignal, succeeded = false)
                        return true
                    }
                }
            }
        }.getOrNull() ?: return null
        val decorView = activity.window.decorView as? ViewGroup
        if (decorView == null) {
            runCatching { hiddenWebView.destroy() }
            return null
        }
        return runCatching {
            decorView.addView(hiddenWebView)
            webView = hiddenWebView
            hiddenWebView
        }.getOrElse {
            runCatching { hiddenWebView.destroy() }
            null
        }
    }

    private fun beginRefresh(signal: RefreshSignal) {
        if (signal !== activeSignal) return
        val target = webView ?: run {
            completeRefresh(signal, succeeded = false)
            return
        }

        cancelRefreshCallbacks()
        challenge405RetryCount = 0
        YamiboSession.syncToWebView(Waf405RecoveryPolicy.CHALLENGE_URL)
        runCatching {
            target.onResume()
            target.loadUrl(
                Waf405RecoveryPolicy.CHALLENGE_URL,
                mapOf("Cache-Control" to "no-cache")
            )
        }.onFailure {
            completeRefresh(signal, succeeded = false)
            return
        }

        timeoutRunnable = Runnable {
            completeRefresh(signal, succeeded = false)
        }.also {
            mainHandler.postDelayed(it, Waf405RecoveryPolicy.CHALLENGE_TIMEOUT_MS)
        }
    }

    private fun pollUntilForumPageReady(expectedGeneration: Int) {
        val signal = activeSignal ?: return
        val target = webView ?: return
        readinessRunnable?.let(mainHandler::removeCallbacks)
        readinessRunnable = Runnable {
            if (signal !== activeSignal || target !== webView) return@Runnable
            if (expectedGeneration != pageGeneration) {
                pollUntilForumPageReady(pageGeneration)
                return@Runnable
            }
            runCatching {
                target.evaluateJavascript(FORUM_READY_JS) { result ->
                    if (signal !== activeSignal || target !== webView) return@evaluateJavascript
                    if (result.equals("true", ignoreCase = true)) {
                        runCatching { android.webkit.CookieManager.getInstance().flush() }
                        completeRefresh(signal, succeeded = true)
                    } else {
                        pollUntilForumPageReady(pageGeneration)
                    }
                }
            }.onFailure {
                pollUntilForumPageReady(pageGeneration)
            }
        }.also {
            mainHandler.postDelayed(it, READY_POLL_INTERVAL_MS)
        }
    }

    private fun completeRefresh(signal: RefreshSignal?, succeeded: Boolean) {
        if (signal == null) {
            destroyHiddenWebView()
            return
        }
        val retries = synchronized(signalLock) {
            if (signal !== activeSignal) return
            signal.succeeded = succeeded
            if (succeeded) lastSuccessfulRefreshMs = SystemClock.elapsedRealtime()
            activeSignal = null
            signal.visibleRetries.toList()
        }
        cancelRefreshCallbacks()
        runCatching { android.webkit.CookieManager.getInstance().flush() }
        signal.latch.countDown()
        destroyHiddenWebView()
        retries.forEach(::retryVisibleWebView)
    }

    private fun retryVisibleWebView(retry: VisibleRetry) {
        val target = retry.webViewRef.get() ?: return
        runCatching {
            target.stopLoading()
            target.loadUrl(retry.url, mapOf("Cache-Control" to "no-cache"))
        }
    }

    private fun cancelRefreshCallbacks() {
        readinessRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        readinessRunnable = null
        timeoutRunnable = null
    }

    private fun destroyHiddenWebView() {
        val target = webView ?: return
        webView = null
        runCatching {
            target.stopLoading()
            (target.parent as? ViewGroup)?.removeView(target)
            target.removeAllViews()
            target.destroy()
        }
        pageGeneration = 0
        challenge405RetryCount = 0
    }
}

package org.shirakawatyu.yamibo.novel.util

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object Waf405RecoveryPolicy {
    /**
     * 与 YamiboReaderLite 一致：用固定的个人主页作为挑战 URL。
     * 论坛的 WAF 对 plugin.php 这类敏感页面才会返回 405 挑战，对普通首页不会，
     * 所以让隐藏 WebView 加载首页既不会触发新的挑战，又能同步 Set-Cookie。
     */
    const val CHALLENGE_URL =
        "https://bbs.yamibo.com/home.php?mod=space&do=profile&mycenter=1"
    const val CHALLENGE_TIMEOUT_MS = 18_000L
    const val RECENT_SUCCESS_GRACE_MS = 15_000L
    const val SAME_PAGE_RETRY_GUARD_MS = 30_000L
    // 挑战失败后的冷却期，期间不再发起新挑战，避免「失败 → 重试 → 再失败」打爆服务器和日志。
    const val FAILED_CHALLENGE_COOLDOWN_MS = 30_000L

    fun shouldRecover(
        statusCode: Int,
        method: String,
        isMainFrame: Boolean,
        isYamiboUrl: Boolean,
        isSignPage: Boolean = false
    ): Boolean = !isSignPage && statusCode == 405 &&
            method.equals("GET", ignoreCase = true) &&
            isMainFrame &&
            isYamiboUrl

    /**
     * 任何 405 GET 都触发 WAF 恢复：不再检查响应体是否含挑战标记，
     * 与 YamiboReaderLite 一致，避免标记列表更新滞后导致恢复不触发。
     */
    fun shouldRefreshForResponse(
        statusCode: Int,
        method: String,
        isSignPage: Boolean = false
    ): Boolean = !isSignPage && method.equals("GET", ignoreCase = true) && statusCode == 405

    /**
     * 签到页（plugin.php?id=zqlj_sign）的 CF 验证需要用户在真实可见的页面里完成，
     * 隐藏 WebView 永远拿不到 cf_clearance/nox 等凭证。命中该 URL 时跳过隐藏挑战，
     * 由调用方按「WAF 拦截，需在可见页面过验证」处理。
     */
    fun isSignPageUrl(url: String): Boolean = url.contains("zqlj_sign", ignoreCase = true)

    fun hasRecentSuccess(lastSuccessMs: Long, nowMs: Long): Boolean =
        lastSuccessMs > 0L && nowMs - lastSuccessMs in 0..RECENT_SUCCESS_GRACE_MS

    fun isFailedChallengeCoolingDown(lastFailedMs: Long, nowMs: Long): Boolean =
        lastFailedMs > 0L && nowMs - lastFailedMs in 0..FAILED_CHALLENGE_COOLDOWN_MS

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
 * 与 YamiboReaderLite 一致：405 时短暂加载个人主页以刷新共享 Cookie。
 *
 * 启动 / 定时预热会让挑战页与正常论坛页面争资源，本类不做任何主动预热。
 * 隐藏 WebView 用 1×1 像素 + 负 margin 移出屏幕，加载不加载图片以加速，
 * 页面就绪标志是页面渲染出 Discuz 页面骨架（formhash / #wp / .threadlist 等），
 * 真正的成功冷却只在调用方重放原请求成功后记录。
 */
object Waf405RecoveryManager {
    private const val READY_POLL_INTERVAL_MS = 500L
    private const val CHALLENGE_405_RETRY_DELAY_MS = 600L
    private const val FORUM_ORIGIN = "https://bbs.yamibo.com/"

    /**
     * 判定论坛首页渲染完毕、可以认为 WAF 凭证已经下发的探针：
     * 任一典型 Discuz 元素出现即可。WebView 加载完成后页面 JS 一定能跑出结果。
     */
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

    private class RefreshSignal(
        val userAgent: String?
    ) {
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

    /** 最近一次挑战失败的时间，用于失败冷却，避免反复打服务器和日志。 */
    @Volatile
    private var lastChallengeFailedMs = 0L

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
        if (!isAllowedYamiboUrl(failedUrl)) return false

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

        if (Waf405RecoveryPolicy.isFailedChallengeCoolingDown(lastChallengeFailedMs, now)) {
            return false
        }

        if (Waf405RecoveryPolicy.hasRecentSuccess(lastSuccessfulRefreshMs, now)) {
            runCatching { webView.stopLoading() }
            mainHandler.post {
                retryVisibleWebView(VisibleRetry(WeakReference(webView), failedUrl))
            }
            return true
        }

        val requestUserAgent = webView.settings.userAgentString
            ?.takeIf(String::isNotBlank)
        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal(requestUserAgent).also { activeSignal = it }
        }
        signal.visibleRetries += VisibleRetry(WeakReference(webView), failedUrl)
        if (webView === this.webView) return false
        if (this.webView == null && createHiddenWebView(owner, signal.userAgent) == null) {
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

    /**
     * OkHttp 405：后台线程等待同一次挑战，调用方据结果决定是否重放 GET。
     *
     * 注：被拦 URL 在本类内部被忽略——我们用固定的主页作为挑战 URL，让隐藏 WebView
     * 加载不受 WAF 保护的普通页面来同步 cookie，避免在敏感 URL 上反复触发挑战。
     */
    fun refreshAndWait(
        challengeUrl: String? = null,
        userAgent: String? = null,
        timeoutMs: Long = Waf405RecoveryPolicy.CHALLENGE_TIMEOUT_MS
    ): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        if (Waf405RecoveryPolicy.hasRecentSuccess(
                lastSuccessfulRefreshMs,
                SystemClock.elapsedRealtime()
            )
        ) {
            return true
        }
        if (Waf405RecoveryPolicy.isFailedChallengeCoolingDown(
                lastChallengeFailedMs,
                SystemClock.elapsedRealtime()
            )
        ) {
            return false
        }
        val owner = ownerRef?.get()
            ?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: run {
                AppErrorLog.record("WAF 挑战跳过：无可用前台页面")
                return false
            }
        val requestUserAgent = userAgent?.takeIf(String::isNotBlank)
        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal(requestUserAgent).also {
                activeSignal = it
                mainHandler.post {
                    if (it !== activeSignal) return@post
                    if (createHiddenWebView(owner, it.userAgent) != null) {
                        if (timeoutRunnable == null) beginRefresh(it)
                    } else {
                        completeRefresh(it, succeeded = false)
                    }
                }
            }
        }
        return try {
            val completed = signal.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                // 不能让短时调用者超时后留下一个仍在后台运行的挑战
                mainHandler.post {
                    if (signal === activeSignal) {
                        completeRefresh(signal, succeeded = false)
                    }
                }
            }
            completed && signal.succeeded
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /** Record the result of the original request replay, not merely challenge-page readiness. */
    internal fun recordReplayResult(succeeded: Boolean) {
        if (succeeded) {
            lastSuccessfulRefreshMs = SystemClock.elapsedRealtime()
            lastChallengeFailedMs = 0L
        } else {
            lastSuccessfulRefreshMs = 0L
            lastChallengeFailedMs = SystemClock.elapsedRealtime()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHiddenWebView(activity: Activity, userAgent: String? = null): WebView? {
        webView?.let { return it }

        val challengeUserAgent = userAgent
            ?.takeIf(String::isNotBlank)
            ?: runCatching {
                WebSettings.getDefaultUserAgent(activity)
            }.getOrDefault(RequestConfig.UA)

        val hiddenWebView = runCatching {
            WebView(activity).apply {
                // 与 YamiboReaderLite 一致：1×1 像素 + 负 margin 推到屏幕外，
                // 让 nox/CF 中间件认为「可见但不可见」，不影响 JS 执行，
                // 也避免遮挡用户界面。
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
                        AppErrorLog.record("WAF 挑战页面开始: ${url}")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        AppErrorLog.record("WAF 挑战页面完成: ${url}")
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

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            completeRefresh(activeSignal, succeeded = false)
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
            if (!succeeded) {
                lastChallengeFailedMs = SystemClock.elapsedRealtime()
            }
            activeSignal = null
            signal.visibleRetries.toList()
        }
        AppErrorLog.record(if (succeeded) "WAF 挑战页面就绪" else "WAF 挑战失败或超时")
        cancelRefreshCallbacks()
        runCatching { android.webkit.CookieManager.getInstance().flush() }
        if (succeeded) {
            syncWafCookieToStore()
        }
        signal.latch.countDown()
        destroyHiddenWebView()
        retries.forEach(::retryVisibleWebView)
    }

    private fun syncWafCookieToStore() {
        try {
            val cm = android.webkit.CookieManager.getInstance()
            val urls = listOf(
                Waf405RecoveryPolicy.CHALLENGE_URL,
                "https://bbs.yamibo.com/home.php",
                "https://bbs.yamibo.com/"
            )
            val targetUrl = "https://bbs.yamibo.com/".toHttpUrl()

            for (url in urls) {
                val cookieString = cm.getCookie(url).orEmpty()
                if (cookieString.isNotBlank()) {
                    YamiboRetrofit.sharedWafCookieStore.captureFromCookieString(
                        targetUrl,
                        cookieString
                    )
                }
            }
        } catch (_: Exception) {}
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

    private fun isAllowedYamiboUrl(rawUrl: String?): Boolean {
        val uri = rawUrl?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("bbs.yamibo.com", ignoreCase = true)
    }
}

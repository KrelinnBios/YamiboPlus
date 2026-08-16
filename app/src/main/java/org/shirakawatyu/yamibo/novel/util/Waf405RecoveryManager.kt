package org.shirakawatyu.yamibo.novel.util

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.shirakawatyu.yamibo.novel.YamiboApplication
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object Waf405RecoveryPolicy {
    const val CHALLENGE_TIMEOUT_MS = 45_000L
    const val RECENT_SUCCESS_GRACE_MS = 15_000L
    const val SAME_PAGE_RETRY_GUARD_MS = 30_000L
    const val NOX_COOKIE_NAME = "nox_jst_v1"
    // 论坛挑战凭证约 30 分钟过期；在还剩约 5 分钟余量时按需换新。
    const val NOX_RENEW_INTERVAL_MS = 25L * 60L * 1000L
    const val NOX_RENEW_TIMEOUT_MS = 12_000L
    // 挑战失败或换新凭证未被服务端接受后的冷却期：期间不再发起新挑战，
    // 防止“挑战失败 → 重试 → 再失败”的紧密循环把服务器和日志都打爆。
    const val FAILED_CHALLENGE_COOLDOWN_MS = 30_000L

    private val challengeBodyMarkers = listOf("__noxexpire", "/nox_", "gangplank_")

    enum class PostRefreshAction(val errorLog: String?) {
        CONTINUE(null),
        INVALIDATE_AFTER_PROBE_FAILURE("WAF 探测未通过，等待下次挑战"),
        INVALIDATE_AFTER_REPLAY_CHALLENGE("WAF 重放仍被拦截，已清凭证");

        val shouldInvalidateNox: Boolean
            get() = this != CONTINUE
    }

    fun shouldRecover(
        statusCode: Int,
        method: String,
        isMainFrame: Boolean,
        isYamiboUrl: Boolean
    ): Boolean = statusCode == 405 &&
            method.equals("GET", ignoreCase = true) &&
            isMainFrame &&
            isYamiboUrl

    fun shouldRefreshForResponse(
        statusCode: Int,
        method: String,
        bodyPreview: String
    ): Boolean {
        if (!method.equals("GET", ignoreCase = true) || statusCode != 405) return false
        val normalizedBody = bodyPreview.lowercase(Locale.ROOT)
        return challengeBodyMarkers.any(normalizedBody::contains)
    }

    /** 挑战后仅在探测失败或重放仍为 NOX 挑战时清除刚换新的凭证。 */
    fun postRefreshAction(
        probeSucceeded: Boolean,
        replayStatusCode: Int? = null,
        replayBodyPreview: String = ""
    ): PostRefreshAction {
        if (!probeSucceeded) return PostRefreshAction.INVALIDATE_AFTER_PROBE_FAILURE
        return if (replayStatusCode != null && shouldRefreshForResponse(
                replayStatusCode,
                "GET",
                replayBodyPreview
            )
        ) {
            PostRefreshAction.INVALIDATE_AFTER_REPLAY_CHALLENGE
        } else {
            PostRefreshAction.CONTINUE
        }
    }

    fun withoutNoxCookie(cookieHeader: String): String =
        cookieHeader.split(';')
            .map(String::trim)
            .filter { cookie ->
                !cookie.substringBefore('=').equals(NOX_COOKIE_NAME, ignoreCase = true)
            }
            .filter(String::isNotEmpty)
            .joinToString("; ")

    fun extractNoxCookieValue(cookieHeader: String): String? =
        cookieHeader.split(';')
            .map(String::trim)
            .firstNotNullOfOrNull { cookie ->
                val separator = cookie.indexOf('=')
                if (separator <= 0 ||
                    !cookie.substring(0, separator).equals(NOX_COOKIE_NAME, ignoreCase = true)
                ) {
                    null
                } else {
                    cookie.substring(separator + 1).takeIf(String::isNotBlank)
                }
            }

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
 * 仅在论坛明确返回 NOX WAF 405 时，通过一个短暂的隐藏 WebView 执行同地址的挑战脚本。
 *
 * 挑战成功的标志是共享 Cookie 中出现 nox_jst_v1，而不是等待论坛页面完整渲染。
 * 并发失败会合并为同一次挑战；完成后原 GET 最多自动重试一次。
 */
object Waf405RecoveryManager {
    private const val COOKIE_POLL_INTERVAL_MS = 250L
    private const val FORUM_ORIGIN = "https://bbs.yamibo.com/"

    private data class VisibleRetry(
        val webViewRef: WeakReference<WebView>,
        val url: String
    )

    private class RefreshSignal(
        val challengeUrl: String,
        val userAgent: String
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
    private var cookiePollRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null

    /** WebView 内部 document.cookie 读取到的最新值，用于捕获 HttpOnly 的 nox cookie。 */
    private val noxCookieFromJs = java.util.concurrent.atomic.AtomicReference<String?>(null)

    @Volatile
    private var lastSuccessfulRefreshMs = 0L

    /** 最近一次挑战失败（或换新凭证未被服务端接受）的时间，用于失败冷却。 */
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
        if (Looper.myLooper() != Looper.getMainLooper() || !isAllowedYamiboUrl(failedUrl)) {
            return false
        }

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
            ?: defaultUserAgent()
        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal(failedUrl, requestUserAgent).also { activeSignal = it }
        }
        signal.visibleRetries += VisibleRetry(WeakReference(webView), failedUrl)
        if (webView === this.webView) return false
        if (this.webView == null && createHiddenWebView(owner, signal) == null) {
            signal.visibleRetries.removeAll {
                it.webViewRef.get() === webView && it.url == failedUrl
            }
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
     * 在真正要访问论坛内容前调用：只有当 nox 凭证已接近过期时才静默换新一次。
     * 不轮询、无后台任务；每次换新成功后至少安静 25 分钟。
     */
    fun ensureFreshNox(
        challengeUrl: String,
        userAgent: String,
        timeoutMs: Long = Waf405RecoveryPolicy.NOX_RENEW_TIMEOUT_MS
    ): Boolean {
        if (!isAllowedYamiboUrl(challengeUrl)) return false
        if (isNoxFresh()) return true
        // 冷启动时不要在真正请求前额外发起挑战：没有 nox 时，交给 405 响应
        // 触发恢复；已有 nox 时也先信任它，避免重复挑战加重论坛负担。
        // 真过期时仍由既有 405 恢复路径兜底。
        if (lastSuccessfulRefreshMs == 0L) return false
        return refreshAndWait(challengeUrl, userAgent, timeoutMs)
    }

    fun isNoxFresh(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val last = lastSuccessfulRefreshMs
        return last > 0L && nowMs - last < Waf405RecoveryPolicy.NOX_RENEW_INTERVAL_MS
    }

    /**
     * 探测/重放确认新凭证并未被服务端接受时调用：清零成功时间戳并进入失败冷却，
     * 避免 15 秒宽限把无效凭证当成“刚换过新”，导致连续刷新全部白重试。
     */
    fun markNoxUnverified() {
        lastSuccessfulRefreshMs = 0L
        lastChallengeFailedMs = SystemClock.elapsedRealtime()
    }

    /** OkHttp 线程等待同一次挑战，由调用方根据结果决定是否重放原 GET。 */
    fun refreshAndWait(
        challengeUrl: String,
        userAgent: String,
        timeoutMs: Long = Waf405RecoveryPolicy.CHALLENGE_TIMEOUT_MS
    ): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper() || !isAllowedYamiboUrl(challengeUrl)) {
            return false
        }
        if (Waf405RecoveryPolicy.hasRecentSuccess(
                lastSuccessfulRefreshMs,
                SystemClock.elapsedRealtime()
            )
        ) {
            return true
        }
        // 上次挑战失败/凭证未被接受后的冷却期内不再发起新挑战，快速失败避免打爆服务器。
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
        val signal = synchronized(signalLock) {
            activeSignal ?: RefreshSignal(
                challengeUrl = challengeUrl,
                userAgent = userAgent.ifBlank(::defaultUserAgent)
            ).also {
                activeSignal = it
                mainHandler.post {
                    if (it !== activeSignal) return@post
                    if (createHiddenWebView(owner, it) != null) {
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
                // 不能让短时调用者超时后留下一个仍在后台运行的挑战，
                // 否则后续 405 请求会全部排队到旧挑战结束。
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHiddenWebView(activity: Activity, signal: RefreshSignal): WebView? {
        webView?.let { return it }

        val hiddenWebView = runCatching {
            WebView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // 与 yamibo-app 的 PlatformNoxWebView 一致：全屏、真实可见渲染。
                // nox 反爬脚本会检测 document.visibilityState，任何隐藏/平移/遮挡
                // 都会被判定为可疑页面而拒绝下发凭证。挑战期间短暂显示挑战页，
                // 拿到 cookie 后立即移除（通常 1~3 秒）。
                visibility = View.VISIBLE
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = signal.userAgent
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = request?.isForMainFrame == true &&
                            !isAllowedYamiboUrl(request.url?.toString())

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        AppErrorLog.record("WAF 挑战页面开始: ${url}")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        AppErrorLog.record("WAF 挑战页面完成: ${url}")
                        // API 端点通过验证后会直接把 JSON 响应渲染成白底文本。
                        // 这已经是服务端放行的明确信号，不能让验证 WebView继续盖住 App。
                        view?.evaluateJavascript(
                            "document.body ? document.body.innerText : ''"
                        ) { value ->
                            val body = value.orEmpty()
                                .replace("\\\"", "\"")
                                .replace("\\n", " ")
                            if (body.contains("\"Version\"") &&
                                body.contains("\"Variables\"") &&
                                url?.contains("bbs.yamibo.com") == true
                            ) {
                                AppErrorLog.record("WAF 挑战验证通过：API 响应已返回")
                                completeRefresh(activeSignal, succeeded = true)
                            }
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true &&
                            errorResponse?.statusCode != 405
                        ) {
                            completeRefresh(activeSignal, succeeded = false)
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
            // 放在应用内容底层：保持 VISIBLE 和全屏渲染，让 nox 正常执行，
            // 但不会把 API JSON/挑战页面闪给用户。
            decorView.addView(hiddenWebView, 0)
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
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val cookiesWithoutNox = Waf405RecoveryPolicy.withoutNoxCookie(
            YamiboSession.cookieFor(signal.challengeUrl)
        )
        runCatching {
            cookieManager.setCookie(
                FORUM_ORIGIN,
                Waf405RecoveryPolicy.NOX_COOKIE_NAME + "=; Max-Age=0; Path=/; Secure"
            )
            cookiesWithoutNox.split(';')
                .map(String::trim)
                .filter { it.contains('=') }
                .forEach { cookie ->
                    cookieManager.setCookie(FORUM_ORIGIN, "$cookie; Path=/; Secure")
                }
            cookieManager.flush()
            target.onResume()
            // 与 yamibo-app 一致：直接加载触发挑战的原始 URL。
            // nox 中间件对该 URL 返回挑战页，脚本执行后设置域名级 cookie，
            // 对所有请求生效。换成论坛首页会被 302 到 misc.php，破坏挑战上下文。
            target.loadUrl(signal.challengeUrl, mapOf("Cache-Control" to "no-cache"))
            AppErrorLog.record("WAF 挑战开始：${signal.challengeUrl}")
            pollForNoxCookie(signal)
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

    private fun pollForNoxCookie(signal: RefreshSignal) {
        val target = webView ?: return
        cookiePollRunnable?.let(mainHandler::removeCallbacks)
        cookiePollRunnable = object : Runnable {
            override fun run() {
                if (signal !== activeSignal || target !== webView) return
                val currentCookies = runCatching {
                    CookieManager.getInstance().getCookie(FORUM_ORIGIN).orEmpty()
                }.getOrDefault("")
                val jsCookie = noxCookieFromJs.get()
                if (Waf405RecoveryPolicy.extractNoxCookieValue(currentCookies) != null ||
                    (jsCookie?.let { Waf405RecoveryPolicy.extractNoxCookieValue(it) } != null)
                ) {
                    // 如果 CookieManager 没读到但 JS 读到了，把它写回 CookieManager 让后续请求可用。
                    if (Waf405RecoveryPolicy.extractNoxCookieValue(currentCookies) == null &&
                        jsCookie != null
                    ) {
                        val noxValue = Waf405RecoveryPolicy.extractNoxCookieValue(jsCookie)
                        if (noxValue != null) {
                            runCatching {
                                CookieManager.getInstance().setCookie(
                                    FORUM_ORIGIN,
                                    "${Waf405RecoveryPolicy.NOX_COOKIE_NAME}=$noxValue; Path=/; Secure"
                                )
                            }
                        }
                    }
                    runCatching { target.stopLoading() }
                    runCatching { CookieManager.getInstance().flush() }
                    completeRefresh(signal, succeeded = true)
                } else {
                    // 每次轮询都读一次 WebView 内部 document.cookie：
                    // nox JS 可能在页面加载后才异步设置 cookie，只在 onPageFinished 读一次会错过。
                    runCatching {
                        target.evaluateJavascript("document.cookie") { value ->
                            val cookie = value?.trim('"').orEmpty()
                            if (cookie.contains("nox_jst_v1=")) {
                                noxCookieFromJs.set(cookie)
                            }
                        }
                    }
                    mainHandler.postDelayed(this, COOKIE_POLL_INTERVAL_MS)
                }
            }
        }.also(mainHandler::post)
    }

    private fun completeRefresh(signal: RefreshSignal?, succeeded: Boolean) {
        if (signal == null) {
            destroyHiddenWebView()
            return
        }
        val retries = synchronized(signalLock) {
            if (signal !== activeSignal) return
            signal.succeeded = succeeded
            if (succeeded) {
                lastSuccessfulRefreshMs = SystemClock.elapsedRealtime()
            } else {
                lastChallengeFailedMs = SystemClock.elapsedRealtime()
            }
            activeSignal = null
            signal.visibleRetries.toList()
        }
        AppErrorLog.record(if (succeeded) "WAF 挑战成功" else "WAF 挑战失败或超时")
        cancelRefreshCallbacks()
        runCatching { CookieManager.getInstance().flush() }
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
        cookiePollRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        cookiePollRunnable = null
        timeoutRunnable = null
    }

    private fun destroyHiddenWebView() {
        val target = webView ?: return
        webView = null
        noxCookieFromJs.set(null)
        runCatching {
            target.stopLoading()
            (target.parent as? ViewGroup)?.removeView(target)
            target.removeAllViews()
            target.destroy()
        }
    }

    private fun defaultUserAgent(): String =
        YamiboApplication.systemUserAgent.ifBlank { RequestConfig.UA }

    private fun isAllowedYamiboUrl(rawUrl: String?): Boolean {
        val uri = rawUrl?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("bbs.yamibo.com", ignoreCase = true)
    }
}

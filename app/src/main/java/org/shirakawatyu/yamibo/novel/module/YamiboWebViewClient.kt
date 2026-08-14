package org.shirakawatyu.yamibo.novel.module

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.PageJsScripts
import org.shirakawatyu.yamibo.novel.util.Waf405RecoveryManager
import org.shirakawatyu.yamibo.novel.util.Waf405RecoveryPolicy
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionJsBridge
import org.shirakawatyu.yamibo.novel.util.blog.MobileBlogJsScripts

open class YamiboWebViewClient : WebViewClient() {

    companion object {
        private val pendingNames = ConcurrentHashMap<String, String>()
        @Volatile private var userSelectedDesktopTemplate = false

        private fun normalizeAttachmentAid(aid: String?): String? {
            if (aid.isNullOrBlank()) return null

            // Discuz 的 aid 是带签名和时间戳的 Base64 字符串，例如：
            // 1264306|8b054faf|1781066499|655106|547818
            // 同一个附件在跳转或刷新后可能得到新的签名，因此只使用稳定的附件编号作缓存键。
            val cleaned = aid.replace(' ', '+')
            return try {
                String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
                    .substringBefore('|')
                    .takeIf { it.isNotBlank() }
                    ?: cleaned
            } catch (_: IllegalArgumentException) {
                cleaned
            }
        }

        private fun attachmentKey(url: String): String? {
            return normalizeAttachmentAid(Uri.parse(url).getQueryParameter("aid"))
        }

        class AttachmentNameBridge {
            @JavascriptInterface
            fun setAttachmentName(url: String, name: String) {
                val key = attachmentKey(url) ?: return
                pendingNames[key] = name
            }
        }

        fun setupDownloadListener(webView: WebView) {
            webView.addJavascriptInterface(AttachmentNameBridge(), "__yamiboAttach")
            webView.addJavascriptInterface(BlogReactionJsBridge(webView), "AndroidBlogReaction")
            webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val aid = Uri.parse(url).getQueryParameter("aid")
                    val attachmentId = normalizeAttachmentAid(aid)
                    val guessed = URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimeType?.takeIf { it.isNotBlank() } ?: "text/plain"
                    )
                    val ext = mimeType?.let {
                        android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
                    }?.takeIf { it.isNotEmpty() }
                    val fileName = when {
                        !guessed.equals("forum.php", ignoreCase = true) &&
                                !guessed.equals("forum.php.txt", ignoreCase = true) -> guessed
                        aid != null -> attachmentId?.let { pendingNames.remove(it) }?.let { name ->
                                if (name.contains('.') && name.lastIndexOf('.') > name.lastIndexOf('/')) name
                                else ext?.let { "$name.$it" } ?: name
                            }
                            ?: "yamibo_${attachmentId ?: aid}" + (ext?.let { ".$it" } ?: "")
                        else -> "yamibo_${System.currentTimeMillis()}" + (ext?.let { ".$it" } ?: "")
                    }
                    DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimeType?.takeIf { it.isNotBlank() } ?: "text/plain")
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                        addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                        addRequestHeader("Referer", webView.url ?: "https://bbs.yamibo.com/")
                        addRequestHeader("Accept", "text/plain,application/octet-stream,*/*")
                        setTitle(fileName)
                        setDescription("正在下载附件")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }.let {
                        val dm = webView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(it)
                    }
                } catch (_: Exception) {}
            }
        }

        private val ATTACH_INTERCEPT_JS = """
            (function() {
                function rememberAttachment(link) {
                    if (!link || !window.__yamiboAttach) return;
                    var span = link.querySelector('.link.f_b');
                    var name = span && span.textContent ? span.textContent.trim() : '';
                    if (name) window.__yamiboAttach.setAttachmentName(link.href, name);
                }

                // 页面完成后先缓存一次，避免点击与 DownloadListener 回调之间的时序竞争。
                document.querySelectorAll('a[href*="mod=attachment"]').forEach(rememberAttachment);

                if (window.__yamiboAttachHooked) return;
                window.__yamiboAttachHooked = true;
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var link = target && target.closest
                        ? target.closest('a[href*="mod=attachment"]')
                        : null;
                    rememberAttachment(link);
                }, true);
            })();
        """.trimIndent()
    }

    private var currentCookie = ""
    private var observedSessionGeneration = GlobalData.sessionGeneration
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val themeFlashHandler = Handler(Looper.getMainLooper())
    private var themeFlashRevealRunnable: Runnable? = null
    private var isSuppressingThemeFlash = false
    private var suppressedThemeFlashUrl: String? = null
    @Volatile private var currentPageUsesDesktopTemplate = false

    init {
        scope.launch {
            currentCookie = GlobalData.cookieFlow.first()
        }
    }

    private fun isYamiboForumUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "bbs.yamibo.com" || host == "m.yamibo.com" ||
                host == "www.yamibo.com" || host == "yamibo.com"
    }

    private fun documentBase(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.substringBefore("#").removeSuffix("/")
    }

    private fun isSameDocumentBase(firstUrl: String?, secondUrl: String?): Boolean {
        val firstBase = documentBase(firstUrl) ?: return false
        val secondBase = documentBase(secondUrl) ?: return false
        return firstBase == secondBase
    }

    private fun isSameDocumentNavigation(currentUrl: String?, nextUrl: String?): Boolean {
        return isSameDocumentBase(currentUrl, nextUrl) &&
                currentUrl?.substringAfter("#", "") != nextUrl?.substringAfter("#", "")
    }

    private fun shouldSuppressThemeFlash(view: WebView?, url: String?): Boolean {
        // 只遮住 WebView 首帧，不接管 URL；findpost 重定向仍由 WebView 原生处理以保留楼层定位。
        if (!GlobalData.isDarkMode.value) return false
        if (url.isNullOrBlank() || url == "about:blank" || url.startsWith("data:") || url.contains("warmup=true")) return false
        if (!isYamiboForumUrl(url)) return false
        val currentUrl = try {
            view?.url
        } catch (_: Throwable) {
            null
        }
        return !isSameDocumentNavigation(currentUrl, url)
    }

    private fun suppressThemeFlashIfNeeded(view: WebView?, url: String?) {
        themeFlashRevealRunnable?.let { themeFlashHandler.removeCallbacks(it) }
        themeFlashRevealRunnable = null
        if (!shouldSuppressThemeFlash(view, url)) {
            revealThemeFlashSuppression(view, delayMs = 0L)
            return
        }
        isSuppressingThemeFlash = true
        suppressedThemeFlashUrl = url
        view?.animate()?.cancel()
        view?.setBackgroundColor(0xFF0D141D.toInt())
        view?.alpha = 0f
    }

    private fun injectCurrentTheme(view: WebView?, currentUrl: String?, onComplete: (() -> Unit)? = null) {
        if (!PageJsScripts.shouldApplyWebTheme(GlobalData.appTheme.value)) {
            onComplete?.invoke()
            return
        }
        val themeUrl = currentUrl ?: try {
            view?.url
        } catch (_: Throwable) {
            null
        }
        if (!isYamiboForumUrl(themeUrl)) {
            onComplete?.invoke()
            return
        }
        if (view == null) {
            onComplete?.invoke()
            return
        }
        view.evaluateJavascript(
            PageJsScripts.getThemeSetJs(
                GlobalData.isDarkMode.value,
                GlobalData.darkModeTheme.value,
                GlobalData.lightModeTheme.value,
                GlobalData.appTheme.value
            )
        ) { onComplete?.invoke() }
    }

    private fun injectCurrentLanguage(view: WebView?, currentUrl: String?) {
        val languageUrl = currentUrl ?: try {
            view?.url
        } catch (_: Throwable) {
            null
        }
        if (!isYamiboForumUrl(languageUrl)) return
        view?.evaluateJavascript(
            PageJsScripts.getLanguageSetJs(GlobalData.languageMode.value),
            null
        )
    }

    private fun revealThemeFlashSuppression(view: WebView?, delayMs: Long = 96L) {
        if (!isSuppressingThemeFlash) return
        themeFlashRevealRunnable?.let { themeFlashHandler.removeCallbacks(it) }
        val targetView = view
        val reveal = Runnable {
            targetView?.animate()?.cancel()
            targetView?.alpha = 1f
            isSuppressingThemeFlash = false
            suppressedThemeFlashUrl = null
            themeFlashRevealRunnable = null
        }
        themeFlashRevealRunnable = reveal
        if (delayMs <= 0L) {
            reveal.run()
        } else {
            themeFlashHandler.postDelayed(reveal, delayMs)
        }
    }

    protected fun applyHideCss(view: WebView?, currentUrl: String?) {
        val url = currentUrl ?: view?.url ?: ""

        // 隐藏论坛全局底栏；保留私信会话里的输入栏和占位，确保可以发送消息。
        var css = PageJsScripts.getForumChromeHideCss()

        // 隐藏顶部栏（仅在自己的主页 mycenter=1 时生效）
        if (url.contains("mycenter=1")) {
            css += " .my, .mz { visibility: hidden !important; pointer-events: none !important; }"
        }

        val injectJs = """
            javascript:(function() {
                var style = document.getElementById('yamibo-hide-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'yamibo-hide-style';
                    
                    var tryInject = function() {
                        if (document.head) {
                            document.head.appendChild(style);
                            return true;
                        } else if (document.documentElement) {
                            document.documentElement.appendChild(style);
                            return true;
                        }
                        return false;
                    };
                    
                    if (!tryInject()) {
                        var observer = new MutationObserver(function(mutations, obs) {
                            if (tryInject()) {
                                obs.disconnect();
                            }
                        });
                        observer.observe(document, { childList: true, subtree: true });
                    }
                }
                style.innerHTML = '$css';
            })();
        """.trimIndent()

        view?.evaluateJavascript(injectJs, null)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (observedSessionGeneration != GlobalData.sessionGeneration) {
            currentCookie = ""
            observedSessionGeneration = GlobalData.sessionGeneration
        }
        YamiboPostLinkUtil.explicitDesktopTemplateSelection(url)?.let { selected ->
            userSelectedDesktopTemplate = selected
            currentPageUsesDesktopTemplate = selected
        }
        suppressThemeFlashIfNeeded(view, url)
        val targetCookie = currentCookie.ifBlank {
            GlobalData.currentCookie
        }
        if (url != null && targetCookie.isNotBlank()) {
            CookieManager.getInstance().setCookie(url, targetCookie)
            CookieManager.getInstance().flush()
        }
        if (isYamiboForumUrl(url)) {
            LanguageModeUtil.applyForumCookies(GlobalData.languageMode.value, url)
        }

        if (url?.startsWith(RequestConfig.BASE_URL) == true) {
            applyHideCss(view, url)
        }

        super.onPageStarted(view, url, favicon)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        updateRenderedForumTemplate(view, url)
        applyHideCss(view, url)
        injectCurrentTheme(view, url) {
            injectCurrentLanguage(view, url)
            revealThemeFlashSuppression(view)
        }
        super.onPageCommitVisible(view, url)
    }

    /**
     * 五个论坛 WebView（论坛/我的/其他帖子/小说原帖/漫画兜底）共用的链接跳转预处理，按顺序：
     * 1) 非 http(s) 协议与站外链接交给系统（浏览器/对应应用）打开；
     * 2) 电脑版专属页（标签页）改写 mobile=no，避免手机版会话下落到「提示信息→首页」；
     * 3) 常规论坛跳转统一保持用户当前选择的模板：手机版补 mobile=2，电脑版补 mobile=no；
     *    标签页和电脑版个人空间继续沿用现有特殊流程。
     * 返回非 null 表示已消费该跳转，调用方直接 return；返回 null 表示继续调用方自己的逻辑。
     */
    protected fun handleCommonUrlOverride(view: WebView?, url: String?): Boolean? {
        val safeUrl = url ?: ""
        if (safeUrl.isBlank()) return false
        if (!safeUrl.startsWith("http://") && !safeUrl.startsWith("https://")) {
            return openExternalUrl(view, safeUrl)
        }
        if (!isYamiboForumUrl(safeUrl)) {
            openExternalUrl(view, safeUrl)
            return true
        }
        YamiboPostLinkUtil.explicitDesktopTemplateSelection(safeUrl, view?.url)?.let { selected ->
            userSelectedDesktopTemplate = selected
            currentPageUsesDesktopTemplate = selected
        }
        YamiboPostLinkUtil.normalizePcOnlyPageUrl(safeUrl)?.let { rewritten ->
            view?.loadUrl(rewritten)
            return true
        }
        val desktopSession = userSelectedDesktopTemplate ||
                currentPageUsesDesktopTemplate ||
                YamiboPostLinkUtil.isDesktopSessionCookie(
                    runCatching {
                        CookieManager.getInstance().getCookie("https://bbs.yamibo.com")
                    }.getOrNull()
                )
        YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
            safeUrl,
            desktopSession,
            view?.url
        )?.let { rewritten ->
            view?.loadUrl(rewritten)
            return true
        }
        return null
    }

    /**
     * 论坛主文档被 WAF 以 405 拒绝时，静默刷新个人主页 Cookie 并重试原页面一次。
     * 子类必须在把 405 交给自身错误页状态机之前调用。
     */
    protected fun tryRecoverWaf405(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ): Boolean {
        val failedUrl = request?.url?.toString().orEmpty()
        if (view == null || !Waf405RecoveryPolicy.shouldRecover(
                statusCode = errorResponse?.statusCode ?: 0,
                method = request?.method.orEmpty(),
                isMainFrame = request?.isForMainFrame == true,
                isYamiboUrl = isYamiboForumUrl(failedUrl)
            )
        ) {
            return false
        }
        revealThemeFlashSuppression(view, delayMs = 0L)
        return Waf405RecoveryManager.recoverWebView(view, failedUrl)
    }

    /**
     * 电脑版 SEO 页面（如 forum-33-1.html）本身可能不带 mobile=no，Cookie 也可能仍是
     * mobile=2。以页面实际 DOM 中电脑版特有的 #toptb 为准，确保后续无参数帖子链接
     * 继续使用电脑版模板。
     */
    private fun updateRenderedForumTemplate(view: WebView?, url: String?) {
        val isPcOnlyTagPage = url?.contains("misc.php", ignoreCase = true) == true &&
                url.contains("mod=tag", ignoreCase = true)
        view?.evaluateJavascript(
            "(function(){return !!document.getElementById('toptb');})()"
        ) { result ->
            currentPageUsesDesktopTemplate = !isPcOnlyTagPage &&
                    result.equals("true", ignoreCase = true)
        }
    }

    private fun openExternalUrl(view: WebView?, url: String): Boolean {
        val context = view?.context ?: return false
        return try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 电脑版专属页（标签页）用 mobile=no 打开后，服务器会写入 *_mobile=no cookie（约 1 小时），
     * 之后所有不带 mobile 参数的站内跳转都会渲染成电脑版，表现为帖子页空白/被弹回。
     * 该页加载完成后立即把 mobile cookie 改回 2，避免污染整个会话（含 CookieUtil 持久化）。
     */
    private fun restoreMobileCookieAfterPcOnlyPage(url: String?) {
        if (url.isNullOrBlank()) return
        if (!url.contains("mod=tag", ignoreCase = true) ||
            !url.contains("mobile=no", ignoreCase = true)
        ) {
            return
        }
        try {
            val cookieManager = CookieManager.getInstance()
            val base = "https://bbs.yamibo.com"
            val cookies = cookieManager.getCookie(base) ?: return
            var changed = false
            cookies.split(';').forEach { pair ->
                val trimmed = pair.trim()
                val name = trimmed.substringBefore('=')
                val value = trimmed.substringAfter('=', "")
                if (value.equals("no", ignoreCase = true) &&
                    (name.equals("mobile", ignoreCase = true) ||
                            name.endsWith("_mobile", ignoreCase = true))
                ) {
                    cookieManager.setCookie(base, "$name=2; path=/")
                    changed = true
                }
            }
            if (changed) cookieManager.flush()
        } catch (_: Throwable) {
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        updateRenderedForumTemplate(view, url)
        restoreMobileCookieAfterPcOnlyPage(url)
        url?.let {
            if (it.contains(RequestConfig.BASE_URL)) {
                applyHideCss(view, url)

                val cookieManager = CookieManager.getInstance()
                val cookie = cookieManager.getCookie(url)
                if (cookie != null) {
                    CookieUtil.saveCookie(cookie)
                    currentCookie = CookieUtil.persistentCookieHeader(cookie)
                }
            }
        }
        super.onPageFinished(view, url)
        injectCurrentTheme(view, url) {
            injectCurrentLanguage(view, url)
            revealThemeFlashSuppression(view)
        }

        view?.evaluateJavascript(
            """
            (function() {
                if (window.__historyHooked) return;
                window.__historyHooked = true;

                function checkState() {
                    var state = window.history.state;
                    var isFullscreen = state && typeof state === 'object' && 'pswp_index' in state;

                    if (window.AndroidFullscreen) {
                        window.AndroidFullscreen.notify(!!isFullscreen);
                    }
                }

                var originalPushState = history.pushState;
                history.pushState = function() {
                    var result = originalPushState.apply(this, arguments);
                    checkState();
                    return result;
                };

                var originalReplaceState = history.replaceState;
                history.replaceState = function() {
                    var result = originalReplaceState.apply(this, arguments);
                    checkState();
                    return result;
                };

                window.addEventListener('popstate', function() {
                    checkState();
                });

                checkState();
            })();
            """.trimIndent(), null
        )
        view?.evaluateJavascript(ATTACH_INTERCEPT_JS, null)
        view?.evaluateJavascript(MobileBlogJsScripts.enhancementsJs(GlobalData.currentUid), null)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        applyHideCss(view, url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) revealThemeFlashSuppression(view, delayMs = 0L)
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (isSameDocumentBase(failingUrl, suppressedThemeFlashUrl) ||
            isSameDocumentBase(failingUrl, try { view?.url } catch (_: Throwable) { null })
        ) {
            revealThemeFlashSuppression(view, delayMs = 0L)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) revealThemeFlashSuppression(view, delayMs = 0L)
    }
}

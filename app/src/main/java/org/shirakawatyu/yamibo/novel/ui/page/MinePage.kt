package org.shirakawatyu.yamibo.novel.ui.page

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.module.CoilWebViewProxy
import org.shirakawatyu.yamibo.novel.module.YamiboWebViewClient
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.effectiveScheme
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboSwitchColors
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarWebViewScroll
import org.shirakawatyu.yamibo.novel.ui.vm.MangaDirectoryVM
import org.shirakawatyu.yamibo.novel.ui.vm.MinePageVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.ui.widget.ReaderModeFAB
import org.shirakawatyu.yamibo.novel.ui.widget.ForumBlocklistDialog
import org.shirakawatyu.yamibo.novel.BuildConfig
import org.shirakawatyu.yamibo.novel.ui.component.AppUpdateDialog
import org.shirakawatyu.yamibo.novel.ui.component.AppUpdateFailureDialog
import org.shirakawatyu.yamibo.novel.util.AppUpdateCheckResult
import org.shirakawatyu.yamibo.novel.util.AppStrings
import org.shirakawatyu.yamibo.novel.util.BackupUtil
import org.shirakawatyu.yamibo.novel.util.AppUpdateInfo
import org.shirakawatyu.yamibo.novel.util.AppUpdateManager
import org.shirakawatyu.yamibo.novel.util.AccountSyncManager
import org.shirakawatyu.yamibo.novel.util.ActivityWebViewLifecycleObserver
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.CacheMaintenance
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.PageJsScripts
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.StaticAssetProxy
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlocklistManager
import org.shirakawatyu.yamibo.novel.util.history.HistoryUtil
import org.shirakawatyu.yamibo.novel.util.manga.MangaImagePipeline
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil
import org.shirakawatyu.yamibo.novel.util.reader.ReaderModeDetector
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

private var mineWebViewPauseRunnable: Runnable? = null
private val mineWebViewHandler = Handler(Looper.getMainLooper())

class FullscreenApiMine {
    var onStateChange: ((Boolean) -> Unit)? = null
    var onMangaActionDone: (() -> Unit)? = null
    var onCopyLink: ((String, String) -> Unit)? = null

    @JavascriptInterface
    fun notify(isFullscreen: Boolean) {
        Handler(Looper.getMainLooper()).post { onStateChange?.invoke(isFullscreen) }
    }

    @JavascriptInterface
    fun notifyMangaActionDone() {
        Handler(Looper.getMainLooper()).post { onMangaActionDone?.invoke() }
    }


    @JavascriptInterface
    fun copyLink(title: String, url: String) {
        Handler(Looper.getMainLooper()).post { onCopyLink?.invoke(title, url) }
    }
}

class NativeMangaMineJSInterface {
    var onTriggerManga: ((String, Int, String) -> Unit)? = null
    var onGoBack: (() -> Unit)? = null
    private var lastNavTime = 0L

    @JavascriptInterface
    fun openNativeManga(urlsJoined: String, clickedIndex: Int, title: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavTime < 1000) return
        lastNavTime = currentTime

        Handler(Looper.getMainLooper()).post {
            onTriggerManga?.invoke(urlsJoined, clickedIndex, title)
        }
    }

    @JavascriptInterface
    fun goBack() {
        Handler(Looper.getMainLooper()).post {
            onGoBack?.invoke()
        }
    }
}

class AboutMineJSInterface {
    var onShowAbout: (() -> Unit)? = null

    @JavascriptInterface
    fun showAbout() {
        Handler(Looper.getMainLooper()).post { onShowAbout?.invoke() }
    }
}

class HistoryJSInterface {
    var onShowHistory: (() -> Unit)? = null

    @JavascriptInterface
    fun showHistory() {
        Handler(Looper.getMainLooper()).post { onShowHistory?.invoke() }
    }
}

private var cachedFullscreenApiMine: FullscreenApiMine? = null
private var cachedNativeMangaApiMine: NativeMangaMineJSInterface? = null
private var cachedAboutApiMine: AboutMineJSInterface? = null
private var cachedHistoryApiMine: HistoryJSInterface? = null

private fun decodeHistoryTargetUrl(initUrl: String): String {
    val decodedUrl = runCatching { URLDecoder.decode(initUrl, "utf-8") }.getOrElse { initUrl }
    val absoluteUrl = if (decodedUrl.startsWith("http://") || decodedUrl.startsWith("https://")) {
        decodedUrl
    } else {
        "https://bbs.yamibo.com/${decodedUrl.removePrefix("/")}"
    }
    return normalizeHistoryComparableUrl(absoluteUrl)
}

private fun normalizeHistoryComparableUrl(url: String?): String {
    return url
        ?.trim()
        ?.substringBefore("#")
        ?.removeSuffix("/")
        .orEmpty()
}

private fun isSameHistoryTargetUrl(actualUrl: String?, targetUrl: String?): Boolean {
    val actual = normalizeHistoryComparableUrl(actualUrl)
    val target = normalizeHistoryComparableUrl(targetUrl)
    if (actual.isBlank() || target.isBlank()) return false
    if (actual == target) return true

    val actualTid = org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner.extractTidFromUrl(actual)
    val targetTid = org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner.extractTidFromUrl(target)
    return actualTid != null && actualTid == targetTid
}

private sealed interface MineDialogState {
    object None : MineDialogState
    object Settings : MineDialogState
    object Blocklist : MineDialogState
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun MinePage(
    isSelected: Boolean,
    navController: NavController,
    webChromeClient: WebChromeClient,
    initUrl: String = "",
    fromHistory: Boolean = false
) {
    val mineUrl = "https://bbs.yamibo.com/home.php?mod=space&do=profile&mycenter=1&mobile=2"
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val activityMinePageVM: MinePageVM = viewModel(viewModelStoreOwner = context as ComponentActivity)
    val routeMinePageVM: MinePageVM = viewModel()
    val minePageVM = if (fromHistory) routeMinePageVM else activityMinePageVM
    val historyTargetUrl = remember(fromHistory, initUrl) {
        if (fromHistory && initUrl.isNotBlank()) decodeHistoryTargetUrl(initUrl) else null
    }

    var activeHistoryTargetUrl by remember { mutableStateOf<String?>(historyTargetUrl) }
    var activeLoadTargetUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember {
        mutableStateOf(run {
            val cachedView = minePageVM.cachedWebView
            val cachedUrl = cachedView?.url
            if (fromHistory && historyTargetUrl != null) {
                // 历史帖子页使用 route-scoped MinePageVM 的独立 WebView，
                // 不再读取真正 MinePage 的 cachedWebView 状态。
                true
            } else if (!fromHistory) {
                (cachedUrl == null || cachedView.tag?.toString()?.startsWith("recycled") == true || cachedUrl == "about:blank")
            } else {
                true
            }
        })
    }
    var showLoadError by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var timeoutJob by remember { mutableStateOf<Job?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var swipeRefresh by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    var currentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    var autoOpenMangaMode by remember { mutableStateOf(false) }
    var savedMangaUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var needFallbackToHome by rememberSaveable { mutableStateOf(false) }
    var mineDialog by remember { mutableStateOf<MineDialogState>(MineDialogState.None) }
    var manualUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var manualUpdateFailure by remember { mutableStateOf<String?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var rendererRecoveryUrl by remember { mutableStateOf<String?>(null) }

    val canConvertToReader = remember(currentUrl, pageTitle) {
        ReaderModeDetector.canConvertToReaderMode(currentUrl, pageTitle)
    }
    val mangaDirVM: MangaDirectoryVM = viewModel(
        factory = ViewModelFactory(LocalContext.current.applicationContext)
    )
    lateinit var startLoading: (webView: WebView, url: String) -> Unit

    fun isMineRootUrl(url: String?): Boolean {
        val normalized = normalizeHistoryComparableUrl(url)
        return normalized == normalizeHistoryComparableUrl(mineUrl) || normalized.contains("mycenter=1") || normalized.contains("mod=logging")
    }

    fun isSamePageTargetUrl(actualUrl: String?, targetUrl: String?): Boolean {
        val target = normalizeHistoryComparableUrl(targetUrl)
        if (target.isBlank()) return true
        if (target == normalizeHistoryComparableUrl(mineUrl) || target.contains("mycenter=1")) {
            return isMineRootUrl(actualUrl)
        }
        return isSameHistoryTargetUrl(actualUrl, target)
    }

    fun currentExpectedLoadTarget(): String? {
        return activeLoadTargetUrl ?: activeHistoryTargetUrl
    }

    fun isExpectedLoadCallback(url: String?): Boolean {
        val target = currentExpectedLoadTarget()
        return target.isNullOrBlank() || isSamePageTargetUrl(url, target)
    }

    fun finishLoadingIfExpected(view: WebView?, url: String?) {
        if (hasError || view == null || !isLoading || !isExpectedLoadCallback(url)) return
        timeoutJob?.cancel()
        retryCount = 0
        isLoading = false
        isPullRefreshing = false
        showLoadError = false
        activeLoadTargetUrl = null
        if (fromHistory) activeHistoryTargetUrl = null
    }

    fun markMainFrameErrorIfExpected(url: String?) {
        if (!BBSGlobalWebViewClient.isYamiboUrl(url ?: "")) return
        if (!isExpectedLoadCallback(url)) return
        timeoutJob?.cancel()
        retryCount = 0
        hasError = true
        isLoading = false
        isPullRefreshing = false
        activeLoadTargetUrl = null
        activeHistoryTargetUrl = null
        showLoadError = true
    }

    fun evaluateCanGoBack(view: WebView?): Boolean {
        if (view == null || !view.canGoBack()) return false
        val currUrl = view.url ?: ""
        // 只将真正的"我的主页" (含mycenter=1) 或 登录页 当做根页面，其它用户的个人资料页可以GoBack
        if (currUrl == mineUrl || currUrl.contains("mycenter=1") || currUrl.contains("mod=logging")) return false

        val list = view.copyBackForwardList()
        if (list.currentIndex <= 0) return false

        val backItem = list.getItemAtIndex(list.currentIndex - 1)
        val backUrl = backItem?.url ?: return false

        return backUrl.isNotBlank() &&
                backUrl != "about:blank" &&
                !backUrl.contains("warmup=true") &&
                !backUrl.startsWith("data:")
    }

    fun runTimeout(webView: WebView, onTimeout: () -> Unit) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(10000)
            if (isLoading) {
                onTimeout()
            }
        }
    }

    startLoading = { webView: WebView, url: String ->
        val targetForThisLoad = normalizeHistoryComparableUrl(url)
        activeLoadTargetUrl = targetForThisLoad
        if (fromHistory) activeHistoryTargetUrl = targetForThisLoad
        isLoading = true
        hasError = false
        showLoadError = false
        retryCount = 0
        webView.stopLoading()
        CookieManager.getInstance().setCookie(url, GlobalData.currentCookie)
        CookieManager.getInstance().flush()
        runTimeout(webView) {
            if (activeLoadTargetUrl != targetForThisLoad) return@runTimeout
            Log.w("MinePage", "WebView loading timed out. Retrying...")
            webView.stopLoading()
            retryCount++

            runTimeout(webView) {
                if (activeLoadTargetUrl != targetForThisLoad) return@runTimeout
                Log.e("MinePage", "Retry timed out. Giving up.")
                hasError = true
                isLoading = false
                isPullRefreshing = false
                activeLoadTargetUrl = null
                activeHistoryTargetUrl = null
                showLoadError = true
                webView.stopLoading()
            }
            webView.loadUrl(url)
        }
        webView.loadUrl(url)
    }

    val view = LocalView.current
    val isFullscreenState = remember { mutableStateOf(false) }
    val bottomNavBarVM: BottomNavBarVM =
        viewModel(viewModelStoreOwner = context)
    DisposableEffect(Unit) {
        onDispose {
            val currentRoute = navController.currentDestination?.route ?: ""
            if (!currentRoute.startsWith("NativeMangaPage") && !currentRoute.startsWith("ReaderPage")) {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
                bottomNavBarVM.updateBottomBarSuppressed(false)
            }
        }
    }

    val fullscreenApi = remember(fromHistory) {
        if (fromHistory) {
            FullscreenApiMine()
        } else {
            if (cachedFullscreenApiMine == null) {
                cachedFullscreenApiMine = FullscreenApiMine()
            }
            cachedFullscreenApiMine!!
        }
    }
    fullscreenApi.onStateChange = { isFullscreen -> isFullscreenState.value = isFullscreen }
    fullscreenApi.onMangaActionDone = { autoOpenMangaMode = false }
    fullscreenApi.onCopyLink = { title, url ->
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("yamibo_link", "$title\n$url")
        clipboard.setPrimaryClip(clip)
        GlobalData.lastClipboardUrl = url
        YamiboToast.show(message = "已复制链接")
    }

    val nativeMangaApi = remember(fromHistory) {
        if (fromHistory) {
            NativeMangaMineJSInterface()
        } else {
            if (cachedNativeMangaApiMine == null) {
                cachedNativeMangaApiMine = NativeMangaMineJSInterface()
            }
            cachedNativeMangaApiMine!!
        }
    }
    val aboutApi = remember(fromHistory) {
        if (fromHistory) {
            AboutMineJSInterface()
        } else {
            if (cachedAboutApiMine == null) {
                cachedAboutApiMine = AboutMineJSInterface()
            }
            cachedAboutApiMine!!
        }
    }
    aboutApi.onShowAbout = { mineDialog = MineDialogState.Settings }
    val historyApi = remember(fromHistory) {
        if (fromHistory) {
            HistoryJSInterface()
        } else {
            if (cachedHistoryApiMine == null) {
                cachedHistoryApiMine = HistoryJSInterface()
            }
            cachedHistoryApiMine!!
        }
    }
    historyApi.onShowHistory = { navController.navigate("HistoryPage") }
    val searchNavApi = remember {
        object {
            @JavascriptInterface
            fun navigateToPost(url: String) {
                Handler(Looper.getMainLooper()).post {
                    GlobalData.pendingDeepLinkUrl.value = url
                }
            }
        }
    }
    val forumBlocklistApi = remember { ForumBlocklistJSInterface() }
    val pullRefreshBridge = remember { WebViewPullRefreshBridge() }

    val mineWebView = remember(fromHistory, historyTargetUrl, webViewGeneration) {
        val isNew = minePageVM.cachedWebView == null
        val webView = minePageVM.getOrAcquireWebView(context)

        if (GlobalData.isDarkMode.value) {
            webView.setBackgroundColor(
                GlobalData.appTheme.value.effectiveScheme(GlobalData.pureBlackMode.value).background.toArgb()
            )
        }

        if (isNew) {
            webView.settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                // 与 BBS WebView 一致开启缩放：从「我的主题」进入帖子后切电脑版需要双指缩放
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                textZoom = 100
                domStorageEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                blockNetworkImage = false
            }
        }

        // 历史帖子详情页使用独立的 route-scoped WebView，但 composable 可能在进入 NativeMangaPage / ReaderPage 后重建。
        // 因此 fromHistory 时每次都重新绑定 JS interface，避免 WebView 内还拿着上一次组合的回调对象。
        if (isNew || fromHistory) {
            if (fromHistory && !isNew) {
                try {
                    webView.removeJavascriptInterface("AndroidFullscreen")
                    webView.removeJavascriptInterface("NativeMangaApi")
                    webView.removeJavascriptInterface("AboutApi")
                    webView.removeJavascriptInterface("HistoryApi")
                    webView.removeJavascriptInterface("AndroidSearchNav")
                    webView.removeJavascriptInterface("AndroidForumBlocklist")
                    webView.removeJavascriptInterface("AndroidPullRefreshGuard")
                } catch (_: Exception) {
                }
            }
            webView.addJavascriptInterface(fullscreenApi, "AndroidFullscreen")
            webView.addJavascriptInterface(nativeMangaApi, "NativeMangaApi")
            webView.addJavascriptInterface(aboutApi, "AboutApi")
            webView.addJavascriptInterface(historyApi, "HistoryApi")
            webView.addJavascriptInterface(searchNavApi, "AndroidSearchNav")
        }
        webView.addJavascriptInterface(forumBlocklistApi, "AndroidForumBlocklist")
        webView.addJavascriptInterface(pullRefreshBridge, "AndroidPullRefreshGuard")
        webView.webChromeClient = webChromeClient

        webView
    }
    ObserveBottomBarWebViewScroll(mineWebView, bottomNavBarVM)

    // 下拉刷新：重新加载当前页面（无可用 URL 时回到个人主页）
    fun triggerMinePullRefresh() {
        isPullRefreshing = true
        isLoading = true
        showLoadError = false
        val curl = mineWebView.url
        if (!curl.isNullOrEmpty() && curl != "about:blank" && !curl.contains("warmup=true")) {
            mineWebView.reload()
        } else {
            startLoading(mineWebView, mineUrl)
        }
    }

    val appTheme by GlobalData.appTheme.collectAsState()
    val isDarkMode = appTheme.isDark
    val languageMode by GlobalData.languageMode.collectAsState()
    val strings = remember(languageMode) { AppStrings.forMode(languageMode) }
    val isForumBlocklistEnabled by ForumBlocklistManager.enabled.collectAsState()
    val forumBlockedItems by ForumBlocklistManager.items.collectAsState()

    fun injectForumBlocker(view: WebView?) {
        view?.evaluateJavascript(
            PageJsScripts.getForumBlockerJs(
                enabled = ForumBlocklistManager.enabled.value,
                itemsJson = ForumBlocklistManager.itemsJson(),
                isDark = GlobalData.isDarkMode.value,
                selfUid = GlobalData.currentUid
            ),
            null
        )
    }

    LaunchedEffect(appTheme, isForumBlocklistEnabled, forumBlockedItems) {
        mineWebView.setBackgroundColor(
            appTheme.effectiveScheme(GlobalData.pureBlackMode.value).background.toArgb()
        )
        mineWebView.evaluateJavascript(
            PageJsScripts.getThemeSetJs(
                isDarkMode,
                GlobalData.darkModeTheme.value,
                GlobalData.lightModeTheme.value,
                GlobalData.appTheme.value,
                pureBlack = GlobalData.pureBlackMode.value
            ),
            null
        )
        mineWebView.evaluateJavascript(
            PageJsScripts.getForumBlockerJs(
                enabled = isForumBlocklistEnabled,
                itemsJson = ForumBlocklistManager.itemsJson(forumBlockedItems),
                isDark = isDarkMode,
                selfUid = GlobalData.currentUid
            ),
            null
        )
        mineWebView.evaluateJavascript(PageJsScripts.PULL_REFRESH_EDIT_FOCUS_JS, null)
    }

    // 下拉小圆球跟随暗黑模式配色
    LaunchedEffect(isDarkMode, swipeRefresh) {
        swipeRefresh?.apply {
            if (isDarkMode) {
                setProgressBackgroundColorSchemeColor(0xFF223247.toInt())
                setColorSchemeColors(0xFF4EA1FF.toInt())
            } else {
                setProgressBackgroundColorSchemeColor(0xFFFFFBE7.toInt())
                setColorSchemeColors(0xFF551200.toInt())
            }
        }
    }

    val rootHistoryIndex = remember { mineWebView.copyBackForwardList().currentIndex }

    fun resumeMineWebViewAfterChildPage() {
        mineWebViewPauseRunnable?.let {
            mineWebViewHandler.removeCallbacks(it)
            mineWebViewPauseRunnable = null
        }
        try {
            mineWebView.onResume()
            mineWebView.resumeTimers()
            // 从 NativeMangaPage / ReaderPage 返回时，不重新加载帖子，只恢复 WebView 的定时器与点击脚本。
            mineWebView.evaluateJavascript(PageJsScripts.REMOVE_TRANSITION_STYLE_JS, null)
            mineWebView.evaluateJavascript(PageJsScripts.RELOAD_BROKEN_IMAGES_JS, null)
            mineWebView.evaluateJavascript(PageJsScripts.MINE_MANGA_REINJECT_JS, null)
            // WebView 暂停期间 evaluateJavascript 可能被丢弃；恢复时补一次主题注入。
            mineWebView.evaluateJavascript(
                PageJsScripts.getThemeSetJs(
                    GlobalData.isDarkMode.value,
                    GlobalData.darkModeTheme.value,
                    GlobalData.lightModeTheme.value,
                    GlobalData.appTheme.value,
                    pureBlack = GlobalData.pureBlackMode.value
                ),
                null
            )
            injectForumBlocker(mineWebView)
            mineWebView.evaluateJavascript(PageJsScripts.PULL_REFRESH_EDIT_FOCUS_JS, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(mineWebView, isSelected, initUrl) {
        val recoveryUrl = rendererRecoveryUrl
        if (recoveryUrl != null) {
            rendererRecoveryUrl = null
            startLoading(mineWebView, recoveryUrl)
        } else if (fromHistory && historyTargetUrl != null) {
            activeHistoryTargetUrl = historyTargetUrl

            val currentWebViewUrl = mineWebView.url ?: ""
            if (isSameHistoryTargetUrl(currentWebViewUrl, historyTargetUrl)) {
                activeLoadTargetUrl = null
                activeHistoryTargetUrl = null
                isLoading = false
                canGoBack = evaluateCanGoBack(mineWebView)
            } else {
                startLoading(mineWebView, historyTargetUrl)
            }
        } else if (!fromHistory) {
            val currentWebViewUrl = mineWebView.url
            if (isSelected && (currentWebViewUrl == null || mineWebView.tag?.toString()
                    ?.startsWith("recycled") == true || currentWebViewUrl == "about:blank")
            ) {
                mineWebView.tag = null
                if (savedMangaUrl != null) {
                    val restoreUrl = savedMangaUrl
                    if (isSamePageTargetUrl(currentWebViewUrl, restoreUrl)) {
                        // 从 NativeMangaPage 返回原帖时，WebView 仍在同一帖子；不要重新 load，避免回退体验倒退成加载圈。
                        savedMangaUrl = null
                        needFallbackToHome = false
                        activeLoadTargetUrl = null
                        isLoading = false
                        canGoBack = evaluateCanGoBack(mineWebView)
                        resumeMineWebViewAfterChildPage()
                    } else {
                        startLoading(mineWebView, restoreUrl!!)
                        savedMangaUrl = null
                        needFallbackToHome = true
                    }
                } else {
                    startLoading(mineWebView, mineUrl)
                }
            } else {
                isLoading = false
                canGoBack = evaluateCanGoBack(mineWebView)
            }
        }
    }

    DisposableEffect(mineWebView) {
        onDispose {
            timeoutJob?.cancel()
            activeLoadTargetUrl = null
            activeHistoryTargetUrl = null

            if (fromHistory) {
                val nextRoute = navController.currentDestination?.route.orEmpty()
                val keepHistoryWebViewForChildPage =
                    nextRoute.startsWith("NativeMangaPage") || nextRoute.startsWith("ReaderPage")

                if (!keepHistoryWebViewForChildPage) {
                    // 真正离开历史帖子详情页时，清掉这个 route-scoped WebView。
                    // 它不会写回真正的 MinePageVM，因此返回 MinePage 时不会再显示历史帖子。
                    try {
                        mineWebView.stopLoading()
                        mineWebView.clearHistory()
                    } catch (e: Exception) {
                        Log.w("MinePage", "Cleanup history WebView failed", e)
                    }
                    minePageVM.scheduleRelease(mineWebView, delayMs = 0L)
                } else {
                    // 去原生漫画 / 阅读器时保留几分钟，返回详情页可以恢复原 WebView。
                    minePageVM.scheduleRelease(mineWebView)
                }
            } else {
                minePageVM.scheduleRelease(mineWebView)
            }
        }
    }

    nativeMangaApi.onGoBack = {
        if (fromHistory) {
            val currentIndex = mineWebView.copyBackForwardList().currentIndex
            if (currentIndex > rootHistoryIndex + 1 && mineWebView.canGoBack()) {
                mineWebView.goBack()
            } else {
                navController.popBackStack()
            }
        } else {
            if (evaluateCanGoBack(mineWebView)) {
                mineWebView.goBack()
            } else {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }
    }

    // 加载结束时收起下拉刷新指示器
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            swipeRefresh?.isRefreshing = false
            isPullRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        bottomNavBarVM.goHomeEvent.collect { route ->
            if (route == "MinePage") {
                startLoading(mineWebView, mineUrl)
            }
        }
    }

    LaunchedEffect(bottomNavBarVM, mineWebView) {
        bottomNavBarVM.scrollToTopEvent.collect {
            mineWebView.scrollTo(0, 0)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (autoOpenMangaMode) {
                    autoOpenMangaMode = false
                }

                resumeMineWebViewAfterChildPage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    nativeMangaApi.onTriggerManga = { urlsJoined, clickedIndex, title ->
        mineWebView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { htmlResult ->
            scope.launch(Dispatchers.Default) {
                val cleanHtml = try {
                    JSON.parse(htmlResult) as? String ?: ""
                } catch (_: Exception) {
                    htmlResult?.trim('"')?.replace("\\u003C", "<")?.replace("\\\"", "\"") ?: ""
                }

                val urls = urlsJoined
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                val safeClickedIndex = clickedIndex.coerceIn(0, maxOf(0, urls.size - 1))

                MangaImagePipeline.handoffPrefetch(
                    context = context.applicationContext,
                    urls = urls,
                    clickedIndex = safeClickedIndex
                )

                withContext(Dispatchers.Main) {
                    GlobalData.tempMangaUrls = urls
                    GlobalData.tempMangaIndex = safeClickedIndex
                    GlobalData.tempHtml = cleanHtml
                    GlobalData.tempTitle = title

                    resumeMineWebViewAfterChildPage()

                    mineWebView.evaluateJavascript(PageJsScripts.REMOVE_TRANSITION_STYLE_JS, null)
                    autoOpenMangaMode = false
                    val passUrl = currentUrl ?: mineWebView.url ?: historyTargetUrl ?: "https://bbs.yamibo.com/forum.php"

                    savedMangaUrl = passUrl
                    val encodedUrl = URLEncoder.encode(passUrl, "utf-8")
                    val encodedOriginal = URLEncoder.encode(passUrl, "utf-8")
                    navController.navigate("NativeMangaPage?url=$encodedUrl&originalUrl=$encodedOriginal")
                }
            }
        }
    }

    ActivityWebViewLifecycleObserver(mineWebView)

    LaunchedEffect(isFullscreenState.value, autoOpenMangaMode) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)

        val shouldBeFullscreen = isFullscreenState.value || autoOpenMangaMode

        if (shouldBeFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bottomNavBarVM.updateBottomBarSuppressed(true)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            bottomNavBarVM.updateBottomBarSuppressed(false)
        }
    }

    LaunchedEffect(isFullscreenState.value) {
        if (!isFullscreenState.value) {
            mineWebView.evaluateJavascript(PageJsScripts.CLEANUP_FULLSCREEN_JS, null)
            GlobalData.webProgress.value = 100
        } else {
            if (autoOpenMangaMode) {
                autoOpenMangaMode = false
            }
            currentUrl?.let { url ->
                if (url.contains("mod=viewthread") && url.contains("tid=")) {
                    mineWebView.evaluateJavascript(PageJsScripts.CHECK_SECTION_JS) { result ->
                        val sectionName = try {
                            JSON.parse(result) as? String ?: ""
                        } catch (_: Exception) {
                            result?.replace("\"", "") ?: ""
                        }

                        val allowedSections = listOf(
                            "中文百合漫画区",
                            "百合漫画图源区"
                        )
                        val isCrossForum = sectionName.isNotBlank() && allowedSections.none {
                            sectionName.contains(it)
                        }

                        if (!isCrossForum) {
                            val pageTitle = mineWebView.title ?: ""
                            mineWebView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })()") { htmlResult ->
                                val cleanHtml = try {
                                    JSON.parse(htmlResult) as? String ?: ""
                                } catch (_: Exception) {
                                    htmlResult.trim('"').replace("\\u003C", "<")
                                        .replace("\\\"", "\"")
                                }
                                if (cleanHtml.isNotBlank()) {
                                    mangaDirVM.initDirectoryFromWeb(url, cleanHtml, pageTitle)
                                }
                            }
                        } else {
                            Log.i("MinePage", "非图区帖子(${sectionName})，跳过本地目录生成与缓存")
                        }
                    }
                }
            }
        }
    }

    // WebViewClient 必须在上面的初始 loadUrl 协程真正执行前同步安装。历史页经常命中缓存，
    // 若仍用 LaunchedEffect 安装，onPageFinished 可能先被池中的空客户端接走，加载遮罩便不会撤下。
    DisposableEffect(mineWebView, isSelected) {
        mineWebView.webViewClient = object : YamiboWebViewClient() {
            val contentImageCount = AtomicInteger(0)
            private fun isHomepageUrl(url: String): Boolean {
                return when (url) {
                    "https://bbs.yamibo.com/",
                    "https://bbs.yamibo.com",
                    "https://bbs.yamibo.com/?mobile=2",
                    "https://bbs.yamibo.com/?mobile=no",
                    "https://bbs.yamibo.com/index.php",
                    "https://bbs.yamibo.com/index.php?mobile=2",
                    "https://bbs.yamibo.com/index.php?mobile=no",
                    "https://bbs.yamibo.com/forum.php",
                    "https://bbs.yamibo.com/forum.php?mobile=2",
                    "https://bbs.yamibo.com/forum.php?mobile=no" -> true

                    else -> false
                }
            }
            private fun getToggleHeaderJs(isMineRoot: Boolean): String {
                return """
                javascript:(function() {
                    var style = document.getElementById('mine-dynamic-header');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'mine-dynamic-header';
                        document.head.appendChild(style);
                    }
                    if ($isMineRoot) {
                        style.innerHTML = '.header .z, .header .y { display: none !important; }';
                        var aboutBtn = document.getElementById('mine-about-btn');
                        if (!aboutBtn) {
                            aboutBtn = document.createElement('a');
                            aboutBtn.id = 'mine-about-btn';
                            aboutBtn.innerText = '设置';
                            aboutBtn.href = 'javascript:void(0)';
                            aboutBtn.style.cssText = 'position:fixed; right:12px; top:8px; height:32px; line-height:32px; padding:0 14px; color:#ffffff; background:rgba(0,0,0,0.35); border-radius:16px; text-decoration:none; z-index:9999; font-size:15px; font-weight:500;';
                            aboutBtn.addEventListener('click', function(e) {
                                e.preventDefault();
                                window.AboutApi.showAbout();
                            });
                            document.body.appendChild(aboutBtn);
                        } else {
                            aboutBtn.style.display = '';
                        }
                        var historyBtn = document.getElementById('mine-history-btn');
                        if (!historyBtn) {
                            historyBtn = document.createElement('a');
                            historyBtn.id = 'mine-history-btn';
                            historyBtn.innerText = '历史';
                            historyBtn.href = 'javascript:void(0)';
                            historyBtn.style.cssText = 'position:fixed; left:12px; top:8px; height:32px; line-height:32px; padding:0 14px; color:#ffffff; background:rgba(0,0,0,0.35); border-radius:16px; text-decoration:none; z-index:9999; font-size:15px; font-weight:500;';
                            historyBtn.addEventListener('click', function(e) {
                                e.preventDefault();
                                if(window.HistoryApi) window.HistoryApi.showHistory();
                            });
                            document.body.appendChild(historyBtn);
                        } else {
                            historyBtn.style.display = '';
                        }
                    } else {
                        style.innerHTML = '.header, .header .z, .header .y { display: block !important; visibility: visible !important; opacity: 1 !important; }';
                        var btnA = document.getElementById('mine-about-btn');
                        if (btnA) btnA.style.display = 'none';
                        var btnH = document.getElementById('mine-history-btn');
                        if (btnH) btnH.style.display = 'none';
                    }
                })();
            """.trimIndent()
            }
            override fun onFormResubmission(
                view: WebView?,
                dontResend: android.os.Message?,
                resend: android.os.Message?
            ) {
                resend?.sendToTarget()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val urlStr = request?.url?.toString() ?: ""
                handleCommonUrlOverride(view, urlStr)?.let { return it }

                if (!fromHistory && isSelected && isHomepageUrl(urlStr) && view != null) {
                    scope.launch(Dispatchers.IO) {
                        delay(500L)
                        AccountSyncManager.syncCookieAndCheckSign(
                            context,
                            "LOGIN_REDIRECT_INTERCEPT"
                        )
                    }
                    startLoading(view, mineUrl)
                    return true
                }

                return super.shouldOverrideUrlLoading(view, request)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                handleCommonUrlOverride(view, url)?.let { return it }
                return super.shouldOverrideUrlLoading(view, url ?: "")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url == "about:blank" || url?.contains("warmup=true") == true || url?.startsWith(
                        "data:"
                    ) == true
                ) return

                if (!isExpectedLoadCallback(url)) {
                    super.onPageStarted(view, url, favicon)
                    return
                }

                val checkUrl = url ?: ""

                // 区分自己的主页(含mycenter=1) 或 登录页 才算是 Root
                val isHomePage =
                    isHomepageUrl(checkUrl) || checkUrl == mineUrl || checkUrl.contains("mycenter=1") || checkUrl.contains("mod=logging")
                bottomNavBarVM.isMineAtRoot = isHomePage

                if (!fromHistory && isSelected && isHomepageUrl(checkUrl) && view != null) {
                    view.stopLoading()
                    scope.launch(Dispatchers.IO) {
                        delay(500L)
                        AccountSyncManager.syncCookieAndCheckSign(
                            context,
                            "LOGIN_REDIRECT_INTERCEPT"
                        )
                    }
                    startLoading(view, mineUrl)
                    return
                }

                GlobalData.webProgress.value = 0
                contentImageCount.set(0)
                if (!showLoadError) {
                    hasError = false
                }
                super.onPageStarted(view, url, favicon)
                currentUrl = url

                canGoBack = evaluateCanGoBack(view)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlStr = request?.url?.toString() ?: ""
                val accept = request?.requestHeaders?.get("Accept") ?: ""

                // 主题模式：拦截主框架 HTML，在渲染前注入主题 CSS，消除白闪
                if (request?.isForMainFrame == true &&
                    request.method == "GET" &&
                    PageJsScripts.shouldApplyWebTheme(GlobalData.appTheme.value) &&
                    urlStr.contains("bbs.yamibo.com")
                ) {
                    val html = YamiboRetrofit.proxyHtmlForDarkMode(request)
                    if (html != null) {
                        val dm = context.resources.displayMetrics
                        val widthPx = (view?.width ?: 0).takeIf { it > 0 } ?: dm.widthPixels
                        val desktopFitScale = PageJsScripts.calculateDesktopFitScale(widthPx, dm.density)
                        val modified = PageJsScripts.injectThemeCssIntoHtml(
                            html,
                            GlobalData.isDarkMode.value,
                            GlobalData.darkModeTheme.value,
                            GlobalData.lightModeTheme.value,
                            desktopFitScale,
                            appTheme = GlobalData.appTheme.value,
                            pureBlack = GlobalData.pureBlackMode.value
                        )
                        return WebResourceResponse(
                            "text/html",
                            "utf-8",
                            modified.byteInputStream(Charsets.UTF_8)
                        )
                    }
                }

                StaticAssetProxy.tryProxySafeStaticAsset(request)?.let { return it }

                val isImage = accept.contains("image/", ignoreCase = true) ||
                        urlStr.contains(
                            Regex(
                                "\\.(jpg|jpeg|png|webp|gif)",
                                RegexOption.IGNORE_CASE
                            )
                        ) ||
                        urlStr.contains("attachment")

                if (request?.isForMainFrame == false &&
                    request.method == "GET" &&
                    urlStr.contains("yamibo.com") &&
                    isImage &&
                    org.shirakawatyu.yamibo.novel.util.WebViewImagePolicy
                        .shouldProxyForumAttachment(urlStr)
                ) {
                    if (!urlStr.contains("smiley") && !urlStr.contains("avatar") &&
                        !urlStr.contains("common") && !urlStr.contains("static/image") &&
                        !urlStr.contains("template") && !urlStr.contains("block")
                    ) {
                        contentImageCount.getAndIncrement()

                        val headers = mutableMapOf<String, String>()
                        request.requestHeaders?.forEach { (k, v) -> headers[k] = v }

                        val coilResponse =
                            CoilWebViewProxy.interceptImage(context, urlStr, headers)
                        if (coilResponse != null) return coilResponse

                        val proxyResponse = YamiboRetrofit.proxyWebViewResource(request)
                        if (proxyResponse != null) return proxyResponse

                        return WebResourceResponse(
                            "image/jpeg",
                            "utf-8",
                            404,
                            "Blocked by Interceptor",
                            null,
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // 只有进入自己的主页或登录页时才清空历史，别人的主页不要清空
                if (url != null && (url == mineUrl || url.contains("mycenter=1") || url.contains("mod=logging"))) {
                    view?.clearHistory()
                }
                canGoBack = evaluateCanGoBack(view)

                val checkUrl = url ?: ""
                val isHomePage =
                    isHomepageUrl(checkUrl) || checkUrl == mineUrl || checkUrl.contains("mycenter=1") || checkUrl.contains("mod=logging")
                bottomNavBarVM.isMineAtRoot = isHomePage
                if (!isLoading) {
                    GlobalData.webProgress.value = 100
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                if (url == "about:blank" || url?.contains("warmup=true") == true) return
                super.onPageCommitVisible(view, url)

                if (!isExpectedLoadCallback(url)) return

                finishLoadingIfExpected(view, url)
                pageTitle = view?.title ?: ""

                // 使用外部提取的特定于 MinePage 的脚本常量
                view?.evaluateJavascript(PageJsScripts.MINE_COMMIT_BOOTSTRAP_JS, null)
                view?.evaluateJavascript(PageJsScripts.BBS_THREAD_NAVIGATION_JS, null)
                injectForumBlocker(view)

                if (PageJsScripts.shouldApplyWebTheme(GlobalData.appTheme.value)) {
                    view?.evaluateJavascript(
                        PageJsScripts.getThemeSetJs(
                            GlobalData.isDarkMode.value,
                            GlobalData.darkModeTheme.value,
                            GlobalData.lightModeTheme.value,
                            GlobalData.appTheme.value,
                            pureBlack = GlobalData.pureBlackMode.value
                        ), null
                    )
                }

                // Explicitly rule out mod=logging for the header injection
                val isMineRoot = url != null && (url == mineUrl || url.contains("mycenter=1")) && !url.contains("mod=logging")
                val toggleHeaderJs = getToggleHeaderJs(isMineRoot)
                view?.evaluateJavascript(toggleHeaderJs, null)

                // 记录浏览历史
                if (url != null && HistoryUtil.isThreadUrl(url)) {
                    view?.evaluateJavascript(PageJsScripts.EXTRACT_THREAD_INFO_JS) { jsonStr ->
                        try {
                            val cleanJson = if (jsonStr?.startsWith("\"") == true) JSON.parse(jsonStr) as String else jsonStr
                            val obj = JSON.parseObject(cleanJson)
                            val title = obj.getString("title") ?: ""
                            val author = obj.getString("author") ?: ""
                            val section = obj.getString("section") ?: ""
                            if (title.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    HistoryUtil.addOrUpdateHistory(url, title, author, section)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isExpectedLoadCallback(url)) {
                    super.onPageFinished(view, url)
                    return
                }

                finishLoadingIfExpected(view, url)

                super.onPageFinished(view, url)
                currentUrl = url

                // 只有进入自己的主页或登录页时才清空历史
                if (url != null && (url == mineUrl || url.contains("mycenter=1") || url.contains("mod=logging"))) {
                    view?.clearHistory()
                }

                canGoBack = evaluateCanGoBack(view)
                val isMineRoot = url != null && (url == mineUrl || url.contains("mycenter=1")) && !url.contains("mod=logging")
                val toggleHeaderJs = getToggleHeaderJs(isMineRoot)
                view?.evaluateJavascript(toggleHeaderJs, null)
                injectForumBlocker(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    markMainFrameErrorIfExpected(request.url?.toString())
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (failingUrl != null && failingUrl == view?.url) {
                    markMainFrameErrorIfExpected(failingUrl)
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                timeoutJob?.cancel()
                Log.e("MinePage", "WebView renderer gone; crashed=${detail?.didCrash()}")
                val deadView = view ?: mineWebView
                rendererRecoveryUrl = runCatching { deadView.url }
                    .getOrNull()
                    ?.takeUnless { it == "about:blank" || it.contains("warmup=true") }
                    ?: historyTargetUrl
                minePageVM.discard(deadView)
                hasError = false
                isLoading = true
                isPullRefreshing = false
                showLoadError = false
                webViewGeneration++

                return true
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (tryRecoverWaf405(view, request, errorResponse)) {
                    timeoutJob?.cancel()
                    hasError = false
                    isLoading = true
                    showLoadError = false
                    return
                }
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    markMainFrameErrorIfExpected(request.url?.toString())
                }
            }
        }
        onDispose { }
    }

    DisposableEffect(mineWebView, isSelected) {
        if (isSelected) {
            resumeMineWebViewAfterChildPage()
        } else {
            timeoutJob?.cancel()
            retryCount = 0
            isLoading = false
            isPullRefreshing = false
        }
        onDispose { }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && autoOpenMangaMode) {
            mineWebView.evaluateJavascript(PageJsScripts.AUTO_OPEN_MANGA_JS, null)

            delay(6000)
            if (autoOpenMangaMode) {
                autoOpenMangaMode = false
                mineWebView.evaluateJavascript(PageJsScripts.REMOVE_TRANSITION_STYLE_JS, null)
            }
        }
    }

    BackHandler(enabled = true) {
        val checkUrl = currentUrl ?: mineWebView.url ?: ""
        val isAtMineHome = checkUrl == mineUrl || checkUrl.contains("mycenter=1") || checkUrl.contains("mod=logging")
        when {
            fromHistory -> {
                val currentIndex = mineWebView.copyBackForwardList().currentIndex
                if (currentIndex > rootHistoryIndex + 1 && mineWebView.canGoBack()) {
                    timeoutJob?.cancel()
                    mineWebView.goBack()
                } else {
                    timeoutJob?.cancel()
                    navController.popBackStack()
                }
            }

            needFallbackToHome -> {
                needFallbackToHome = false
                timeoutJob?.cancel()
                startLoading(mineWebView, mineUrl)
            }

            evaluateCanGoBack(mineWebView) -> {
                timeoutJob?.cancel()
                mineWebView.goBack()
            }

            !isAtMineHome -> {
                timeoutJob?.cancel()
                startLoading(mineWebView, mineUrl)
            }

            else -> {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    activity?.finish()
                }
            }
        }
    }

    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var lockedNavHeightValue by rememberSaveable { mutableFloatStateOf(0f) }

    SideEffect {
        if (navBarsPadding.value > lockedNavHeightValue) {
            lockedNavHeightValue = navBarsPadding.value
        }
    }
    val lockedNavHeight = lockedNavHeightValue.dp

    val statusBarsPaddingVal = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var lockedStatusHeightValue by rememberSaveable { mutableFloatStateOf(0f) }

    SideEffect {
        if (statusBarsPaddingVal.value > lockedStatusHeightValue) {
            lockedStatusHeightValue = statusBarsPaddingVal.value
        }
    }
    val lockedStatusHeight = lockedStatusHeightValue.dp
    val isFullscreen = isFullscreenState.value || autoOpenMangaMode
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    LaunchedEffect(isFullscreen, isImeVisible) {
        bottomNavBarVM.updateBottomBarSuppressed(!isFullscreen && !isImeVisible)
    }

    val topSpacerColor = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.primary
    val bottomPad = when {
        isFullscreen -> lockedNavHeight
        isImeVisible -> 0.dp
        else -> lockedNavHeight + 50.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(lockedStatusHeight)
                .background(topSpacerColor)
                .align(Alignment.TopCenter)
                .zIndex(1f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = lockedStatusHeight,
                    bottom = bottomPad
                )
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SwipeRefreshLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setOnRefreshListener { triggerMinePullRefresh() }
                        guardWebViewPullRefresh(mineWebView, pullRefreshBridge)
                        swipeRefresh = this
                        (mineWebView.parent as? ViewGroup)?.removeView(mineWebView)
                        addView(
                            mineWebView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                update = { container ->
                    container.guardWebViewPullRefresh(mineWebView, pullRefreshBridge)
                    canGoBack = evaluateCanGoBack(mineWebView)
                    currentUrl = mineWebView.url
                    pageTitle = mineWebView.title ?: ""
                },
                onRelease = { _ ->
                    timeoutJob?.cancel()

                    val nextRoute = navController.currentDestination?.route.orEmpty()
                    val keepAliveForChildPage =
                        nextRoute.startsWith("NativeMangaPage") || nextRoute.startsWith("ReaderPage")

                    mineWebViewPauseRunnable?.let {
                        mineWebViewHandler.removeCallbacks(it)
                        mineWebViewPauseRunnable = null
                    }

                    if (!keepAliveForChildPage) {
                        val runnable = Runnable {
                            try {
                                mineWebView.onPause()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        mineWebViewPauseRunnable = runnable
                        mineWebViewHandler.postDelayed(runnable, 3000L)
                    }
                }
            )
            ReaderModeFAB(
                visible = canConvertToReader && !isLoading && !showLoadError && !isFullscreenState.value,
                onClick = {
                    currentUrl?.let { url ->
                        val cleanUrl = url.substringBefore("#")

                        savedMangaUrl = cleanUrl

                        ReaderModeDetector.extractThreadPath(cleanUrl)?.let { threadPath ->
                            val encodedPath = URLEncoder.encode(threadPath, "utf-8")
                            navController.navigate("ReaderPage/$encodedPath")
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 150.dp)
            )

            if (showLoadError) {
                YamiboLoadError(
                    title = "网页无法打开",
                    onRetry = {
                        val currentWebViewUrl = mineWebView.url
                        if (!currentWebViewUrl.isNullOrEmpty() && currentWebViewUrl != "about:blank") {
                            startLoading(mineWebView, currentWebViewUrl)
                        } else {
                            startLoading(mineWebView, mineUrl)
                        }
                    }
                )
            }

            val expectedLoadTarget = currentExpectedLoadTarget()
            val shouldBlockOldWebContent = isLoading && !isPullRefreshing && (
                    fromHistory || !isSamePageTargetUrl(mineWebView.url, expectedLoadTarget)
                    )

            // 只要当前 WebView 还不是本次目标页面，就用不透明遮罩挡住旧 DOM。
            // 这样从历史帖子返回 MinePage 时，不会在个人主页提交前露出刚才的帖子。
            // 只有 fromHistory 时需要主动吞掉触摸事件，防止用户点到旧 DOM 的残留交互。
            androidx.compose.animation.AnimatedVisibility(
                visible = shouldBlockOldWebContent,
                enter = EnterTransition.None,
                exit = fadeOut()
            ) {
                val blockModifier = if (fromHistory) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { }
                        detectVerticalDragGestures { _, _ -> }
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .then(blockModifier)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (autoOpenMangaMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { }
                            detectVerticalDragGestures { _, _ -> }
                        }
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }

            when (mineDialog) {
                MineDialogState.None -> Unit

                MineDialogState.Settings -> {
                    val isDnsOptimizationEnabled by
                    GlobalData.isDnsOptimizationEnabled.collectAsState()
                    val isAutoVersionUpdateEnabled by
                    GlobalData.isAutoVersionUpdateEnabled.collectAsState()
                    val isAutoClearCacheEnabled by
                    GlobalData.isAutoClearCacheEnabled.collectAsState()
                    val isAutoSignInEnabled = GlobalData.isAutoSignInEnabled.value
                    var cacheSizeBytes by remember { mutableStateOf(0L) }
                    var isClearingCache by remember { mutableStateOf(false) }
                    var showClearCacheDialog by remember { mutableStateOf(false) }
                    var showBackupDialog by remember { mutableStateOf(false) }
                    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
                    var showImportDoneDialog by remember { mutableStateOf(false) }
                    var isBackupWorking by remember { mutableStateOf(false) }

                    val exportBackupLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/zip")
                    ) { uri ->
                        if (uri != null) {
                            isBackupWorking = true
                            scope.launch {
                                BackupUtil.exportBackup(context, uri)
                                    .onSuccess {
                                        YamiboToast.show(message = strings.backupExported)
                                    }
                                    .onFailure {
                                        YamiboToast.show(
                                            message = strings.backupFailed.format(it.message ?: "")
                                        )
                                    }
                                isBackupWorking = false
                                showBackupDialog = false
                            }
                        }
                    }
                    val importBackupLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) pendingImportUri = uri
                    }

                    fun setLanguageMode(mode: String) {
                        val normalized = LanguageModeUtil.normalize(mode)
                        if (LanguageModeUtil.normalize(languageMode) == normalized) return
                        GlobalData.languageMode.value = normalized
                        SettingsUtil.saveLanguageMode(normalized)
                        LanguageModeUtil.applyForumCookies(normalized, mineWebView.url)
                        mineWebView.evaluateJavascript(
                            PageJsScripts.getLanguageSetJs(normalized, forceForumSwitch = true),
                            null
                        )
                        LanguageModeUtil.applyLocale(context, normalized)
                    }

                    fun formatFileSize(bytes: Long): String = when {
                        bytes < 1024L -> "$bytes B"
                        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
                        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                    }

                    LaunchedEffect(Unit) {
                        cacheSizeBytes = withContext(Dispatchers.IO) {
                            val imageSize = context.imageLoader.diskCache?.size ?: 0L
                            val novelSize = LocalCacheUtil.getInstance(context).index.value
                                .values
                                .sumOf { cache -> cache.pages.values.sumOf { it.fileSize } }
                            imageSize + novelSize
                        }
                    }

                    Dialog(onDismissRequest = { mineDialog = MineDialogState.None }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    top = 24.dp,
                                    end = 24.dp,
                                    bottom = 16.dp
                                )
                            ) {
                                Text(
                                    strings.settings,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(18.dp))
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                mineDialog = MineDialogState.None
                                                navController.navigate("NativeThemePage")
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("主题", fontSize = 15.sp)
                                            Text(
                                                "主题模式、主题色彩与纯黑模式",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            "${GlobalData.themeMode.value.label} · ${GlobalData.themePalette.value.label}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.language, fontSize = 15.sp)
                                            Text(
                                                strings.languageDesc,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = LanguageModeUtil.normalize(languageMode) == LanguageModeUtil.TRADITIONAL,
                                            onCheckedChange = { useTraditional ->
                                                setLanguageMode(
                                                    if (useTraditional) LanguageModeUtil.TRADITIONAL
                                                    else LanguageModeUtil.SIMPLIFIED
                                                )
                                            },
                                            colors = yamiboSwitchColors(),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.networkOptimization, fontSize = 15.sp)
                                            Text(
                                                strings.networkOptimizationDesc,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = isDnsOptimizationEnabled,
                                            onCheckedChange = { enabled ->
                                                GlobalData.isDnsOptimizationEnabled.value = enabled
                                                SettingsUtil.saveDnsOptimizationEnabled(enabled)
                                            },
                                            colors = yamiboSwitchColors(),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.blockPosts, fontSize = 15.sp)
                                            Text(
                                                strings.blockPostsDesc,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = isForumBlocklistEnabled,
                                            onCheckedChange = ForumBlocklistManager::setEnabled,
                                            colors = yamiboSwitchColors(),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.autoSignIn, fontSize = 15.sp)
                                            Text(
                                                strings.autoSignInDesc,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = isAutoSignInEnabled,
                                            onCheckedChange = { enabled ->
                                                GlobalData.isAutoSignInEnabled.value = enabled
                                                SettingsUtil.saveAutoSignInMode(enabled)
                                                if (enabled) {
                                                    scope.launch(Dispatchers.IO) {
                                                        AutoSignManager.resetQuota()
                                                        AutoSignManager.checkAndSignIfNeeded(
                                                            context,
                                                            force = true,
                                                        )
                                                    }
                                                }
                                            },
                                            colors = yamiboSwitchColors(),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.autoVersionUpdate, fontSize = 15.sp)
                                            Text(
                                                strings.autoVersionUpdateDesc,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = isAutoVersionUpdateEnabled,
                                            onCheckedChange = { enabled ->
                                                GlobalData.isAutoVersionUpdateEnabled.value = enabled
                                                SettingsUtil.saveAutoVersionUpdateMode(enabled)
                                            },
                                            colors = yamiboSwitchColors(),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.autoClearCache, fontSize = 15.sp)
                                            Text(
                                                strings.autoClearCacheDesc.format(CacheMaintenance.RETENTION_DAYS),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = isAutoClearCacheEnabled,
                                            onCheckedChange = { enabled ->
                                                GlobalData.isAutoClearCacheEnabled.value = enabled
                                                SettingsUtil.saveAutoClearCacheMode(enabled)
                                                CacheMaintenance.onAutoClearChanged(context, enabled)
                                            },
                                            colors = yamiboSwitchColors(),
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showClearCacheDialog = true },
                                    enabled = !isClearingCache,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isClearingCache) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(strings.clearCache + "（${formatFileSize(cacheSizeBytes)}）")
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { showBackupDialog = true },
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text(strings.backup)
                                    }
                                    TextButton(
                                        onClick = { mineDialog = MineDialogState.Blocklist },
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text(strings.blacklist)
                                    }
                                    TextButton(
                                        onClick = {
                                            mineDialog = MineDialogState.None
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KrelinnBios/YamiboPlus/issues/new"))
                                            )
                                        },
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text(strings.feedback)
                                    }
                                    TextButton(
                                        onClick = {
                                            mineDialog = MineDialogState.None
                                            YamiboToast.show(message = strings.checkingUpdate)
                                            scope.launch {
                                                when (val result = AppUpdateManager.checkForUpdate()) {
                                                    AppUpdateCheckResult.NoUpdate ->
                                                        YamiboToast.show(
                                                            message = strings.noNewVersion.format(BuildConfig.VERSION_NAME)
                                                        )

                                                    is AppUpdateCheckResult.UpdateAvailable ->
                                                        manualUpdateInfo = result.info

                                                    is AppUpdateCheckResult.Failed ->
                                                        manualUpdateFailure = result.reason
                                                }
                                            }
                                        },
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text(strings.checkUpdate)
                                    }
                                    TextButton(
                                        onClick = { mineDialog = MineDialogState.None },
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text(strings.close)
                                    }
                                }
                            }
                        }
                    }

                    if (showClearCacheDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearCacheDialog = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            textContentColor = MaterialTheme.colorScheme.onSurface,
                            title = { Text(strings.clearCache, fontSize = 18.sp) },
                            text = {
                                Text(
                                    strings.clearCacheConfirmText,
                                    fontSize = 15.sp
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showClearCacheDialog = false
                                    isClearingCache = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                CacheMaintenance.clearAll(context)
                                            }
                                            cacheSizeBytes = 0L
                                        } finally {
                                            isClearingCache = false
                                        }
                                    }
                                }) {
                                    Text(
                                        strings.confirm,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 15.sp
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearCacheDialog = false }) {
                                    Text(strings.cancel, fontSize = 15.sp)
                                }
                            }
                        )
                    }

                    if (showBackupDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!isBackupWorking) showBackupDialog = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            textContentColor = MaterialTheme.colorScheme.onSurface,
                            title = { Text(strings.backup, fontSize = 18.sp) },
                            text = { Text(strings.backupDialogText, fontSize = 14.sp) },
                            confirmButton = {
                                TextButton(
                                    enabled = !isBackupWorking,
                                    onClick = {
                                        exportBackupLauncher.launch(BackupUtil.suggestedFileName())
                                    }
                                ) {
                                    Text(strings.exportBackup, fontSize = 15.sp)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !isBackupWorking,
                                    onClick = {
                                        importBackupLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream"
                                            )
                                        )
                                    }
                                ) {
                                    Text(strings.importBackup, fontSize = 15.sp)
                                }
                            }
                        )
                    }

                    pendingImportUri?.let { importUri ->
                        AlertDialog(
                            onDismissRequest = { if (!isBackupWorking) pendingImportUri = null },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            textContentColor = MaterialTheme.colorScheme.onSurface,
                            title = { Text(strings.importConfirmTitle, fontSize = 18.sp) },
                            text = { Text(strings.importConfirmText, fontSize = 15.sp) },
                            confirmButton = {
                                TextButton(
                                    enabled = !isBackupWorking,
                                    onClick = {
                                        isBackupWorking = true
                                        scope.launch {
                                            BackupUtil.importBackup(context, importUri)
                                                .onSuccess {
                                                    showImportDoneDialog = true
                                                }
                                                .onFailure {
                                                    YamiboToast.show(
                                                        message = strings.backupFailed
                                                            .format(it.message ?: "")
                                                    )
                                                }
                                            isBackupWorking = false
                                            pendingImportUri = null
                                        }
                                    }
                                ) {
                                    Text(
                                        strings.confirm,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !isBackupWorking,
                                    onClick = { pendingImportUri = null }
                                ) {
                                    Text(strings.cancel, fontSize = 15.sp)
                                }
                            }
                        )
                    }

                    if (showImportDoneDialog) {
                        AlertDialog(
                            // 恢复完成后运行中的 DataStore 缓存已过期，必须重启，不允许点外部关闭
                            onDismissRequest = {},
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            textContentColor = MaterialTheme.colorScheme.onSurface,
                            title = { Text(strings.importDoneTitle, fontSize = 18.sp) },
                            text = { Text(strings.importDoneText, fontSize = 15.sp) },
                            confirmButton = {
                                TextButton(onClick = { BackupUtil.restartApp(context) }) {
                                    Text(strings.restartNow, fontSize = 15.sp)
                                }
                            }
                        )
                    }
                }

                MineDialogState.Blocklist -> {
                    ForumBlocklistDialog(
                        onDismiss = { mineDialog = MineDialogState.Settings },
                        onOpenPost = { url ->
                            mineDialog = MineDialogState.None
                            GlobalData.pendingClipboardUrl.value = url
                            GlobalData.lastClipboardUrl = url
                        }
                    )
                }
            }

            manualUpdateInfo?.let { info ->
                AppUpdateDialog(
                    info = info,
                    onDismiss = { manualUpdateInfo = null }
                )
            }
            manualUpdateFailure?.let { reason ->
                AppUpdateFailureDialog(
                    reason = reason,
                    onDismiss = { manualUpdateFailure = null }
                )
            }

        }
    }
}

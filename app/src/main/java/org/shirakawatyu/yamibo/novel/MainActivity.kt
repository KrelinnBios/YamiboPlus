package org.shirakawatyu.yamibo.novel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.repository.ForumRepository
import org.shirakawatyu.yamibo.novel.ui.page.FavoritePage
import org.shirakawatyu.yamibo.novel.ui.page.MangaWebPage
import org.shirakawatyu.yamibo.novel.ui.page.MangaHomePage
import org.shirakawatyu.yamibo.novel.ui.page.MinePage
import org.shirakawatyu.yamibo.novel.ui.page.NativeMangaPage
import org.shirakawatyu.yamibo.novel.ui.page.NativeForumPage
import org.shirakawatyu.yamibo.novel.ui.page.NativeThreadPage
import org.shirakawatyu.yamibo.novel.ui.page.HistoryPage
import org.shirakawatyu.yamibo.novel.ui.page.OtherWebPage
import org.shirakawatyu.yamibo.novel.ui.page.ReaderPage
import org.shirakawatyu.yamibo.novel.ui.page.ReaderWebPage
import org.shirakawatyu.yamibo.novel.ui.component.AppUpdateDialog
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.theme._300文学Theme
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.ui.widget.BottomNavBar
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingOverlay
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingStep
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToastHost
import org.shirakawatyu.yamibo.novel.util.AccountSyncManager
import org.shirakawatyu.yamibo.novel.util.AppUpdateInfo
import org.shirakawatyu.yamibo.novel.util.AppUpdateCheckResult
import org.shirakawatyu.yamibo.novel.util.AppUpdateManager
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
import org.shirakawatyu.yamibo.novel.util.CurrentUserUtil
import org.shirakawatyu.yamibo.novel.util.OnboardingUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.SignTrigger
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import org.shirakawatyu.yamibo.novel.util.Waf405RecoveryManager
import org.shirakawatyu.yamibo.novel.util.darkThemeColor
import org.shirakawatyu.yamibo.novel.util.network.NetworkMonitor
import java.net.URLDecoder
import kotlin.text.RegexOption
import androidx.core.graphics.toColorInt
import androidx.navigation.NavGraph.Companion.findStartDestination
import org.shirakawatyu.yamibo.novel.util.reader.rememberScreenCorner

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cookies")

class MainActivity : ComponentActivity() {


    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        uploadMessage?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        uploadMessage = null
    }
    private val customWebChromeClient by lazy { createWebChromeClient() }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                GlobalData.webProgress.value = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (uploadMessage != null) {
                    uploadMessage?.onReceiveValue(null)
                    uploadMessage = null
                }
                uploadMessage = filePathCallback

                try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                    } else {
                        uploadMessage = null
                        return false
                    }
                } catch (_: Exception) {
                    uploadMessage = null
                    return false
                }
                return true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        GlobalData.dataStore = applicationContext.dataStore
        GlobalData.displayMetrics = resources.displayMetrics
        GlobalData.homePageRoute.value = "MangaHomePage"
        GlobalData.isDarkMode.value = false
        GlobalData.darkModeTheme.value = 0
        GlobalData.lightModeTheme.value = 0
        super.onCreate(savedInstanceState)
        LanguageModeUtil.applyLocale(this, runCatching { kotlinx.coroutines.runBlocking { SettingsUtil.getLanguageMode() } }.getOrDefault(LanguageModeUtil.SIMPLIFIED))

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutParams = window.attributes
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = layoutParams
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.statusBarColor = "#551200".toColorInt()
        window.navigationBarColor = "#EEE1BE".toColorInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }


        // 只登记前台 Activity；真正收到 WAF 405 时才创建短生命周期挑战页。
        Waf405RecoveryManager.start(this)

        handleDeepLink(intent)

        setContent {
            App(webChromeClient = customWebChromeClient)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = YamiboPostLinkUtil.normalizePostUrl(intent.dataString) ?: return
        GlobalData.pendingDeepLinkUrl.value = url
    }

    @Deprecated("论坛入口已原生化，仅用于销毁遗留页面的失效渲染器")
    fun recreateBbsWebViewAfterRendererGone(deadView: WebView?) {
        runCatching {
            (deadView?.parent as? android.view.ViewGroup)?.removeView(deadView)
            deadView?.destroy()
        }
    }
    override fun onStart() {
        super.onStart()
        Waf405RecoveryManager.start(this)
    }

    override fun onStop() {
        Waf405RecoveryManager.stop(this)
        super.onStop()
    }

    override fun onDestroy() {
        GlobalData.isAppInitialized = false
        super.onDestroy()
    }
}
private suspend fun openNativeForumPost(
    navController: NavController,
    url: String
): Boolean {
    val threadId = withContext(Dispatchers.IO) {
        ForumRepository().resolveThreadId(url)
    } ?: return false
    navController.navigate("NativeThreadPage/" + threadId) {
        launchSingleTop = true
    }
    return true
}
@Composable
private fun ClipboardLinkHint(
    url: String,
    lockedNavHeight: Dp,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(120)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(80f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = lockedNavHeight + 52.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 4.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
                ) {
                    Text(
                        text = "检测到百合会链接",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                    ) {
                        Surface(
                            modifier = Modifier.clickable { onDismiss() },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "以后再说",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            modifier = Modifier.clickable {
                                GlobalData.lastClipboardUrl = url
                                scope.launch {
                                    openNativeForumPost(navController, url)
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "打开",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun App(webChromeClient: WebChromeClient) {
    val isAppInitialized = GlobalData.isAppInitialized
    val context = LocalContext.current
    val isNetworkAvailable by remember {
        NetworkMonitor.observeNetwork(context)
    }.collectAsState(initial = false)
    val homeRoute by GlobalData.homePageRoute.collectAsState()
    val isAutoVersionUpdateEnabled by GlobalData.isAutoVersionUpdateEnabled.collectAsState()

    LaunchedEffect(Unit) {
        if (!GlobalData.isAppInitialized) {
            try {
                GlobalData.currentCookie = GlobalData.cookieFlow.first()
            } catch (_: Exception) {
                GlobalData.currentCookie = ""
            } finally {
                GlobalData.homePageRoute.value = "MangaHomePage"
                SettingsUtil.getFavoriteCollapseMode { GlobalData.isFavoriteCollapsed.value = it }
                SettingsUtil.getCustomDnsMode { GlobalData.isCustomDnsEnabled.value = it }
                SettingsUtil.getClickToTopMode { GlobalData.isClickToTopEnabled.value = it }
                SettingsUtil.getAutoSignInMode { GlobalData.isAutoSignInEnabled.value = it }
                GlobalData.isAutoVersionUpdateEnabled.value =
                    SettingsUtil.getAutoVersionUpdateMode()
                SettingsUtil.getDnsOptimizationEnabled {
                    GlobalData.isDnsOptimizationEnabled.value = it
                }
                SettingsUtil.getDnsOptimizationMode { GlobalData.dnsOptimizationMode.value = it }
                SettingsUtil.getCustomDnsUrl { GlobalData.customDnsUrl.value = it }
                SettingsUtil.getDarkMode { GlobalData.isDarkMode.value = it }
                val savedLanguageMode = SettingsUtil.getLanguageMode()
                GlobalData.languageMode.value = savedLanguageMode
                LanguageModeUtil.applyForumCookies(savedLanguageMode)
                CurrentUserUtil.load()
                GlobalData.darkModeTheme.value = 0
                GlobalData.lightModeTheme.value = 0
                GlobalData.isAppInitialized = true
            }
        }
    }

    var isFirstResume by remember { mutableStateOf(true) }
    var showClipboardHint by remember { mutableStateOf(false) }
    var detectedClipboardUrl by remember { mutableStateOf("") }
    var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // 用 remember 而非 rememberSaveable：进程被杀后从最近任务恢复时应重新检查，
    // 否则恢复的 true 会让这次冷启动跳过检查；频率控制交给 checkForUpdateAuto 的节流。
    var hasCheckedAppUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(
        isAppInitialized,
        isNetworkAvailable,
        isAutoVersionUpdateEnabled
    ) {
        if (isAppInitialized &&
            isNetworkAvailable &&
            isAutoVersionUpdateEnabled &&
            !hasCheckedAppUpdate
        ) {
            hasCheckedAppUpdate = true
            // 自动检查：节流 + 静默。失败（网络/限流 403 等）不打扰用户，
            // 需要明确结果时由设置页「检查更新」手动触发。
            when (val result = AppUpdateManager.checkForUpdateAuto()) {
                null -> Unit
                AppUpdateCheckResult.NoUpdate -> Unit
                is AppUpdateCheckResult.UpdateAvailable -> appUpdateInfo = result.info
                is AppUpdateCheckResult.Failed -> Unit
            }
        }
    }
    LaunchedEffect(isAppInitialized, isNetworkAvailable) {
        if (isAppInitialized && isNetworkAvailable) {
            launch(Dispatchers.IO) {
                delay(3000L)
                if (GlobalData.isAutoSignInEnabled.value && AutoSignManager.needsSignIn(SignTrigger.LAUNCH)) {
                    AutoSignManager.checkAndSignIfNeeded(context, SignTrigger.LAUNCH)
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // 2. 后台切回 (Resume) 触发逻辑
    DisposableEffect(lifecycleOwner, isAppInitialized, isNetworkAvailable) {
        if (!isAppInitialized || !isNetworkAvailable) return@DisposableEffect onDispose {}

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    AccountSyncManager.syncCookieAndCheckSign(context, "APP_RESUME")

                    if (isFirstResume) {
                        isFirstResume = false
                    } else {
                        if (GlobalData.isAutoSignInEnabled.value && AutoSignManager.needsSignIn(
                                SignTrigger.RESUME
                            )
                        ) {
                            delay(3000L)
                            AutoSignManager.checkAndSignIfNeeded(context, SignTrigger.RESUME)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        coroutineScope.launch(Dispatchers.IO) {
            AccountSyncManager.syncCookieAndCheckSign(context, "APP_INIT")
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 剪贴板检测：首次进入或切回前台时检查是否复制了百合会帖子链接
    DisposableEffect(lifecycleOwner, isAppInitialized) {
        if (!isAppInitialized) return@DisposableEffect onDispose {}

        fun inspectClipboard() {
            if (showClipboardHint) return
            coroutineScope.launch {
                delay(300L)
                if (showClipboardHint) return@launch
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager ?: return@launch
                val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0) ?: return@launch
                val text = item.text?.toString()
                    ?: item.uri?.toString()
                    ?: item.coerceToText(context)?.toString()
                val matched = YamiboPostLinkUtil.extractPostUrl(text)
                if (matched != null && matched != GlobalData.lastClipboardUrl) {
                    detectedClipboardUrl = matched
                    showClipboardHint = true
                }
            }
        }

        val clipboardObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) inspectClipboard()
        }
        lifecycleOwner.lifecycle.addObserver(clipboardObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            inspectClipboard()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(clipboardObserver)
        }
    }
    _300文学Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isAppInitialized) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        val navController = rememberNavController()
                        val enterEasing = FastOutSlowInEasing
                        val exitEasing = FastOutLinearInEasing
                        val enterDuration = 380
                        val exitDuration = 300
                        val stateOwner = LocalViewModelStoreOwner.current
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val bottomNavBarVM: BottomNavBarVM = viewModel(stateOwner!!)

                        // Deep link: 从其他 app 点击百合会链接跳转进来
                        LaunchedEffect(Unit) {
                            GlobalData.pendingDeepLinkUrl.collect { url ->
                                if (url == null) return@collect
                                GlobalData.lastClipboardUrl = url
                                GlobalData.pendingDeepLinkUrl.value = null
                                val opened = runCatching {
                                    openNativeForumPost(navController, url)
                                }.getOrDefault(false)
                                if (!opened) {
                                    navController.navigate("BBSPage") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        val context = LocalContext.current
                        val pageList =
                            listOf("MangaHomePage", "FavoritePage", "BBSPage", "MinePage")
                        val selectedItemIndex =
                            pageList.indexOf(currentRoute ?: homeRoute).coerceAtLeast(0)
                        var activeTopLevelRoute by rememberSaveable(homeRoute) {
                            mutableStateOf(homeRoute)
                        }

                        LaunchedEffect(currentRoute) {
                            activeTopLevelRoute = when {
                                currentRoute in pageList -> currentRoute!!
                                currentRoute == "HistoryPage" ||
                                        currentRoute?.startsWith("MineHistoryPostPage") == true -> "MinePage"
                                else -> activeTopLevelRoute
                            }
                            val isReaderRoute =
                                currentRoute?.startsWith("ReaderPage") == true ||
                                        currentRoute?.startsWith("NativeMangaPage") == true
                            bottomNavBarVM.setBottomNavBarVisibility(!isReaderRoute)
                        }

                        OnboardingOverlay(
                            page = OnboardingUtil.Page.BOTTOM_NAV,
                            enabled = GlobalData.currentUid.isNotBlank() && bottomNavBarVM.showBottomNavBar,
                            steps = listOf(
                                OnboardingStep(
                                    title = "底栏操作小提示",
                                    description = "单击底栏图标：切换到对应板块；已在该板块时单击不会重新加载。"
                                ),
                                OnboardingStep(
                                    title = "底栏操作小提示",
                                    description = "长按底栏图标：直接回到该板块主页（论坛首页/个人资料/漫画首页/收藏首页）。"
                                ),
                                OnboardingStep(
                                    title = "底栏操作小提示",
                                    description = "下拉页面：刷新当前内容，长按已不再用于刷新。"
                                )
                            )
                        )

                        val density = androidx.compose.ui.platform.LocalDensity.current.density

                        val initialNavHeight = remember(context, density) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val wm =
                                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                                val insets =
                                    wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                                        android.view.WindowInsets.Type.navigationBars() or android.view.WindowInsets.Type.displayCutout()
                                    )
                                insets.bottom / density
                            } else {
                                0f
                            }
                        }

                        val navBarsPadding =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val currentPaddingValue = navBarsPadding.value

                        var lockedNavHeightValue by rememberSaveable {
                            mutableFloatStateOf(
                                initialNavHeight
                            )
                        }

                        SideEffect {
                            if (currentPaddingValue > lockedNavHeightValue) {
                                lockedNavHeightValue = currentPaddingValue
                            }
                        }

                        val lockedNavHeight = maxOf(currentPaddingValue, lockedNavHeightValue).dp

                        Box(modifier = Modifier.fillMaxSize()) {
                            val statusBarColor = when {
                                currentRoute == "MangaHomePage" -> YamiboColors.primary
                                currentRoute == "FavoritePage" -> YamiboColors.onSurface
                                currentRoute == "BBSPage" -> YamiboColors.primary
                                currentRoute == "MinePage" || currentRoute?.startsWith("MineHistoryPostPage") == true -> YamiboColors.primary
                                currentRoute?.startsWith("OtherWebPage") == true || currentRoute?.startsWith("ReaderWebPage") == true -> YamiboColors.primary
                                currentRoute == "HistoryPage" -> YamiboColors.primary
                                else -> null
                            }
                            if (statusBarColor != null) {
                                SetStatusBarColor(statusBarColor)
                            }
                            val topLevelRoutes =
                                listOf("MangaHomePage", "FavoritePage", "BBSPage", "MinePage")
                            NavHost(
                                modifier = Modifier.fillMaxSize(),
                                navController = navController,
                                startDestination = homeRoute
                            ) {
                                composable(
                                    "MangaHomePage",
                                    enterTransition = {
                                        if (initialState.destination.route in topLevelRoutes) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(tween(150))
                                        }
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route in topLevelRoutes) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(tween(150))
                                        }
                                    },
                                    popEnterTransition = {
                                        if (initialState.destination.route in topLevelRoutes) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(tween(150))
                                        }
                                    },
                                    popExitTransition = {
                                        if (targetState.destination.route in topLevelRoutes) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(tween(150))
                                        }
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        MangaHomePage(navController = navController)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "MangaHomePage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "FavoritePage",
                                    enterTransition = {
                                        if (initialState.destination.route in topLevelRoutes) EnterTransition.None
                                        else fadeIn(tween(150))
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route?.run { startsWith("ReaderPage") || this == "HistoryPage" || startsWith("MineHistoryPostPage") } == true) {
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 3 },
                                                animationSpec = tween(
                                                    enterDuration,
                                                    easing = enterEasing
                                                )
                                            )
                                        } else if (targetState.destination.route?.startsWith("NativeMangaPage") == true || targetState.destination.route in topLevelRoutes) {
                                            ExitTransition.None
                                        } else fadeOut(tween(150))
                                    },
                                    popEnterTransition = {
                                        if (initialState.destination.route?.run { startsWith("ReaderPage") || this == "HistoryPage" || startsWith("MineHistoryPostPage") } == true) {
                                            slideInHorizontally(
                                                initialOffsetX = { -it / 3 },
                                                animationSpec = tween(exitDuration, easing = exitEasing)
                                            )
                                        } else if (initialState.destination.route?.startsWith("NativeMangaPage") == true || initialState.destination.route in topLevelRoutes) {
                                            EnterTransition.None
                                        } else fadeIn(tween(150))
                                    },
                                    popExitTransition = {
                                        if (targetState.destination.route in topLevelRoutes) ExitTransition.None
                                        else fadeOut(tween(150))
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        FavoritePage(
                                            viewModel(
                                                stateOwner,
                                                factory = ViewModelFactory(context.applicationContext)
                                            ),
                                            navController
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "FavoritePage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }

                                        val isContentPage = currentRoute?.run {
                                            startsWith("ReaderPage") || startsWith("NativeMangaPage") || startsWith(
                                                "MangaWebPage"
                                            ) || startsWith("OtherWebPage") || startsWith("ReaderWebPage") || this == "HistoryPage" || startsWith("MineHistoryPostPage")
                                        } == true
                                        val maskAlpha by animateFloatAsState(
                                            targetValue = if (isContentPage) 0.5f else 0f,
                                            animationSpec = tween(
                                                if (isContentPage) enterDuration else exitDuration,
                                                easing = if (isContentPage) enterEasing else exitEasing
                                            ),
                                            label = "FavoriteMask"
                                        )
                                        if (maskAlpha > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        androidx.compose.ui.graphics.Color.Black.copy(
                                                            alpha = maskAlpha
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "BBSPage",
                                    enterTransition = {
                                        if (initialState.destination.route in topLevelRoutes) EnterTransition.None
                                        else fadeIn(tween(150))
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route?.startsWith("NativeThreadPage") == true) {
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 3 },
                                                animationSpec = tween(enterDuration, easing = enterEasing)
                                            )
                                        } else if (targetState.destination.route in topLevelRoutes) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(tween(150))
                                        }
                                    },
                                    popEnterTransition = {
                                        if (initialState.destination.route?.startsWith("NativeThreadPage") == true) {
                                            slideInHorizontally(
                                                initialOffsetX = { -it / 3 },
                                                animationSpec = tween(exitDuration, easing = exitEasing)
                                            )
                                        } else if (initialState.destination.route in topLevelRoutes) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(tween(150))
                                        }
                                    },
                                    popExitTransition = {
                                        if (targetState.destination.route in topLevelRoutes) ExitTransition.None
                                        else fadeOut(tween(150))
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        NativeForumPage(
                                            onThreadClick = { thread ->
                                                navController.navigate("NativeThreadPage/" + thread.id)
                                            }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "BBSPage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }
                                    }
                                }
                                composable(
                                    route = "NativeThreadPage/{threadId}",
                                    arguments = listOf(
                                        navArgument("threadId") { type = NavType.StringType }
                                    ),
                                    enterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(enterDuration, easing = enterEasing)
                                        )
                                    },
                                    exitTransition = {
                                        fadeOut(tween(150))
                                    },
                                    popEnterTransition = {
                                        fadeIn(tween(150))
                                    },
                                    popExitTransition = {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(exitDuration, easing = exitEasing)
                                        )
                                    }
                                ) { entry ->
                                    val threadId = entry.arguments?.getString("threadId").orEmpty()
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        NativeThreadPage(
                                            threadId = threadId,
                                            onBack = navController::popBackStack,
                                            onOpenLink = { url ->
                                                val linkedThreadId =
                                                    YamiboPostLinkUtil.extractThreadId(url)
                                                if (linkedThreadId != null) {
                                                    navController.navigate(
                                                        "NativeThreadPage/" + linkedThreadId
                                                    )
                                                } else {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse(url)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "NativeThreadPage",
                                                selectedRoute = "BBSPage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "MinePage",
                                    enterTransition = {
                                        if (initialState.destination.route in topLevelRoutes) EnterTransition.None
                                        else fadeIn(tween(150))
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route?.run { startsWith("ReaderPage") || this == "HistoryPage" || startsWith("MineHistoryPostPage") } == true) {
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 3 },
                                                animationSpec = tween(
                                                    enterDuration,
                                                    easing = enterEasing
                                                )
                                            )
                                        } else if (targetState.destination.route in topLevelRoutes) {
                                            ExitTransition.None
                                        } else fadeOut(tween(150))
                                    },
                                    popEnterTransition = {
                                        val initialRoute = initialState.destination.route
                                        when {
                                            // 从 HistoryPage 返回 MinePage 时不要再播放 MinePage 的抽屉回弹动画。
                                            // 这样 HistoryPage 自身已经不画退出动画，MinePage 也不会“从左侧补一段动画”。
                                            initialRoute == "HistoryPage" -> EnterTransition.None
                                            initialRoute?.run { startsWith("ReaderPage") || startsWith("MineHistoryPostPage") } == true -> {
                                                slideInHorizontally(
                                                    initialOffsetX = { -it / 3 },
                                                    animationSpec = tween(exitDuration, easing = exitEasing)
                                                )
                                            }
                                            initialRoute?.startsWith("NativeMangaPage") == true || initialRoute in topLevelRoutes -> {
                                                EnterTransition.None
                                            }
                                            else -> fadeIn(tween(150))
                                        }
                                    },
                                    popExitTransition = {
                                        if (targetState.destination.route in topLevelRoutes) ExitTransition.None
                                        else fadeOut(tween(150))
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        MinePage(
                                            isSelected = selectedItemIndex == 3,
                                            navController = navController,
                                            webChromeClient = webChromeClient
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "MinePage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }

                                        val isContentPage = currentRoute?.run {
                                            startsWith("ReaderPage") || startsWith("NativeMangaPage") || startsWith(
                                                "MangaWebPage"
                                            ) || startsWith("OtherWebPage") || startsWith("ReaderWebPage") || this == "HistoryPage" || startsWith("MineHistoryPostPage")
                                        } == true
                                        val maskAlpha by animateFloatAsState(
                                            targetValue = if (isContentPage) 0.5f else 0f,
                                            animationSpec = tween(
                                                if (isContentPage) enterDuration else exitDuration,
                                                easing = if (isContentPage) enterEasing else exitEasing
                                            ),
                                            label = "MineMask"
                                        )
                                        if (maskAlpha > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        androidx.compose.ui.graphics.Color.Black.copy(
                                                            alpha = maskAlpha
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "ReaderPage/{passageUrl}",
                                    arguments = listOf(navArgument("passageUrl") {
                                        type = NavType.StringType
                                    }),
                                    enterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(enterDuration, easing = enterEasing)
                                        )
                                    },
                                    popExitTransition = {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(exitDuration, easing = exitEasing)
                                        )
                                    }
                                ) {
                                    it.arguments?.getString("passageUrl")?.let { url ->
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ReaderPage(
                                                url = URLDecoder.decode(url, "utf-8"),
                                                navController = navController
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "MangaWebPage/{url}/{originalUrl}?fastForward={fastForward}&initialPage={initialPage}",
                                    arguments = listOf(
                                        navArgument("url") { type = NavType.StringType },
                                        navArgument("originalUrl") { type = NavType.StringType },
                                        navArgument("fastForward") {
                                            type = NavType.BoolType; defaultValue = false
                                        },
                                        navArgument("initialPage") {
                                            type = NavType.IntType; defaultValue = 0
                                        }
                                    ),
                                    enterTransition = {
                                        if (
                                            initialState.destination.route?.startsWith("FavoritePage") == true ||
                                            initialState.destination.route?.startsWith("MangaHomePage") == true
                                        ) EnterTransition.None
                                        else fadeIn(tween(150))
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route?.startsWith("NativeMangaPage") == true) ExitTransition.None
                                        else fadeOut(tween(150))
                                    },
                                    popEnterTransition = {
                                        if (initialState.destination.route?.startsWith("NativeMangaPage") == true) EnterTransition.None
                                        else fadeIn(tween(150))
                                    }
                                ) {
                                    val loadUrl =
                                        URLDecoder.decode(it.arguments?.getString("url") ?: "", "utf-8")
                                    val originalUrl = URLDecoder.decode(
                                        it.arguments?.getString("originalUrl") ?: "",
                                        "utf-8"
                                    )
                                    val fastForward = it.arguments?.getBoolean("fastForward") ?: false
                                    val initialPage = it.arguments?.getInt("initialPage") ?: 0

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        MangaWebPage(
                                            url = loadUrl,
                                            originalFavoriteUrl = originalUrl,
                                            navController = navController,
                                            webChromeClient = webChromeClient,
                                            isFastForward = fastForward,
                                            initialPage = initialPage
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = currentRoute,
                                                navBarVM = bottomNavBarVM,
                                                selectedRoute = activeTopLevelRoute
                                            )
                                        }
                                    }
                                }
                                composable(
                                    "OtherWebPage/{url}",
                                    arguments = listOf(navArgument("url") { type = NavType.StringType })
                                ) {
                                    it.arguments?.getString("url")?.let { url ->
                                        OtherWebPage(
                                            url = URLDecoder.decode(url, "utf-8"),
                                            navController = navController,
                                            webChromeClient = webChromeClient
                                        )
                                    }
                                }
                                composable(
                                    "ReaderWebPage/{url}",
                                    arguments = listOf(navArgument("url") { type = NavType.StringType })
                                ) {
                                    it.arguments?.getString("url")?.let { url ->
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ReaderWebPage(
                                                url = URLDecoder.decode(url, "utf-8"),
                                                navController = navController,
                                                webChromeClient = webChromeClient
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = lockedNavHeight)
                                            ) {
                                                BottomNavBar(
                                                    navController = navController,
                                                    currentRoute = currentRoute,
                                                    navBarVM = bottomNavBarVM,
                                                    selectedRoute = activeTopLevelRoute
                                                )
                                            }
                                        }
                                    }
                                }
                                composable(
                                    route = "HistoryPage",
                                    enterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(enterDuration, easing = enterEasing)
                                        )
                                    },
                                    exitTransition = {
                                        if (targetState.destination.route?.startsWith("MineHistoryPostPage") == true) {
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 3 },
                                                animationSpec = tween(enterDuration, easing = enterEasing)
                                            )
                                        } else {
                                            fadeOut(tween(150))
                                        }
                                    },
                                    popEnterTransition = {
                                        if (initialState.destination.route?.startsWith("MineHistoryPostPage") == true) {
                                            slideInHorizontally(
                                                initialOffsetX = { -it / 3 },
                                                animationSpec = tween(exitDuration, easing = exitEasing)
                                            )
                                        } else {
                                            fadeIn(tween(150))
                                        }
                                    },
                                    popExitTransition = {
                                        if (targetState.destination.route == "MinePage") {
                                            // HistoryPage -> MinePage 是返回个人页，不再显示右滑退出动画。
                                            // 保留 MineHistoryPostPage -> HistoryPage 的抽屉感，但彻底避免最终退出时历史界面残留。
                                            ExitTransition.None
                                        } else {
                                            slideOutOfContainer(
                                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                                animationSpec = tween(exitDuration, easing = exitEasing)
                                            )
                                        }
                                    }
                                ) {
                                    // 1. 获取动态物理屏幕圆角
                                    val screenCorner = rememberScreenCorner()
                                    // 2. 仅对左上、左下应用圆角裁剪，保持与 ReaderPage 完全一致的抽屉感
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(topStart = screenCorner, bottomStart = screenCorner))
                                    ) {
                                        HistoryPage(navController = navController)

                                        val isContentPage = currentRoute?.startsWith("MineHistoryPostPage") == true
                                        val maskAlpha by animateFloatAsState(
                                            targetValue = if (isContentPage) 0.5f else 0f,
                                            animationSpec = tween(
                                                if (isContentPage) enterDuration else exitDuration,
                                                easing = if (isContentPage) enterEasing else exitEasing
                                            ),
                                            label = "HistoryMask"
                                        )
                                        if (maskAlpha > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        androidx.compose.ui.graphics.Color.Black.copy(
                                                            alpha = maskAlpha
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                                composable(
                                    route = "MineHistoryPostPage?url={url}",
                                    arguments = listOf(navArgument("url") { defaultValue = "" }),
                                    enterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(enterDuration, easing = enterEasing)
                                        )
                                    },
                                    exitTransition = {
                                        val targetRoute = targetState.destination.route
                                        when {
                                            targetRoute?.startsWith("ReaderPage") == true -> {
                                                slideOutHorizontally(
                                                    targetOffsetX = { -it / 3 },
                                                    animationSpec = tween(
                                                        enterDuration,
                                                        easing = enterEasing
                                                    )
                                                )
                                            }
                                            targetRoute?.startsWith("NativeMangaPage") == true -> {
                                                ExitTransition.None
                                            }
                                            else -> {
                                                fadeOut(tween(150))
                                            }
                                        }
                                    },
                                    popEnterTransition = {
                                        val initialRoute = initialState.destination.route
                                        when {
                                            initialRoute?.startsWith("ReaderPage") == true -> {
                                                slideInHorizontally(
                                                    initialOffsetX = { -it / 3 },
                                                    animationSpec = tween(exitDuration, easing = exitEasing)
                                                )
                                            }
                                            initialRoute?.startsWith("NativeMangaPage") == true -> {
                                                EnterTransition.None
                                            }
                                            else -> {
                                                fadeIn(tween(150))
                                            }
                                        }
                                    },
                                    popExitTransition = {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(exitDuration, easing = exitEasing)
                                        )
                                    }
                                ) { backStackEntry ->
                                    val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "utf-8")
                                    // 1. 获取动态物理屏幕圆角
                                    val screenCorner = rememberScreenCorner()
                                    // 2. 同样仅对左侧应用圆角
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(topStart = screenCorner, bottomStart = screenCorner))
                                    ) {
                                        MinePage(
                                            isSelected = true,
                                            navController = navController,
                                            webChromeClient = webChromeClient,
                                            initUrl = url,
                                            fromHistory = true
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = lockedNavHeight)
                                        ) {
                                            BottomNavBar(
                                                navController = navController,
                                                currentRoute = "MineHistoryPostPage",
                                                navBarVM = bottomNavBarVM
                                            )
                                        }
                                    }
                                }
                                composable(
                                    route = "NativeMangaPage?url={url}&originalUrl={originalUrl}",
                                    arguments = listOf(
                                        navArgument("url") { defaultValue = "" },
                                        navArgument("originalUrl") { defaultValue = "" }
                                    ),
                                    enterTransition = {
                                        scaleIn(
                                            initialScale = 0.50f,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                300,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    },
                                    exitTransition = { ExitTransition.None },
                                    popEnterTransition = { EnterTransition.None },
                                    popExitTransition = {
                                        scaleOut(
                                            targetScale = 0.8f,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        ) + fadeOut(
                                            targetAlpha = 0.01f,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        )
                                    }
                                ) { backStackEntry ->
                                    val url = backStackEntry.arguments?.getString("url") ?: ""
                                    val originalUrl = backStackEntry.arguments?.getString("originalUrl")
                                        ?.takeIf { it.isNotBlank() } ?: url

                                    NativeMangaPage(
                                        url = url,
                                        originalUrl = originalUrl,
                                        navController = navController
                                    )
                                }
                            }

                            if (showClipboardHint && detectedClipboardUrl.isNotEmpty()) {
                                ClipboardLinkHint(
                                    url = detectedClipboardUrl,
                                    lockedNavHeight = lockedNavHeight,
                                    navController = navController,
                                    onDismiss = {
                                        GlobalData.lastClipboardUrl = detectedClipboardUrl
                                        showClipboardHint = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = darkThemeColor(YamiboColors.secondary) { surfaceVariant }
                        )
                    }
                } // isAppInitialized else 结束

                appUpdateInfo?.let { info ->
                    AppUpdateDialog(
                        info = info,
                        onDismiss = { appUpdateInfo = null }
                    )
                }

                YamiboToastHost(modifier = Modifier.padding(bottom = 50.dp))
            } // 外层 Box 结束
        }
    }
}

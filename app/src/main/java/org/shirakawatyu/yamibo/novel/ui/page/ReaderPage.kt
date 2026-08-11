package org.shirakawatyu.yamibo.novel.ui.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.ui.component.ReaderProgressSlider
import org.shirakawatyu.yamibo.novel.ui.component.ReaderSettingSlider
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.GlobalChapter
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.ui.theme.ReaderTheme
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
import org.shirakawatyu.yamibo.novel.ui.vm.ReaderVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingOverlay
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingStep
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.ui.widget.reader.ContentViewer
import org.shirakawatyu.yamibo.novel.ui.widget.reader.CustomStatusBar
import org.shirakawatyu.yamibo.novel.util.OnboardingUtil
import org.shirakawatyu.yamibo.novel.util.darkModeColor
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner
import org.shirakawatyu.yamibo.novel.util.reader.ReaderReturnBridge
import org.shirakawatyu.yamibo.novel.util.reader.ReaderSpacingOptions
import org.shirakawatyu.yamibo.novel.util.reader.rememberScreenCorner
import kotlin.math.roundToInt

fun typefaceFromMode(mode: Int): Typeface = when (mode) {
    1 -> Typeface.create("sans-serif-medium", Typeface.NORMAL) // 黑体
    2 -> Typeface.create("serif", Typeface.NORMAL)             // 宋体
    else -> Typeface.DEFAULT                                    // 系统
}

/**
 * 阅读器页面，用于格式化显示原论坛内容
 *
 * @param readerVM 默认为新实例
 * @param url 要显示的网页的 URL
 * @param navController 用于顶部栏的返回按钮的导航
 */
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ReaderPage(
    readerVM: ReaderVM = viewModel(
        factory = ViewModelFactory(LocalContext.current.applicationContext)
    ),
    url: String = "",
    navController: NavController
) {
    val uiState by readerVM.uiState.collectAsState()
    val languageMode by GlobalData.languageMode.collectAsState()
    val currentPercentage by readerVM.currentPercentage.collectAsState()
    val favoriteVM: FavoriteVM = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = ViewModelFactory(LocalContext.current.applicationContext)
    )
    val currentTypeface = Typeface.DEFAULT
    // 全屏与状态栏高度处理
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    val view = remember(window) { window?.decorView }
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var rememberedStatusBarHeightValue by rememberSaveable { mutableFloatStateOf(0f) }
    SideEffect {
        if (statusBarsPadding.value > rememberedStatusBarHeightValue) {
            rememberedStatusBarHeightValue = statusBarsPadding.value
        }
    }
    val statusBarHeight =
        if (rememberedStatusBarHeightValue > 0f) rememberedStatusBarHeightValue.dp else 28.dp
    var rememberedNavBarHeightValue by rememberSaveable { mutableFloatStateOf(0f) }
    SideEffect {
        if (navBarsPadding.value > rememberedNavBarHeightValue) {
            rememberedNavBarHeightValue = navBarsPadding.value
        }
    }
    var pullOverscrollAmount by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density
    val triggerDistancePx = 150f * density   // 触发加载的阈值
    val showUiDistancePx = 40f * density    // 显示提示的阈值
    // 记录进入阅读器前的系统栏状态，退出时恢复，避免上一页上下栏抖动
    val originalBehavior = remember { mutableIntStateOf(0) }
    var hasCapturedOriginal by remember { mutableStateOf(false) }
    var lastVolKeyTime by remember { mutableLongStateOf(0L) }
    var isExiting by remember { mutableStateOf(false) }
    var isFirstEnter by remember { mutableStateOf(true) }
    val favoritesState = FavoriteUtil.getFavoriteFlow().collectAsState(initial = emptyList())
    val readerIdentityUrl = remember(url) {
        FavoriteUtil.normalizeUrl(url)
    }

    OnboardingOverlay(
        page = OnboardingUtil.Page.NOVEL_READER,
        enabled = GlobalData.currentUid.isNotBlank(),
        steps = listOf(
            OnboardingStep(
                title = "小说阅读小提示",
                description = "点击正文可呼出菜单，菜单里能调节字号、行距、页边距和横向/纵向翻页方式。"
            ),
            OnboardingStep(
                title = "小说阅读小提示",
                description = "菜单里的目录支持跨论坛页聚合全书章节，可按序号或章节号搜索、正倒序排列。"
            ),
            OnboardingStep(
                title = "小说阅读小提示",
                description = "阅读进度会自动记录，下次打开同一本书会跳回上次的位置。"
            )
        )
    )

    val bookTitle by remember(readerIdentityUrl, favoritesState.value) {
        derivedStateOf {
            val rawTitle = favoritesState.value.find { it.url == readerIdentityUrl }?.title ?: ""
            rawTitle.replace(Regex("(\\[.*?]|【.*?】|\\(.*?\\)|（.*?）)"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
    val onRefreshAction = remember(readerVM) { { readerVM.forceRefreshCurrentPage() } }
    val onSetViewAction = remember(readerVM) { { viewIndex: Int -> readerVM.onSetView(viewIndex) } }
    val onSetLineHeightAction =
        remember(readerVM) { { lineHeight: TextUnit -> readerVM.onSetLineHeight(lineHeight) } }
    val onSetFontSizeAction =
        remember(readerVM) { { fontSize: TextUnit -> readerVM.onSetFontSize(fontSize) } }
    val onSetPaddingAction =
        remember(readerVM) { { padding: Dp -> readerVM.onSetPadding(padding) } }
    val onShowChaptersAction = remember(readerVM) { { readerVM.toggleChapterDrawer(true) } }
    val pagerState = rememberPagerState(pageCount = { uiState.htmlList.size })
    val lazyListState = rememberLazyListState()
    val headerAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentPageIndex by remember(uiState.isVerticalMode, uiState.htmlList.size) {
        derivedStateOf {
            val maxIndex = (uiState.htmlList.size - 1).coerceAtLeast(0)
            if (uiState.isVerticalMode) {
                lazyListState.firstVisibleItemIndex.coerceIn(0, maxIndex)
            } else {
                pagerState.settledPage.coerceIn(0, maxIndex)
            }
        }
    }
    LaunchedEffect(languageMode) {
        readerVM.syncGlobalTranslationMode(languageMode, currentPageIndex)
    }

    val onSetPageAction = remember(lazyListState, pagerState, scope, readerVM) {
        { pageIndex: Int ->
            scope.launch {
                if (readerVM.uiState.value.isVerticalMode) lazyListState.scrollToItem(pageIndex)
                else pagerState.scrollToPage(pageIndex)
            }
            Unit
        }
    }
    val onSetReadingModeAction = remember(readerVM) {
        { isVertical: Boolean ->
            readerVM.setReadingMode(isVertical, currentPageIndex)
        }
    }
    DisposableEffect(window, view) {
        if (window == null || view == null) {
            onDispose { }
        } else {
            val windowController = WindowCompat.getInsetsController(window, view)
            if (!hasCapturedOriginal) {
                originalBehavior.value = windowController.systemBarsBehavior
                hasCapturedOriginal = true
            }
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            windowController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                windowController.systemBarsBehavior = originalBehavior.intValue

                view.postDelayed({
                    try {
                        windowController.show(WindowInsetsCompat.Type.systemBars())
                    } catch (_: Exception) {
                    }
                }, 180L)
            }
        }
    }
    ReaderTheme {
        val themeBackground = MaterialTheme.colorScheme.background
        val finalBackground = themeBackground
        val targetStatusBarColor = MaterialTheme.colorScheme.primary
        val statusBarContentColor =
            remember { androidx.compose.animation.Animatable(Color.Transparent) }
        LaunchedEffect(targetStatusBarColor) {
            delay(400)
            statusBarContentColor.animateTo(
                targetValue = targetStatusBarColor,
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
            )
        }
        var showSettings by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val smoothScrollAnimation =
            remember { tween<Float>(durationMillis = 432, easing = EaseOut) }
        val focusRequester = remember { FocusRequester() }
        val screenCorner = rememberScreenCorner()
        LaunchedEffect(uiState.showChapterDrawer) {
            if (uiState.showChapterDrawer) drawerState.open() else drawerState.close()
        }
        LaunchedEffect(drawerState.isOpen) {
            if (!drawerState.isOpen) {
                readerVM.toggleChapterDrawer(false)
                showSettings = false
            }
        }
        val currentChapterTitle =
            if (uiState.htmlList.isNotEmpty() && currentPageIndex < uiState.htmlList.size) {
                uiState.htmlList[currentPageIndex].chapterTitle
            } else {
                null
            }
        var pendingReaderReturnJump by remember(url) {
            mutableStateOf(ReaderReturnBridge.takePendingJumpForUrl(url))
        }
        // 章节目录里点「其它论坛页」的章节时的跨页跳转：切到目标论坛页加载后，按页内序号定位。
        var pendingChapterJump by remember(url) {
            mutableStateOf<GlobalChapter?>(null)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
        val isAnimationFinished = lifecycleState == Lifecycle.State.RESUMED
        val hasRealContent = remember(uiState.htmlList) {
            uiState.htmlList.size > 1 || uiState.htmlList.any { it.chapterTitle != "footer" }
        }
        val openedFromFavorite = remember {
            navController.previousBackStackEntry?.destination?.route == "FavoritePage"
        }
        var automaticRefreshTriggered by remember(url) { mutableStateOf(false) }
        LaunchedEffect(openedFromFavorite, hasRealContent) {
            if (openedFromFavorite && hasRealContent && !automaticRefreshTriggered) {
                automaticRefreshTriggered = true
                readerVM.forceRefreshCurrentPage()
            }
        }
        LaunchedEffect(
            pendingReaderReturnJump?.id,
            hasRealContent,
            uiState.currentView,
            uiState.htmlList.size,
            uiState.isVerticalMode
        ) {
            val jump = pendingReaderReturnJump ?: return@LaunchedEffect
            if (!hasRealContent) return@LaunchedEffect

            if (uiState.currentView != jump.webPage) {
                readerVM.onSetView(jump.webPage)
                return@LaunchedEffect
            }

            val targetIndex = (jump.readerPageIndex ?: 0)
                .coerceIn(0, (uiState.htmlList.size - 1).coerceAtLeast(0))

            awaitFrame()
            awaitFrame()
            if (uiState.isVerticalMode) {
                lazyListState.scrollToItem(targetIndex)
                readerVM.onVerticalPageSettled(targetIndex)
            } else if (pagerState.pageCount > targetIndex) {
                pagerState.scrollToPage(targetIndex)
                readerVM.onPageChange(pagerState, scope)
            }
            pendingReaderReturnJump = null
        }
        LaunchedEffect(
            pendingChapterJump,
            hasRealContent,
            uiState.currentView,
            uiState.chapterList,
            uiState.htmlList.size,
            uiState.isVerticalMode,
            readerVM.showLoadingScrim
        ) {
            val jump = pendingChapterJump ?: return@LaunchedEffect
            if (uiState.currentView != jump.webPage) {
                readerVM.onSetView(jump.webPage)
                return@LaunchedEffect
            }
            // 必须等目标页彻底加载完（loadFinished 置 showLoadingScrim=false）再定位，
            // 否则可能用切页瞬间仍是旧页的 chapterList 误算位置。
            if (readerVM.showLoadingScrim || !hasRealContent) return@LaunchedEffect

            val targetIndex = (uiState.chapterList.getOrNull(jump.orderInPage)?.startIndex ?: 0)
                .coerceIn(0, (uiState.htmlList.size - 1).coerceAtLeast(0))

            awaitFrame()
            awaitFrame()
            if (uiState.isVerticalMode) {
                lazyListState.scrollToItem(targetIndex)
                readerVM.onVerticalPageSettled(targetIndex)
            } else if (pagerState.pageCount > targetIndex) {
                pagerState.scrollToPage(targetIndex)
                readerVM.onPageChange(pagerState, scope)
            }
            pendingChapterJump = null
        }
        LaunchedEffect(showSettings) {
            if (isExiting) return@LaunchedEffect
            val windowController = window?.let { WindowCompat.getInsetsController(it, view!!) }
            if (windowController != null) {
                if (showSettings) {
                    windowController.show(WindowInsetsCompat.Type.systemBars())
                    window.statusBarColor = android.graphics.Color.BLACK
                    windowController.isAppearanceLightStatusBars = false
                    isFirstEnter = false
                } else {
                    if (isFirstEnter) {
                        delay(180)
                        isFirstEnter = false
                    }
                    windowController.hide(WindowInsetsCompat.Type.systemBars())
                    delay(220)
                    window.statusBarColor = android.graphics.Color.BLACK
                    windowController.isAppearanceLightStatusBars = false
                }
            }
        }
        var settingsOnOpen by remember {
            mutableStateOf<Triple<TextUnit, TextUnit, Dp>?>(null)
        }
        val isLoading = readerVM.showLoadingScrim
        var showImageWarning by remember { mutableStateOf(false) }
        val exitReader: () -> Unit = remember(window, view, navController, readerVM) {
            {
                readerVM.saveCurrentHistory()
                if (window != null && view != null) {
                    isExiting = true
                    view.clearFocus()
                }
                navController.navigateUp()
            }
        }
        val returnToOriginalPost: () -> Unit =
            remember(window, view, navController, favoriteVM, readerVM, currentPageIndex, url) {
                {
                    readerVM.saveCurrentHistory()
                    if (window != null && view != null) {
                        isExiting = true
                        view.clearFocus()
                    }
                    favoriteVM.nextResumeStrategy = FavoriteVM.RefreshStrategy.SMART
                    val currentState = readerVM.uiState.value
                    val returnContext = ReaderReturnBridge.captureFromReader(
                        readerUrl = readerVM.url.ifBlank { url },
                        authorId = currentState.authorId,
                        currentView = currentState.currentView,
                        readerPageIndex = currentPageIndex,
                        cacheTitle = bookTitle.ifBlank { null }
                    )
                    val previousRoute = navController.previousBackStackEntry?.destination?.route
                    val navigateAction = {
                        if (previousRoute == "BBSPage" || previousRoute == "MinePage") {
                            navController.navigateUp()
                        } else if (previousRoute?.startsWith("ReaderWebPage") == true) {
                            // ReaderWebPage 可能已经停在别的页/取消了只看楼主。
                            // 先发一次校正请求，再露出已有 WebView，这样"原贴"看到的是阅读器当前网页页码。
                            readerVM.scheduleDiskCacheRefresh()
                            ReaderReturnBridge.requestOriginalPost(returnContext.originalPostUrl)
                            navController.navigateUp()
                        } else {
                            readerVM.scheduleDiskCacheRefresh()
                            navController.navigate("ReaderWebPage/${ReaderReturnBridge.encodeRouteArg(returnContext.originalPostUrl)}") {
                                navController.currentDestination?.id?.let { currentId ->
                                    popUpTo(currentId) { inclusive = true }
                                }
                            }
                        }
                    }
                    if (view != null) {
                        view.post { navigateAction() }
                    } else {
                        navigateAction()
                    }
                }
            }
        val onToggleFavorite: () -> Unit = remember(readerVM, favoriteVM, bookTitle, readerIdentityUrl) {
            {
                if (uiState.isFavorited) {
                    val favorite = favoritesState.value.find { it.url == readerIdentityUrl }
                    if (favorite != null) {
                        favoriteVM.deleteFavorite(favorite) { msg -> YamiboToast.show(message = msg) }
                    }
                } else {
                    val tid = MangaTitleCleaner.extractTidFromUrl(readerIdentityUrl)
                    if (tid == null) {
                        YamiboToast.show(message = "无法识别帖子链接，请从原帖页面收藏")
                    } else {
                        val title = readerVM.currentThreadTitle?.takeIf(String::isNotBlank)
                            ?: bookTitle
                        favoriteVM.toggleFavorite(
                            url = readerIdentityUrl,
                            title = title,
                            tid = tid
                        ) { msg -> YamiboToast.show(message = msg) }
                    }
                }
            }
        }
        BackHandler(enabled = drawerState.isOpen || showSettings) {
            if (drawerState.isOpen) {
                scope.launch {
                    drawerState.close()
                }
            }
            if (showSettings) {
                val currentState = readerVM.uiState.value
                val settingsNow =
                    Triple(currentState.fontSize, currentState.lineHeight, currentState.padding)
                if (settingsOnOpen != settingsNow) {
                    readerVM.saveSettings(currentPageIndex)
                }
                showSettings = false
            }
        }
        if (showImageWarning) {
            AlertDialog(
                onDismissRequest = { showImageWarning = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("确认加载图片") },
                text = { Text("开启后将加载帖子中的图片，这会显著增加加载时间") },
                confirmButton = {
                    TextButton(onClick = {
                        readerVM.toggleLoadImages(true); showImageWarning = false
                    }) { Text("确认开启") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImageWarning = false
                    }) { Text("取消") }
                }
            )
        }
        LaunchedEffect(showSettings) {
            if (showSettings) {
                settingsOnOpen = Triple(uiState.fontSize, uiState.lineHeight, uiState.padding)
            } else {
                view?.isFocusableInTouchMode = true
                view?.requestFocus()
                awaitFrame()
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {
                }
            }
        }
        LaunchedEffect(uiState.isVerticalMode, lazyListState) {
            if (uiState.isVerticalMode) {
                snapshotFlow {
                    Pair(
                        lazyListState.firstVisibleItemIndex,
                        !lazyListState.isScrollInProgress
                    )
                }
                    .distinctUntilChanged()
                    .collect { (visibleIndex, isSettled) ->
                        if (isSettled && visibleIndex >= 0 && visibleIndex < uiState.htmlList.size) {
                            readerVM.onVerticalPageSettled(visibleIndex)
                        }
                    }
            }
        }
        LaunchedEffect(lazyListState.isScrollInProgress) {
            if (lazyListState.isScrollInProgress) {
                headerAlpha.animateTo(0f, animationSpec = tween(300))
            } else {
                delay(1000)
                headerAlpha.animateTo(1f, animationSpec = tween(300))
            }
        }
        val onSettingsMaskClick = remember(readerVM) {
            {
                val currentState = readerVM.uiState.value
                val settingsNow =
                    Triple(currentState.fontSize, currentState.lineHeight, currentState.padding)

                if (settingsOnOpen != settingsNow) readerVM.saveSettings(currentPageIndex)
                showSettings = false
            }
        }
        val onVerticalBackgroundClick = remember {
            {
                if (!isExiting) {
                    showSettings = true
                }
            }
        }
        // --- 根布局Box ---
        ModalNavigationDrawer(
            modifier = Modifier.clip(RectangleShape),
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ChapterDrawerContent(
                    drawerState = drawerState,
                    globalChapters = uiState.globalChapters,
                    currentChapterList = uiState.chapterList,
                    currentWebPage = uiState.currentView,
                    currentPageIndex = currentPageIndex,
                    isIndexing = uiState.globalChapterIndexing,
                    onChapterClick = { chapter ->
                        readerVM.toggleChapterDrawer(false)
                        if (chapter.webPage == uiState.currentView) {
                            // 本论坛页内：直接滚到该章在当前页的起始片。
                            val startIndex = uiState.chapterList
                                .getOrNull(chapter.orderInPage)?.startIndex ?: 0
                            scope.launch {
                                if (uiState.isVerticalMode) {
                                    lazyListState.scrollToItem(startIndex)
                                } else {
                                    pagerState.scrollToPage(startIndex)
                                }
                            }
                        } else {
                            // 其它论坛页：交给 pending-jump，切页加载后再定位。
                            pendingChapterJump = chapter
                        }
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = screenCorner, bottomStart = screenCorner))
                    .background(finalBackground)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val isVolDown = event.key == Key.VolumeDown
                        val isVolUp = event.key == Key.VolumeUp
                        if (isVolDown || isVolUp) {
                            if (!showSettings && !isLoading) {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastVolKeyTime > 200L) {
                                        lastVolKeyTime = currentTime
                                        scope.launch {
                                            if (uiState.isVerticalMode) {
                                                val layoutInfo = lazyListState.layoutInfo
                                                val visibleItems = layoutInfo.visibleItemsInfo
                                                if (visibleItems.isNotEmpty()) {
                                                    val bufferLines = 3
                                                    val visibleCount = visibleItems.size
                                                    val scrollStep =
                                                        (visibleCount - bufferLines).coerceAtLeast(1)
                                                    if (isVolDown) {
                                                        val targetIndex =
                                                            (lazyListState.firstVisibleItemIndex + scrollStep)
                                                                .coerceAtMost(uiState.htmlList.size - 1)
                                                        lazyListState.animateScrollToItem(index = targetIndex)
                                                    } else {
                                                        val targetIndex =
                                                            (lazyListState.firstVisibleItemIndex - scrollStep)
                                                                .coerceAtLeast(0)
                                                        lazyListState.animateScrollToItem(index = targetIndex)
                                                    }
                                                }
                                            } else {
                                                val target =
                                                    if (isVolDown) pagerState.targetPage + 1 else pagerState.targetPage - 1
                                                pagerState.animateScrollToPage(
                                                    page = target.coerceIn(
                                                        0,
                                                        pagerState.pageCount - 1
                                                    ),
                                                    animationSpec = smoothScrollAnimation
                                                )
                                            }
                                        }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        false
                    }
            ) {
                // 内容层
                Box(modifier = Modifier.fillMaxSize()) {
                    var hasTriggeredLoad by remember(url) { mutableStateOf(false) }
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val screenWidthDp = configuration.screenWidthDp.dp
                    val availableHeightDp = configuration.screenHeightDp.dp - statusBarHeight
                    LaunchedEffect(url, screenWidthDp, availableHeightDp) {
                        if (!hasTriggeredLoad && screenWidthDp > 0.dp && availableHeightDp > 0.dp) {
                            val jump = pendingReaderReturnJump
                            if (jump?.allowCache == true) {
                                readerVM.setExternalCacheIdentity(
                                    enabled = true,
                                    title = jump.cacheTitle
                                )
                            }
                            readerVM.firstLoad(url, availableHeightDp, screenWidthDp)
                            hasTriggeredLoad = true
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = statusBarHeight)
                    ) {
                        // --- 内部状态渲染 ---
                        if (uiState.isError) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_error),
                                    "加载失败",
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "页面加载失败",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { readerVM.retryLoad() }) { Text("重试") }
                            }
                        } else if (hasTriggeredLoad) {
                            if (hasRealContent) {
                                val scrollTargetKey =
                                    "${uiState.currentView}_${uiState.initPage}_${uiState.isVerticalMode}_${uiState.htmlList.size}"
                                var appliedScrollTargetKey by remember {
                                    mutableStateOf<String?>(null)
                                }
                                LaunchedEffect(scrollTargetKey, hasRealContent) {
                                    if (hasRealContent && appliedScrollTargetKey != scrollTargetKey) {
                                        if (uiState.isVerticalMode) {
                                            if (lazyListState.firstVisibleItemIndex != uiState.initPage) {
                                                lazyListState.scrollToItem(uiState.initPage)
                                            }
                                        } else {
                                            if (pagerState.pageCount > uiState.initPage && pagerState.currentPage != uiState.initPage) {
                                                pagerState.scrollToPage(uiState.initPage)
                                            }
                                        }
                                        appliedScrollTargetKey = scrollTargetKey
                                    }
                                }
                                val contentAlpha by animateFloatAsState(
                                    targetValue = if (appliedScrollTargetKey == scrollTargetKey) 1f else 0f,
                                    animationSpec = tween(durationMillis = 150),
                                    label = "contentAlphaFadeIn"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = contentAlpha }
                                ) {
                                    if (uiState.isVerticalMode) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    onClick = onVerticalBackgroundClick
                                                )
                                                .padding(horizontal = uiState.padding),
                                            state = lazyListState
                                        ) {
                                            itemsIndexed(
                                                items = uiState.htmlList,
                                                key = { index, item -> "${uiState.currentView}_${index}_${item.type}" }
                                            ) { index, content ->
                                                ContentViewer(
                                                    data = content,
                                                    padding = uiState.padding,
                                                    lineHeight = uiState.lineHeight,
                                                    letterSpacing = uiState.letterSpacing,
                                                    fontSize = uiState.fontSize,
                                                    currentPage = index + 1,
                                                    pageCount = uiState.htmlList.size,
                                                    nightMode = false,
                                                    backgroundColor = finalBackground,
                                                    isVerticalMode = true,
                                                    onRefresh = onRefreshAction,
                                                    bookTitle = bookTitle,
                                                    typeface = currentTypeface
                                                )
                                            }
                                        }
                                    } else {
                                        HorizontalPager(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onTap = { offset ->
                                                            if (isExiting) {
                                                                return@detectTapGestures
                                                            }
                                                            val screenWidth =
                                                                size.width.toFloat()
                                                            if (offset.x < screenWidth * 0.25f) {
                                                                scope.launch {
                                                                    pagerState.animateScrollToPage(
                                                                        (pagerState.currentPage - 1).coerceAtLeast(
                                                                            0
                                                                        ),
                                                                        animationSpec = smoothScrollAnimation
                                                                    )
                                                                }
                                                            } else if (offset.x > screenWidth * 0.75f) {
                                                                scope.launch {
                                                                    pagerState.animateScrollToPage(
                                                                        (pagerState.currentPage + 1).coerceAtMost(
                                                                            pagerState.pageCount - 1
                                                                        ),
                                                                        animationSpec = smoothScrollAnimation
                                                                    )
                                                                }
                                                            } else {
                                                                showSettings = true
                                                            }
                                                        }
                                                    )
                                                },
                                            state = pagerState,
                                        ) { page ->
                                            ContentViewer(
                                                data = uiState.htmlList[page],
                                                padding = uiState.padding,
                                                lineHeight = uiState.lineHeight,
                                                letterSpacing = uiState.letterSpacing,
                                                fontSize = uiState.fontSize,
                                                currentPage = page + 1,
                                                pageCount = uiState.htmlList.size,
                                                nightMode = false,
                                                backgroundColor = finalBackground,
                                                isVerticalMode = false,
                                                onRefresh = onRefreshAction,
                                                bookTitle = bookTitle,
                                                typeface = currentTypeface
                                            )
                                            SideEffect {
                                                readerVM.onPageChange(
                                                    pagerState,
                                                    scope
                                                )
                                            }
                                        }
                                    }
                                }
                                if (uiState.isVerticalMode) {
                                    val effectiveAlpha = if (showSettings) 1f else headerAlpha.value
                                    VerticalModeHeader(
                                        currentPage = currentPageIndex + 1,
                                        pageCount = uiState.htmlList.size,
                                        backgroundColor = finalBackground,
                                        padding = uiState.padding,
                                        alpha = effectiveAlpha
                                    )
                                }
                            }
                        }
                    }
                } // 正文Box End
                // --- 自定义状态栏层 ---
                if (!showSettings) {
                    CustomStatusBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusBarHeight)
                            .align(Alignment.TopCenter),
                        height = statusBarHeight,
                        backgroundColor = finalBackground,
                        contentColor = statusBarContentColor.value,
                        title = ""
                    )
                }
                // --- 设置菜单层---
                if (showSettings) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onSettingsMaskClick
                            )
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = statusBarHeight)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = exitReader) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                            // 顶栏只显示当前目录章节名，与章节目录保持一致。
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentChapterTitle
                                        ?.takeIf { it.isNotBlank() && it != "footer" }
                                        ?: "小说阅读",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                            }
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (uiState.isFavorited) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Outlined.FavoriteBorder
                                    },
                                    contentDescription = if (uiState.isFavorited) "取消收藏" else "收藏",
                                    tint = if (uiState.isFavorited) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        darkModeColor(
                                            light = MaterialTheme.colorScheme.outline,
                                            dark = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                            TextButton(
                                onClick = {
                                    returnToOriginalPost()
                                    showSettings = false
                                }
                            ) {
                                Text(
                                    text = "原帖",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    ReaderSettingsBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        uiState = uiState,
                        currentPercentage = currentPercentage,
                        pageCount = uiState.htmlList.size,
                        currentPage = currentPageIndex,
                        onSetPage = onSetPageAction,
                        onSetFontSize = onSetFontSizeAction,
                        onSetLineHeight = onSetLineHeightAction,
                        onSetPadding = onSetPaddingAction,
                        onShowChapters = onShowChaptersAction,
                        onSetReadingMode = onSetReadingModeAction,
                        onSetLoadImages = { enabled ->
                            if (enabled && !uiState.loadImages) {
                                showImageWarning = true
                            } else if (!enabled && uiState.loadImages) {
                                readerVM.toggleLoadImages(false)
                            }
                        }
                    )
                }
            }
        }
        // 加载遮罩
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ReaderSettingsBar(
    modifier: Modifier = Modifier,
    uiState: ReaderState,
    currentPercentage: Float,
    pageCount: Int,
    currentPage: Int,
    onSetPage: (page: Int) -> Unit,
    onSetFontSize: (fontSize: TextUnit) -> Unit,
    onSetLineHeight: (lineHeight: TextUnit) -> Unit,
    onSetPadding: (padding: Dp) -> Unit,
    onShowChapters: () -> Unit,
    onSetReadingMode: (isVertical: Boolean) -> Unit,
    onSetLoadImages: (Boolean) -> Unit
) {
    var showSpacingMenu by remember { mutableStateOf(false) }
    var chapterPillVisible by remember { mutableStateOf(false) }
    var dynamicChapterTitle by remember { mutableStateOf("") }
    Box(modifier = modifier) {
        // 章节气泡
        androidx.compose.animation.AnimatedVisibility(
            visible = chapterPillVisible && !showSpacingMenu,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)) +
                    androidx.compose.animation.slideInVertically(initialOffsetY = { 30 }),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(500)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-64).dp)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .animateContentSize(
                        animationSpec = tween(150),
                        alignment = Alignment.Center
                    ),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                shape = CircleShape,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = dynamicChapterTitle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                if (showSpacingMenu) {
                    SpacingSettingsMenu(
                        uiState = uiState,
                        onSetFontSize = onSetFontSize,
                        onSetLineHeight = onSetLineHeight,
                        onSetPadding = onSetPadding,
                        onSetReadingMode = onSetReadingMode,
                        onSetLoadImages = onSetLoadImages
                    )
                } else {
                    MainSettingsMenu(
                        uiState = uiState,
                        currentPercentage = currentPercentage,
                        pageCount = pageCount,
                        currentPage = currentPage,
                        onSetPage = onSetPage,
                        onShowSpacingMenu = { showSpacingMenu = true },
                        onShowChapters = onShowChapters,
                        onPillStateChange = { visible, title ->
                            chapterPillVisible = visible
                            dynamicChapterTitle = title
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterDrawerContent(
    drawerState: DrawerState,
    globalChapters: List<GlobalChapter>,
    currentChapterList: List<ChapterInfo>,
    currentWebPage: Int,
    currentPageIndex: Int,
    isIndexing: Boolean,
    onChapterClick: (chapter: GlobalChapter) -> Unit
) {
    val lazyListState = rememberLazyListState()
    // 全书目录尚未建立时（刚打开/单页书）回退展示当前论坛页的章节。
    val chapters = remember(globalChapters, currentChapterList, currentWebPage) {
        if (globalChapters.isNotEmpty()) {
            globalChapters
        } else {
            currentChapterList.mapIndexed { i, c -> GlobalChapter(currentWebPage, i, c.title) }
        }
    }
    // 当前阅读位置对应的章节：先按当前页码定位「页内章节序号」（重名章节不会全高亮第一个），
    // 再在全书目录里找同论坛页、同序号的项。
    val currentChapterIndex = remember(chapters, currentWebPage, currentPageIndex, currentChapterList) {
        val localOrder = currentChapterList
            .indexOfLast { it.startIndex <= currentPageIndex }
            .coerceAtLeast(0)
        chapters.indexOfFirst { it.webPage == currentWebPage && it.orderInPage == localOrder }
            .takeIf { it >= 0 }
            ?: chapters.indexOfLast { it.webPage <= currentWebPage }.coerceAtLeast(0)
    }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // 过滤+排序：保留每章「原始序号」(原 chapters 下标) 用于显示编号与高亮，只改变展示顺序与可见项。
    val processed = remember(chapters, query, ascending) {
        val q = query.trim()
        val base = chapters.mapIndexed { i, c -> i to c }
        val filtered = if (q.isEmpty()) {
            base
        } else {
            base.filter { (i, c) ->
                // 按「序号」(列表编号 i+1) 或「章节号/标题文字」匹配
                (i + 1).toString().contains(q) || c.title.contains(q, ignoreCase = true)
            }
        }
        if (ascending) filtered else filtered.reversed()
    }
    val currentDisplayIndex = remember(processed, currentChapterIndex) {
        processed.indexOfFirst { it.first == currentChapterIndex }.coerceAtLeast(0)
    }
    LaunchedEffect(drawerState.targetValue, currentDisplayIndex) {
        if (drawerState.targetValue == DrawerValue.Open) {
            val targetIndex = (currentDisplayIndex - 4).coerceAtLeast(0)
            lazyListState.scrollToItem(index = targetIndex)
        }
    }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.75f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searchActive) {
                // 紧凑椭圆搜索框：高度与两侧图标按钮一致。
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(
                                            "搜索序号 / 章节号",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            } else {
                Text(
                    "章节目录",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            // 正序 / 倒序 切换（图形化）
            IconButton(onClick = { ascending = !ascending }) {
                Icon(
                    imageVector = if (ascending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (ascending) "当前正序，点按倒序" else "当前倒序，点按正序"
                )
            }
            // 搜索开关（图形化）
            IconButton(onClick = {
                searchActive = !searchActive
                if (!searchActive) query = ""
            }) {
                Icon(
                    imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchActive) "关闭搜索" else "搜索章节"
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState
        ) {
            itemsIndexed(
                items = processed,
                key = { _, pair -> "${pair.second.webPage}_${pair.second.orderInPage}_${pair.first}" }
            ) { _, pair ->
                val originalIndex = pair.first
                val chapter = pair.second
                val isSelected = originalIndex == currentChapterIndex
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (originalIndex + 1).toString(),
                                modifier = Modifier.width(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = chapter.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    selected = isSelected,
                    onClick = { onChapterClick(chapter) },
                    badge = {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            if (processed.isEmpty() && query.isNotBlank()) {
                item(key = "yamibo_no_result") {
                    Text(
                        text = "未找到匹配的章节",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
            if (isIndexing && query.isBlank()) {
                item(key = "yamibo_indexing_hint") {
                    Text(
                        text = "目录补全中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 主设置菜单
 */
@Composable
private fun MainSettingsMenu(
    uiState: ReaderState,
    pageCount: Int,
    currentPercentage: Float,
    currentPage: Int,
    onSetPage: (page: Int) -> Unit,
    onShowSpacingMenu: () -> Unit,
    onShowChapters: () -> Unit,
    onPillStateChange: (Boolean, String) -> Unit = { _, _ -> }
){
    val isVerticalMode = uiState.isVerticalMode
    val sliderValue = if (isVerticalMode) currentPercentage else currentPage.toFloat()
    val sliderRange =
        if (isVerticalMode) 0f..100f else 0f..(pageCount - 1).toFloat().coerceAtLeast(0f)
    var sliderPos by remember(sliderValue) {
        mutableFloatStateOf(sliderValue)
    }
    var pillLastActiveTime by remember { mutableLongStateOf(0L) }
    val targetPageIndex = remember(sliderPos, isVerticalMode, pageCount) {
        if (isVerticalMode) {
            (sliderPos / 100f * pageCount.coerceAtLeast(1).toFloat())
                .toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        } else {
            sliderPos.roundToInt()
        }
    }
    val dynamicChapterTitle = remember(targetPageIndex, uiState.chapterList) {
        val chapter = uiState.chapterList.findLast { it.startIndex <= targetPageIndex }
        chapter?.title ?: "•••"
    }
    LaunchedEffect(pillLastActiveTime, dynamicChapterTitle) {
        if (pillLastActiveTime > 0) {
            onPillStateChange(true, dynamicChapterTitle)
            delay(800)
            onPillStateChange(false, dynamicChapterTitle)
        }
    }

    val currentChapterIndex = uiState.chapterList.indexOfLast { it.startIndex <= currentPage }
    val hasPrevChapter = currentChapterIndex > 0
    val hasNextChapter =
        currentChapterIndex >= 0 && currentChapterIndex < uiState.chapterList.size - 1
    // 统一显示页数，不显示百分比
    val displayText = "${targetPageIndex + 1} / $pageCount"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (hasPrevChapter) {
                        onSetPage(uiState.chapterList[currentChapterIndex - 1].startIndex)
                        pillLastActiveTime = System.currentTimeMillis()
                    }
                },
                enabled = hasPrevChapter
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一章",
                    tint = if (hasPrevChapter) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    }
                )
            }
            ReaderProgressSlider(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                value = sliderPos,
                onValueChange = {
                    sliderPos = it
                    pillLastActiveTime = System.currentTimeMillis()
                },
                onValueChangeFinished = {
                    val targetIndex = if (isVerticalMode) {
                        (sliderPos / 100f * pageCount.coerceAtLeast(1).toFloat())
                            .toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    } else {
                        sliderPos.roundToInt()
                    }
                    onSetPage(targetIndex)
                },
                valueRange = sliderRange
            )
            IconButton(
                onClick = {
                    if (hasNextChapter) {
                        onSetPage(uiState.chapterList[currentChapterIndex + 1].startIndex)
                        pillLastActiveTime = System.currentTimeMillis()
                    }
                },
                enabled = hasNextChapter
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一章",
                    tint = if (hasNextChapter) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                TextButton(
                    onClick = onShowChapters,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        "目录",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayText,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = onShowSpacingMenu,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        "设置",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 二级菜单 - 阅读设置
 */
@Composable
private fun SpacingSettingsMenu(
    uiState: ReaderState,
    onSetFontSize: (fontSize: TextUnit) -> Unit,
    onSetLineHeight: (lineHeight: TextUnit) -> Unit,
    onSetPadding: (padding: Dp) -> Unit,
    onSetReadingMode: (isVertical: Boolean) -> Unit,
    onSetLoadImages: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompactOptionRow(
                label = "翻页方式",
                options = listOf("←→", "↑↓"),
                selectedIndex = if (uiState.isVerticalMode) 1 else 0,
                onSelect = { onSetReadingMode(it == 1) }
            )

            ReaderSettingSlider(
                label = "正文字号",
                value = uiState.fontSize.value,
                options = ReaderSpacingOptions.FONT_SIZES,
                onValueChange = { onSetFontSize(it.sp) }
            )

            ReaderSettingSlider(
                label = "行距",
                value = uiState.lineHeight.value,
                options = ReaderSpacingOptions.lineHeights(uiState.fontSize.value),
                onValueChange = { onSetLineHeight(it.sp) }
            )
            ReaderSettingSlider(
                label = "页边距",
                value = uiState.padding.value,
                options = ReaderSpacingOptions.PAGE_PADDINGS,
                onValueChange = { onSetPadding(it.dp) }
            )
            CompactOptionRow(
                label = "正文图片",
                options = listOf("关闭", "显示"),
                selectedIndex = if (uiState.loadImages) 1 else 0,
                onSelect = { onSetLoadImages(it == 1) }
            )
        }
    }
}

@Composable
private fun CompactOptionRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            modifier = Modifier.width(82.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, option ->
                val selected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 15.sp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalModeHeader(
    currentPage: Int,
    pageCount: Int,
    backgroundColor: Color,
    padding: Dp,
    alpha: Float = 1f
) {
    val chapterTitleHeight = 24.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(chapterTitleHeight)
            .clip(RectangleShape)
            .graphicsLayer { this.alpha = alpha },
        color = backgroundColor.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题不再常驻显示（移入点击弹出的菜单），左侧留空
            Spacer(modifier = Modifier.weight(1f))

            // 页码（右）
            Text(
                text = "$currentPage/${pageCount.coerceAtLeast(1)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 1.dp, top = 4.dp, end = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

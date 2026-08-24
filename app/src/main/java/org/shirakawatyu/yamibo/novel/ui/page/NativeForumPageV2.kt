package org.shirakawatyu.yamibo.novel.ui.page

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumComment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPollOption
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRatePopout
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.component.YamiboDialogSurface
import org.shirakawatyu.yamibo.novel.ui.component.YamiboTextEditorDialog
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.state.ForumSort
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.state.ForumThreadState
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.vm.ForumThreadVM
import org.shirakawatyu.yamibo.novel.ui.vm.ForumVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarLazyListScroll
import org.shirakawatyu.yamibo.novel.util.manga.MangaImagePipeline
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.util.DarkThemeColors
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlockedItem
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlocklistManager
import org.shirakawatyu.yamibo.novel.util.reader.ReaderModeDetector
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast

internal object ForumActionUrls {
    private const val ORIGIN = "https://bbs.yamibo.com"
    val search get() = "$ORIGIN/search.php?mod=forum&mobile=no"
    val home get() = "$ORIGIN/forum.php?mobile=no"
    val creditLog get() = "$ORIGIN/home.php?mod=spacecp&ac=credit&op=log&mobile=no"
    val messages get() = "$ORIGIN/home.php?mod=space&do=pm&page=1&mobile=no"
    val reminders get() = "$ORIGIN/home.php?mod=space&do=notice&mobile=no"
    fun messageCenter(hasNewPrompt: Boolean) = if (hasNewPrompt) reminders else messages
    fun board(fid: String) = desktopBoard(fid)
    fun desktopBoard(fid: String) = "$ORIGIN/forum.php?mod=forumdisplay&fid=$fid&mobile=no"
    fun newThread(fid: String) = "$ORIGIN/forum.php?mod=post&action=newthread&fid=$fid&mobile=no"
    fun thread(tid: String) = desktopThread(tid)
    fun desktopThread(tid: String) = "$ORIGIN/forum.php?mod=viewthread&tid=$tid&mobile=no"
    fun reply(tid: String, fid: String, pid: String? = null, page: Int = 1) = buildString {
        append(ORIGIN).append("/forum.php?mod=post&action=reply&fid=").append(fid)
        append("&tid=").append(tid)
        if (!pid.isNullOrBlank()) append("&repquote=").append(pid)
        append("&page=").append(page.coerceAtLeast(1)).append("&mobile=no")
    }
    fun edit(tid: String, fid: String, pid: String) =
        "$ORIGIN/forum.php?mod=post&action=edit&fid=$fid&tid=$tid&pid=$pid&mobile=no"
    fun comment(tid: String, pid: String) =
        "$ORIGIN/forum.php?mod=misc&action=postreview&tid=$tid&pid=$pid&mobile=no"
    fun rate(tid: String, pid: String) =
        "$ORIGIN/forum.php?mod=misc&action=rate&tid=$tid&pid=$pid&mobile=no"
    fun topicAdmin(fid: String, tid: String) =
        "$ORIGIN/forum.php?mod=topicadmin&action=stick&fid=$fid&tid=$tid&mobile=no"
    fun userSpace(uid: String, section: String) = when (section) {
        "reply" -> "$ORIGIN/home.php?mod=space&uid=$uid&do=thread&view=me&type=reply&mobile=no"
        "thread" -> "$ORIGIN/home.php?mod=space&uid=$uid&do=thread&view=me&mobile=no"
        else -> "$ORIGIN/home.php?mod=space&uid=$uid&do=$section&view=me&mobile=no"
    }
}

private data class NativeForumBlockTarget(
    val type: String,
    val id: String,
    val title: String,
    val authorUid: String = "",
    val authorName: String = "",
    val threadTitle: String = ""
)

private fun isBlocked(type: String, id: String, authorUid: String?, enabled: Boolean, items: List<ForumBlockedItem>) =
    enabled && items.any {
        (it.type == type && it.id == id) ||
            (it.type == ForumBlockedItem.TYPE_USER && it.id == authorUid)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeForumPageV2(
    onThreadClick: (ForumThread) -> Unit,
    onOpenWeb: (String) -> Unit,
    forumVM: ForumVM = viewModel(),
    bottomNavBarVM: BottomNavBarVM
) {
    val state by forumVM.uiState.collectAsState()
    val blocklistEnabled by ForumBlocklistManager.enabled.collectAsState()
    val blockedItems by ForumBlocklistManager.items.collectAsState()
    val listState = rememberLazyListState()
    ObserveBottomBarLazyListScroll(listState, bottomNavBarVM)
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    var blockTarget by remember { mutableStateOf<NativeForumBlockTarget?>(null) }

    LaunchedEffect(Unit) { ForumBlocklistManager.initialize() }
    LaunchedEffect(state.selectedForum?.id) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.page) {
        // 版块主题列表翻页后回到顶部，避免停留在上一页的滚动位置。
        withFrameNanos { }
        listState.scrollToItem(0)
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.goHomeEvent.collect { route ->
            if (route == "BBSPage") {
                listState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.scrollToTopEvent.collect { index ->
            if (index == 2) listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.categoryHomeEvent.collect { index ->
            if (index == 2) {
                forumVM.showForumIndex()
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(
                bottom = if (bottomNavBarVM.bottomBarScrollSuppressed) navBottom else navBottom + 50.dp
            )
    ) {
        ForumTopBarV2(
            title = state.selectedForum?.name ?: "论坛",
            forum = state.selectedForum,
            showLogo = state.selectedForum == null,
            headerColor = headerColor,
            contentColor = headerContent,
            onBack = if (state.selectedForum == null) null else forumVM::showForumIndex
        ) {
            state.selectedForum?.let { forum ->
                val forumFavorited = forumVM.isForumFavorited(forum.id)
                IconButton(onClick = {
                    forumVM.toggleForumFavorite(forum) { success, message ->
                        YamiboToast.show(message = message)
                    }
                }) {
                    Icon(
                        imageVector = if (forumFavorited) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (forumFavorited) "取消收藏" else "收藏本版",
                        // 与日志详情页收藏按钮一致：始终使用顶栏内容色全亮显示。
                        tint = headerContent
                    )
                }
                IconButton(onClick = { onOpenWeb(ForumActionUrls.search) }) {
                    Icon(Icons.Default.Search, "搜索论坛")
                }
                IconButton(onClick = { onOpenWeb(ForumActionUrls.newThread(forum.id)) }) {
                    Icon(Icons.Default.Add, "发表主题")
                }
            }
        }
        val refreshingWithContent = state.isLoading && !currentItemsEmptyV2(state)
        PullToRefreshBox(
            isRefreshing = refreshingWithContent,
            onRefresh = forumVM::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshingWithContent,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            when {
                state.isLoading && currentItemsEmptyV2(state) -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && currentItemsEmptyV2(state) -> ForumErrorV2(state.error.orEmpty(), forumVM::refresh)
                state.selectedForum == null -> ForumIndexListV2(
                    state = state,
                    onForumClick = forumVM::openForum,
                    onBannerClick = { threadId -> onOpenWeb(ForumActionUrls.thread(threadId)) },
                    listState = listState
                )
                else -> ForumThreadListV2(
                    state,
                    onThreadClick,
                    { thread ->
                        if (thread.authorId != GlobalData.currentUid) {
                            blockTarget = NativeForumBlockTarget(
                                ForumBlockedItem.TYPE_THREAD,
                                thread.id,
                                thread.subject,
                                thread.authorId.orEmpty(),
                                thread.authorName
                            )
                        }
                    },
                    forumVM::goToPage,
                    listState,
                    blocklistEnabled,
                    blockedItems,
                    forumVM::setSort,
                    forumVM::setFilterType,
                    forumVM::openForum
                )
            }
        }
    }
    blockTarget?.let { NativeBlockMenuV2(it) { blockTarget = null } }
}

@Composable
private fun ForumTopBarV2(
    title: String,
    forum: ForumBoard? = null,
    showLogo: Boolean = false,
    headerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val logoStartPadding = when {
        configuration.screenWidthDp >= 840 -> 24.dp
        configuration.screenWidthDp >= 600 -> 20.dp
        else -> 16.dp
    }
    Surface(color = headerColor, contentColor = contentColor) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .height(if (showLogo) 56.dp else 74.dp)
                .padding(start = if (showLogo) logoStartPadding else 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack == null && forum != null) Spacer(Modifier.width(48.dp)) else if (onBack != null) IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, forumText(36820, 22238))
            }
            if (showLogo) {
                val context = LocalContext.current
                val logoRequest = remember(context) {
                    ImageRequest.Builder(context)
                        .data("https://bbs.yamibo.com/template/oyeeh_com_baihe_f_x35/img/300-logo-m.png")
                        .addHeader("User-Agent", RequestConfig.UA)
                        .addHeader("Referer", "https://bbs.yamibo.com/")
                        .build()
                }
                AsyncImage(
                    model = logoRequest,
                    contentDescription = "百合会论坛",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(48.dp)
                        .padding(top = 7.dp, bottom = 7.dp)
                )
                Spacer(Modifier.weight(1f))
            } else {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    forum?.let { f ->
                        Column(
                            modifier = Modifier.width(76.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(forumText(20027, 39064) + f.threadCount, color = contentColor, fontSize = 13.sp, maxLines = 1)
                            Text(forumText(20170, 26085) + f.todayPostCount, color = contentColor, fontSize = 13.sp, maxLines = 1)
                        }
                        // 相邻搜索/发布图标的可见间距为 24dp；排名两侧保持同样留白。
                        Spacer(Modifier.width(24.dp))
                        Column(
                            modifier = Modifier.width(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(forumText(25490, 21517), color = contentColor, fontSize = 12.sp)
                            Text(f.rank?.toString() ?: "--", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        // 搜索 IconButton 自带 12dp 左内边距，这里再补 12dp，合计同为 24dp。
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
            Row(content = actions)
        }
    }
}

private fun currentItemsEmptyV2(state: ForumState) =
    if (state.selectedForum == null) state.categories.isEmpty() else state.threads.isEmpty()

@Composable
private fun ForumIndexListV2(
    state: ForumState,
    onForumClick: (ForumBoard) -> Unit,
    onBannerClick: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (state.categories.isEmpty()) return EmptyForumMessageV2("暂无可浏览板块")
    val banners = state.banners
    val pagerState = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(banners.size) {
        if (banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % banners.size)
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
        if (banners.isNotEmpty()) {
            item("forum-home-banner", contentType = "banner") {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.63f)
                ) { page ->
                    ForumHomeBannerV2(banners[page], onBannerClick)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        items(state.categories, key = { "category-${it.id}" }) { category ->
            ForumCategoryCardV2(category, onForumClick)
        }
    }
}

@Composable
private fun ForumHomeBannerV2(
    banner: ForumBanner,
    onBannerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val request = remember(context, banner.imageUrl) {
        ImageRequest.Builder(context)
            .data(banner.imageUrl)
            .crossfade(true)
            .addHeader("User-Agent", RequestConfig.UA)
            .addHeader("Referer", "https://bbs.yamibo.com/")
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "百合会论坛头图",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = banner.threadId != null) {
                banner.threadId?.let(onBannerClick)
            },
        loading = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        error = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun ForumCategoryCardV2(
    category: ForumCategory,
    onForumClick: (ForumBoard) -> Unit
) {
    var expandedForumId by rememberSaveable(category.id) { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category.name,
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${category.forums.size} 个板块",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            category.forums.forEach { forum ->
                val hasSubforums = forum.subforums.isNotEmpty()
                val isExpanded = expandedForumId == forum.id

                ForumBoardRowV2(
                    forum = forum,
                    onClick = { onForumClick(forum) },
                    onLongClick = if (hasSubforums) {
                        { expandedForumId = toggleExpandedForumId(expandedForumId, forum.id) }
                    } else null
                )
                if (isExpanded && hasSubforums) {
                    forum.subforums.forEach { sub ->
                        ForumSubBoardRowV2(sub) { onForumClick(sub) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumBoardRowV2(
    forum: ForumBoard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClickLabel = onLongClick?.let { "展开或折叠子板块" },
            onLongClick = onLongClick?.let { action ->
                {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                }
            }
        ).padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) { Box(contentAlignment = Alignment.Center) { Text(forum.name.take(1), fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(forum.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (forum.description.isNotBlank()) Text(
                forum.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (forum.todayPostCount > 0) SmallTagV2("今日 ${forum.todayPostCount}")
    }
}

@Composable
private fun ForumSubBoardRowV2(forum: ForumBoard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 38.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) { Box(contentAlignment = Alignment.Center) { Text(forum.name.take(1), fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(forum.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        }
        if (forum.todayPostCount > 0) SmallTagV2("今日 ${forum.todayPostCount}")
    }
}

@Composable
private fun ForumThreadListV2(
    state: ForumState,
    onThreadClick: (ForumThread) -> Unit,
    onThreadLongClick: (ForumThread) -> Unit,
    onGoToPage: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    blocklistEnabled: Boolean,
    blockedItems: List<ForumBlockedItem>,
    onSortChange: (ForumSort) -> Unit,
    onFilterChange: (String?) -> Unit,
    onForumClick: (ForumBoard) -> Unit
) {
    val visible = remember(state.threads, blocklistEnabled, blockedItems) {
        state.threads.filterNot {
            isBlocked(ForumBlockedItem.TYPE_THREAD, it.id, it.authorId, blocklistEnabled, blockedItems)
        }
    }
    var stickyExpanded by remember(state.selectedForum?.id) {
        mutableStateOf(STICKY_THREADS_INITIAL_EXPANDED)
    }
    val groupedThreads = remember(visible) { groupForumThreads(visible) }
    val sticky = groupedThreads.sticky
    val regular = groupedThreads.regular
    val forum = state.selectedForum
    val availableTypes = state.availableTypes
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        forum?.let { f ->
            f.headImageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                item("forum-head-image", contentType = "forum-head-image") {
                    ForumHeadImageV2(forum = f, imageUrl = imageUrl)
                }
            }
            if (f.subforums.isNotEmpty()) {
                item("subforums") {
                    Column {
                        Text("子板块", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(f.subforums, key = { "subforum-${it.id}" }) { sub ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onForumClick(sub) },
                                    label = {
                                        Text(if (sub.threadCount > 0) "${sub.name} · ${sub.threadCount} 主题" else sub.name)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item("sort-bar") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ForumSort.entries, key = { "sort-${it.name}" }) { sort ->
                        FilterChip(
                            selected = state.sortBy == sort,
                            onClick = { onSortChange(sort) },
                            label = { Text(sort.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
            if (availableTypes.isNotEmpty()) {
                item("type-filter") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item("type-all") {
                            FilterChip(
                                selected = state.filterType == null,
                                onClick = { onFilterChange(null) },
                                label = { Text("全部") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        items(availableTypes.entries.toList(), key = { "type-${it.key}" }) { (typeId, typeName) ->
                            FilterChip(
                                selected = state.filterType == typeId,
                                onClick = { onFilterChange(typeId) },
                                label = { Text(typeName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
        if (sticky.isNotEmpty()) item("sticky-header", contentType = "sticky-header") {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { stickyExpanded = !stickyExpanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "置顶帖子 (${sticky.size})",
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        if (stickyExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (stickyExpanded) "收起置顶帖子" else "展开置顶帖子"
                    )
                }
            }
        }
        if (stickyExpanded) items(sticky, key = { "sticky-${it.id}" }) { thread ->
            ForumThreadCardV2(thread, { onThreadClick(thread) }, { onThreadLongClick(thread) })
        }
        items(
            regular,
            key = { thread -> "thread-${thread.id}" },
            contentType = { "regular-thread" }
        ) { thread ->
            ForumThreadCardV2(thread, { onThreadClick(thread) }, { onThreadLongClick(thread) })
        }
        if (visible.isNotEmpty()) {
            item("forum-pagination", contentType = "forum-pagination") {
                ForumPaginationV2(
                    page = state.page,
                    totalPages = state.totalPages,
                    enabled = !state.isLoading,
                    onGoToPage = onGoToPage
                )
            }
        }
        if (visible.isEmpty() && !state.isLoading) item("empty-threads") {
            EmptyForumMessageV2(
                if (state.threads.isEmpty()) "这个板块暂时没有主题" else "主题已按屏蔽设置隐藏"
            )
        }
        state.error?.let { item("page-error") { InlineErrorV2(it) } }
    }
}

@Composable
private fun ForumHeadImageV2(forum: ForumBoard, imageUrl: String) {
    val context = LocalContext.current
    val request = remember(context, forum.id, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .addHeader("User-Agent", RequestConfig.UA)
            .addHeader("Referer", "https://bbs.yamibo.com/")
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "${forum.name}版块头图",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 180.dp)
            .clip(RoundedCornerShape(12.dp)),
        loading = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        error = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumPaginationV2(
    page: Int,
    totalPages: Int,
    enabled: Boolean,
    onGoToPage: (Int) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable(page) { mutableStateOf(page.toString()) }
    val requestedPage = pageInput.toIntOrNull()
    val validPage = requestedPage?.takeIf { it in 1..totalPages.coerceAtLeast(1) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            enabled = enabled && page > 1,
            onClick = { onGoToPage(page - 1) },
            modifier = Modifier.padding(horizontal = 4.dp),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = forumPaginationButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(forumText(19978, 19968, 39029))
        }
        Button(
            enabled = enabled,
            onClick = {
                pageInput = page.toString()
                showPicker = true
            },
            modifier = Modifier.padding(horizontal = 4.dp),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = forumPaginationButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) { Text(forumText(31532) + page + forumText(39029)) }
        Button(
            enabled = enabled && page < totalPages,
            onClick = { onGoToPage(page + 1) },
            modifier = Modifier.padding(horizontal = 4.dp),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = forumPaginationButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(forumText(19979, 19968, 39029))
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(forumText(36339, 36716, 39029, 30721)) },
            text = {
                TextField(
                    value = pageInput,
                    onValueChange = { value -> pageInput = value.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(forumText(39029, 30721) + " (1-${totalPages.coerceAtLeast(1)})")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = validPage != null,
                    onClick = {
                        validPage?.let(onGoToPage)
                        showPicker = false
                    }
                ) { Text(forumText(36339, 36716)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(forumText(21462, 28040)) }
            }
        )
    }
}
@Composable
private fun ForumThreadCardV2(thread: ForumThread, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthorAvatarV2(
                    name = thread.authorName,
                    avatarUrl = thread.avatarUrl,
                    size = 36
                )
                Spacer(Modifier.width(9.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        thread.authorName.ifBlank { "匿名" },
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        " · ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        thread.createdAt,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (thread.isSticky || thread.typeName?.isNotBlank() == true) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (thread.isSticky) SmallTagV2("置顶")
                        thread.typeName?.takeIf(String::isNotBlank)?.let { SmallTagV2(it) }
                    }
                }
            }
            Text(
                thread.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "查看 ${thread.viewCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "回复 ${thread.replyCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (thread.lastPoster.isBlank()) "暂无回复" else "最后回复 ${thread.lastPoster}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun forumPaginationButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.primary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeThreadPageV2(
    threadId: String,
    targetPostId: String = "",
    forumName: String? = null,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenManga: (String) -> Unit = {},
    onOpenReader: (String) -> Unit = {},
    bottomNavBarVM: BottomNavBarVM,
    vm: ForumThreadVM = viewModel(
        key = "NativeThreadPageV2-$threadId-$targetPostId",
        factory = ForumThreadVM.factory(threadId, targetPostId)
    )
) {
    val state by vm.uiState.collectAsState()
    val cookie by GlobalData.cookieFlow.collectAsState(initial = "")
    val enabled by ForumBlocklistManager.enabled.collectAsState()
    val blockedItems by ForumBlocklistManager.items.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val favoriteVM: FavoriteVM = viewModel(
        factory = ViewModelFactory(context.applicationContext)
    )
    val listState = rememberLazyListState()
    ObserveBottomBarLazyListScroll(listState, bottomNavBarVM)
    val pullState = rememberPullToRefreshState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val componentColors = yamiboComponentColors()
    var menuOpen by remember { mutableStateOf(false) }
    var blockTarget by remember { mutableStateOf<NativeForumBlockTarget?>(null) }
    var ratingDialog by remember { mutableStateOf<ForumPost?>(null) }
    var commentDialog by remember { mutableStateOf<ForumPost?>(null) }
    var rateDialog by remember { mutableStateOf<ForumPost?>(null) }
    var replyRequest by remember { mutableStateOf<ForumReplyRequest?>(null) }
    var imagePreview by remember { mutableStateOf<ForumImagePreview?>(null) }
    val isLoggedIn = GlobalData.currentUid.isNotBlank()
    val forumId = state.thread?.forumId.orEmpty()
    val posts = state.posts.mapNotNull { post ->
        if (isBlocked(ForumBlockedItem.TYPE_POST, post.id, post.author.id, enabled, blockedItems)) {
            null
        } else {
            val comments = post.comments.filterNot {
                isBlocked(ForumBlockedItem.TYPE_USER, it.authorUid.orEmpty(), it.authorUid, enabled, blockedItems)
            }
            val ratingSummary = post.ratingSummary?.let { summary ->
                val ratings = summary.ratings.filterNot {
                    isBlocked(ForumBlockedItem.TYPE_USER, it.authorUid.orEmpty(), it.authorUid, enabled, blockedItems)
                }
                if (ratings.isEmpty()) null else summary.copy(ratings = ratings)
            }
            post.copy(comments = comments, ratingSummary = ratingSummary)
        }
    }
    val displayPosts = if (state.reverseOrder) posts.asReversed() else posts
    var targetPostHandled by rememberSaveable(threadId, targetPostId) {
        mutableStateOf(targetPostId.isBlank())
    }

    DisposableEffect(lifecycleOwner, state.verificationUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.verificationUrl != null) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.page, displayPosts.map(ForumPost::id), targetPostId) {
        if (state.posts.isNotEmpty()) {
            // 等待列表完成新页布局；首次带 pid 进入时定位该楼，之后正常翻页回到顶部。
            withFrameNanos { }
            val postIndex = if (!targetPostHandled) {
                displayPosts.indexOfFirst { it.id == targetPostId }
            } else {
                -1
            }
            if (postIndex >= 0) {
                val summaryOffset = if (displayPosts.none(ForumPost::isOriginalPost) && state.thread != null) 1 else 0
                listState.scrollToItem(postIndex + summaryOffset)
                targetPostHandled = true
            } else {
                listState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.scrollToTopEvent.collect { index ->
            if (index == 2) listState.animateScrollToItem(0)
        }
    }

    // 点击帖子图片：漫画帖直接唤起原生漫画阅读器，普通帖打开原生图片预览
    fun openPostImage(post: ForumPost, url: String) {
        val urls = buildList {
            post.blocks.forEach { block ->
                (block as? ForumPostBlock.Image)?.let { add(it.url) }
            }
            post.attachments.filter { it.isImage }.forEach { add(it.url) }
        }.distinct()
        if (urls.isEmpty()) return
        val index = urls.indexOf(url).coerceAtLeast(0)
        val thread = state.thread
        val isMangaThread = thread != null && (
            thread.forumId in MANGA_FORUM_IDS ||
                thread.forumName.contains("漫画") ||
                thread.forumName.contains("图源")
            )
        if (isMangaThread) {
            MangaImagePipeline.handoffPrefetch(
                context = context.applicationContext,
                urls = urls,
                clickedIndex = index,
                cookie = cookie
            )
            GlobalData.tempMangaUrls = urls
            GlobalData.tempMangaIndex = index
            GlobalData.tempHtml = state.threadHtml
            GlobalData.tempTitle = thread.subject
            onOpenManga(threadId)
        } else {
            imagePreview = ForumImagePreview(
                urls = urls,
                index = index,
                title = thread?.subject.orEmpty()
            )
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(
                bottom = if (bottomNavBarVM.bottomBarScrollSuppressed) navBottom else navBottom + 50.dp
            )
    ) {
        ForumTopBarV2(
            title = state.thread?.forumName
                ?.takeIf(String::isNotBlank)
                ?.takeUnless { it.startsWith("[") && it.endsWith("]") }
                ?: forumName?.takeIf(String::isNotBlank)
                ?: "论坛",
            headerColor = componentColors.topBarContainer,
            contentColor = componentColors.topBarContent,
            onBack = onBack        ) {
            if (false) IconButton(onClick = { onOpenWeb(ForumActionUrls.search) }) {
                Icon(Icons.Default.Search, "搜索论坛")
            }
            val threadUrl = ForumActionUrls.thread(threadId)
            val favorites by FavoriteUtil.getFavoriteFlow().collectAsState(initial = emptyList())
            val threadFavoriteUrl = remember(threadUrl) { FavoriteUtil.normalizeUrl(threadUrl) }
            val isFavorited = favorites.any { it.url == threadFavoriteUrl }
            // 小说帖：在收藏按钮左侧展示阅读入口，跳转原生小说阅读器
            if (ReaderModeDetector.isNovelForum(state.thread?.forumName)) {
                IconButton(onClick = {
                    val readerUrl =
                        "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$threadId&mobile=no"
                    if (threadId.isBlank()) {
                        YamiboToast.show(message = "帖子信息加载中，请稍后再试")
                    } else {
                        onOpenReader(readerUrl)
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "阅读模式",
                        tint = componentColors.topBarContent
                    )
                }
            }
            IconButton(onClick = {
                favoriteVM.toggleFavorite(
                    threadUrl,
                    state.thread?.subject.orEmpty(),
                    threadId
                ) { message -> YamiboToast.show(message = message) }
            }) {
                Icon(
                    imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorited) "取消收藏" else "收藏本主题",
                    // 与日志详情页收藏按钮一致：始终使用顶栏内容色全亮显示。
                    tint = componentColors.topBarContent
                )
            }
            IconButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("yamibo_link", threadUrl)
                )
                YamiboToast.show(message = "已复制链接")
            }) {
                Icon(Icons.Filled.Share, "复制链接")
            }
            Box {
                IconButton({ menuOpen = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.onlyOriginalPoster) "查看全部" else "只看楼主") },
                        onClick = { menuOpen = false; vm.toggleOriginalPosterOnly() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.reverseOrder) "正序浏览" else "倒序浏览") },
                        onClick = { menuOpen = false; vm.toggleReverseOrder() }
                    )
                    if (false) DropdownMenuItem(
                        text = { Text("返回首页") },
                        onClick = {
                            menuOpen = false
                            favoriteVM.toggleFavorite(
                                ForumActionUrls.thread(threadId),
                                state.thread?.subject.orEmpty(),
                                threadId
                            ) { message -> YamiboToast.show(message = message) }
                        }
                    )
                }
            }
        }
        val refreshingWithContent = state.isLoading && state.posts.isNotEmpty()
        PullToRefreshBox(
            isRefreshing = refreshingWithContent,
            onRefresh = vm::refresh,
            state = pullState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshingWithContent,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            when {
                state.isLoading && state.posts.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.posts.isEmpty() -> ForumErrorV2(
                    message = state.error.orEmpty(),
                    onRetry = vm::refresh,
                    onVerify = state.verificationUrl?.let { url ->
                        { onOpenWeb(url) }
                    }
                )
                else -> ThreadBodyV2(
                    state,
                    displayPosts,
                    cookie,
                    listState,
                    onOpenLink,
                    onOpenWeb,
                    vm::goToPage,
                    { post -> ratingDialog = post },
                    { poll, optionIds ->
                        vm.votePoll(poll, optionIds) { message ->
                            YamiboToast.show(message = message)
                        }
                    },
                    { post ->
                        if (isLoggedIn) commentDialog = post
                        else YamiboToast.show(message = "请先登录后再使用点评功能")
                    },
                    { post ->
                        if (isLoggedIn) rateDialog = post
                        else YamiboToast.show(message = "请先登录后再使用评分功能")
                    },
                    { post ->
                        if (isLoggedIn) replyRequest = ForumReplyRequest(post)
                        else YamiboToast.show(message = "请先登录后再使用回复功能")
                    },
                    { post ->
                        val fid = state.thread?.forumId.orEmpty()
                        if (fid.isBlank()) {
                            YamiboToast.show(message = "板块信息缺失，请刷新页面")
                        } else {
                            onOpenWeb(ForumActionUrls.edit(post.threadId, fid, post.id))
                        }
                    },
                    { post ->
                        val threadUrl = ForumActionUrls.thread(post.threadId)
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("yamibo_link", threadUrl)
                        )
                        YamiboToast.show(message = "已复制链接")
                    },
                    { post ->
                        val fid = state.thread?.forumId.orEmpty()
                        if (fid.isBlank()) {
                            YamiboToast.show(message = "板块信息缺失，请刷新页面")
                        } else {
                            onOpenWeb(ForumActionUrls.topicAdmin(fid, post.threadId))
                        }
                    },
                    { post, url -> openPostImage(post, url) }
                ) { post ->
                    if (post.author.id != GlobalData.currentUid) blockTarget = NativeForumBlockTarget(
                        ForumBlockedItem.TYPE_POST,
                        post.id,
                        "${post.floor} 楼",
                        post.author.id.orEmpty(),
                        post.author.name,
                        state.thread?.subject.orEmpty()
                    )
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
            Button(
                onClick = {
                    if (isLoggedIn) replyRequest = ForumReplyRequest(null)
                    else YamiboToast.show(message = "请先登录后再使用回复功能")
                },
                enabled = forumId.isNotBlank() && state.thread?.isClosed != true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (state.thread?.isClosed == true) "主题已关闭" else "回复主题") }
        }
    }
    blockTarget?.let { NativeBlockMenuV2(it) { blockTarget = null } }
    ratingDialog?.let { post ->
        post.ratingSummary?.let { summary ->
            ForumRatingsDialog(
                summary = summary,
                loadAll = { onResult -> vm.loadAllRatings(post, onResult) },
                onDismiss = { ratingDialog = null }
            )
        }
    }
    commentDialog?.let { post ->
        ForumCommentDialog(
            post = post,
            onDismiss = { commentDialog = null },
            onSubmit = { message ->
                vm.submitComment(post, message) { result -> YamiboToast.show(message = result) }
            }
        )
    }
    rateDialog?.let { post ->
        ForumRateDialog(
            post = post,
            loadPopout = { onResult -> vm.loadRatePopout(post, onResult) },
            onDismiss = { rateDialog = null },
            onSubmit = { score, reason, formHash ->
                vm.submitRate(post, score, reason, formHash) { result ->
                    YamiboToast.show(message = result)
                }
            }
        )
    }
    replyRequest?.let { request ->
        ForumReplyDialog(
            quotePost = request.quotePost,
            onDismiss = { replyRequest = null },
            onSubmit = { message ->
                vm.submitReply(message, request.quotePost) { result ->
                    YamiboToast.show(message = result)
                }
            }
        )
    }
    imagePreview?.let { preview ->
        ForumImagePreviewDialog(preview, cookie) { imagePreview = null }
    }
}

@Composable
private fun ThreadBodyV2(
    state: ForumThreadState,
    posts: List<ForumPost>,
    cookie: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onGoToPage: (Int) -> Unit,
    onOpenRatings: (ForumPost) -> Unit,
    onVotePoll: (ForumPoll, List<String>) -> Unit,
    onComment: (ForumPost) -> Unit,
    onRate: (ForumPost) -> Unit,
    onReply: (ForumPost?) -> Unit,
    onEdit: (ForumPost) -> Unit,
    onShare: (ForumPost) -> Unit,
    onManage: (ForumPost) -> Unit,
    onOpenImage: (ForumPost, String) -> Unit,
    onLongClick: (ForumPost) -> Unit
) {
    val hasOriginalPost = posts.any { it.isOriginalPost }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasOriginalPost) {
            state.thread?.let { thread ->
                item("thread-summary") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        ThreadSummaryV2(thread, state.onlyOriginalPoster)
                    }
                }
            }
        }
        items(posts, key = { post -> post.id }) { post ->
            ForumPostCardV2(
                post = post,
                thread = state.thread.takeIf { post.isOriginalPost },
                isThreadAuthor = state.thread?.author?.id == GlobalData.currentUid,
                onlyOriginalPoster = state.onlyOriginalPoster,
                cookie = cookie,
                onOpenLink = onOpenLink,
                onOpenImage = { url -> onOpenImage(post, url) },
                onLongClick = { onLongClick(post) },
                onReply = { onReply(post) },
                onEdit = { onEdit(post) },
                onComment = { onComment(post) },
                onRate = { onRate(post) },
                onShare = { onShare(post) },
                onManage = { onManage(post) },
                onOpenRatings = onOpenRatings,
                onVotePoll = onVotePoll
            )
        }
        if (posts.isNotEmpty()) {
            item("thread-pagination", contentType = "thread-pagination") {
                ForumPaginationV2(
                    page = state.page,
                    totalPages = state.totalPages,
                    enabled = !state.isLoading,
                    onGoToPage = onGoToPage
                )
            }
        }
        state.error?.let { item("thread-page-error") { InlineErrorV2(it) } }
    }
}

@Composable
private fun ThreadSummaryV2(
    thread: ForumThreadDetail,
    onlyOriginalPoster: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = thread.subject,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看 ${thread.viewCount} · 回复 ${thread.replyCount}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall
            )
            if (thread.lastPoster.isNotBlank()) {
                Text(
                    text = "最后回复 ${thread.lastPoster}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
        if (onlyOriginalPoster) SmallTagV2("只看楼主")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumPostCardV2(
    post: ForumPost,
    thread: ForumThreadDetail?,
    isThreadAuthor: Boolean,
    onlyOriginalPoster: Boolean,
    cookie: String,
    onOpenLink: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onComment: () -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onManage: () -> Unit,
    onOpenRatings: (ForumPost) -> Unit,
    onVotePoll: (ForumPoll, List<String>) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (post.isOriginalPost) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column {
            thread?.let { ThreadSummaryV2(it, onlyOriginalPoster) }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuthorAvatarV2(post.author.name, post.author.avatarUrl, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(post.author.name, fontWeight = FontWeight.SemiBold)
                        Text(post.createdAt, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    if (post.author.id == GlobalData.currentUid) {
                        SmallActionText("编辑", onEdit)
                    }
                    SmallTagV2(if (post.isOriginalPost) "楼主" else "${post.floor} 楼")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    ForumPostContentV2(post, cookie, onOpenLink, onOpenImage)
                }
                post.poll?.let { poll ->
                    Spacer(Modifier.height(6.dp))
                    ForumPollV2(poll) { optionIds -> onVotePoll(poll, optionIds) }
                }
 post.ratingSummary?.let { summary ->
                    Spacer(Modifier.height(6.dp))
                    ForumPostRatingV2(summary) { onOpenRatings(post) }
                }
                if (post.comments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ForumCommentsV2(post.comments)
                }
                if (post.ratingSummary != null || post.comments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    post.editedAt?.let { editedAt ->
                        Text(
                            text = "修改于 $editedAt",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (post.author.id != GlobalData.currentUid) SmallActionText("评分", onRate)
                    SmallActionText("点评", onComment)
                    SmallActionText("回复", onReply)
                    if (!post.isOriginalPost) SmallActionText("分享", onShare)
                    if (isThreadAuthor && !post.isOriginalPost) SmallActionText("管理", onManage)
                }
            }
        }
    }
}

@Composable
private fun PollOptionIndicator(selected: Boolean, multiple: Boolean) {
    val shape = if (multiple) RoundedCornerShape(6.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .then(
                if (!selected) Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PollOptionRow(
    option: ForumPollOption,
    selected: Boolean,
    canVote: Boolean,
    showVoteStats: Boolean,
    multiple: Boolean,
    onClick: () -> Unit
) {
    val percent = (option.percent ?: 0f).coerceIn(0f, 100f)
    val containerColor = if (canVote && selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (canVote && selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canVote) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = if (canVote && !selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canVote) {
                    PollOptionIndicator(selected = selected, multiple = multiple)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = option.text,
                    modifier = Modifier.weight(1f),
                    color = textColor
                )
                if (showVoteStats) {
                    Spacer(Modifier.width(8.dp))
                    val percentText = if (percent % 1f == 0f) {
                        percent.toInt().toString()
                    } else {
                        percent.toString()
                    }
                    Text(
                        text = percentText + "% (" + (option.voteCount ?: 0) + "票)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (showVoteStats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent / 100f)
                            .height(5.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumPollV2(
    poll: ForumPoll,
    onVote: ((List<String>) -> Unit)? = null
) {
    var selectedOptionIds by remember(poll) { mutableStateOf(emptySet<String>()) }
    val showVoteStats = poll.options.any { it.percent != null || it.voteCount != null }
    val canVote = !poll.hasVoted &&
        poll.formHash.isNullOrBlank().not() &&
        poll.actionUrl.isNullOrBlank().not() &&
        poll.options.all { !it.id.isNullOrBlank() } &&
        onVote != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "投票",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = poll.typeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                poll.participantCount?.let { count ->
                    Text(
                        text = "共有 " + count + " 人参与",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (poll.participantCount != null && poll.remainingText != null) {
                    Spacer(Modifier.width(12.dp))
                }
                poll.remainingText?.let { remaining ->
                    Text(
                        text = remaining,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            poll.options.forEach { option ->
                val optionId = option.id
                PollOptionRow(
                    option = option,
                    selected = optionId != null && selectedOptionIds.contains(optionId),
                    canVote = canVote,
                    showVoteStats = showVoteStats,
                    multiple = poll.isMultipleChoice,
                    onClick = {
                        optionId?.let { id ->
                            selectedOptionIds = if (poll.isMultipleChoice) {
                                if (selectedOptionIds.contains(id)) {
                                    selectedOptionIds - id
                                } else {
                                    selectedOptionIds + id
                                }
                            } else {
                                setOf(id)
                            }
                        }
                    }
                )
            }
            if (canVote) {
                Button(
                    onClick = { onVote.invoke(selectedOptionIds.toList()) },
                    enabled = selectedOptionIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("提交投票")
                }
            } else if (!showVoteStats) {
                Text(
                    text = "投票后显示结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            poll.statusText?.let { status ->
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ForumPostRatingV2(
    summary: ForumPostRatingSummary,
    onOpenRatings: (ForumPostRatingSummary) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpenRatings(summary) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(forumText(35780, 20998), fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.participantText.isNotBlank()) {
                    Text(
                        summary.participantText,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (summary.scoreText.isNotBlank()) {
                    Text(
                        summary.scoreText,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    forumText(29702, 30001),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (summary.ratings.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                summary.ratings.take(5).forEach { rating ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            rating.userName.ifBlank { forumText(21311, 21517) },
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            rating.score,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            rating.reason,
                            Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
if (summary.ratings.size > 5) {
                    Text(
                        text = "查看全部评分",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRatings(summary) }
                            .padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * 点评行内展示：默认 5 条，超出后点击“查看更多点评”直接在卡片内展开剩余，
 * 再次点击收起。展开/收起带 animateContentSize 平滑过渡；展开前展示短暂
 * 加载状态。布局贴近现有卡片风格，不依赖弹窗。
 */
@Composable
private fun ForumCommentsV2(comments: List<ForumComment>) {
    var expanded by remember(comments) { mutableStateOf(false) }
    var loadingMore by remember(comments) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val visible = if (expanded || comments.size <= COMMENTS_PREVIEW_LIMIT) {
        comments
    } else {
        comments.take(COMMENTS_PREVIEW_LIMIT)
    }
    val hidden = comments.size - visible.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "点评（${comments.size}）",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (hidden > 0) {
                    Row(
                        modifier = Modifier.clickable(
                            enabled = !loadingMore
                        ) {
                            if (expanded) {
                                expanded = false
                            } else {
                                loadingMore = true
                                scope.launch {
                                    delay(360)
                                    loadingMore = false
                                    expanded = true
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "正在加载更多点评…",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Text(
                                text = if (expanded) "收起" else "查看更多点评",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            visible.forEach { comment ->
                Row(verticalAlignment = Alignment.Top) {
                    AuthorAvatarV2(comment.authorName, comment.authorAvatarUrl, 32)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.authorName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (comment.createdAt.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = comment.createdAt,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = comment.message,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (hidden > 0 && expanded) {
                Text(
                    text = "— 查看全部 ${comments.size} 条点评 —",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private const val COMMENTS_PREVIEW_LIMIT = 5

@Composable
private fun ForumPostContentV2(
    post: ForumPost,
    cookie: String,
    onOpenLink: (String) -> Unit,
    onOpenImage: (String) -> Unit
) {
    if (post.blocks.isEmpty() && post.attachments.isEmpty()) {
        Text("该楼层没有可显示的内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    post.blocks.forEachIndexed { index, block ->
        val previous = post.blocks.getOrNull(index - 1)
        if (previous != null &&
            ((previous is ForumPostBlock.Text && block is ForumPostBlock.Image) ||
                (previous is ForumPostBlock.Image && block is ForumPostBlock.Text))
        ) {
            Spacer(Modifier.height(12.dp))
        }
        when (block) {
            is ForumPostBlock.Text -> ForumPostTextV2(block, onOpenLink)
            is ForumPostBlock.Image -> ForumPostImageV2(block.url, block.description, cookie, onOpenImage)
        }
    }
    val inlineImages = post.blocks.filterIsInstance<ForumPostBlock.Image>().mapTo(hashSetOf(), ForumPostBlock.Image::url)
    if (post.blocks.isNotEmpty() && post.attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
    }
    post.attachments.forEach { attachment ->
        if (attachment.isImage && attachment.url !in inlineImages) {
            ForumPostImageV2(attachment.url, attachment.filename, cookie, onOpenImage)
        } else if (!attachment.isImage) {
            ForumAttachmentRowV2(attachment, onOpenLink)
        }
    }
}

@Composable
private fun NativeBlockMenuV2(target: NativeForumBlockTarget, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        YamiboDialogSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "加入屏蔽名单",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (target.type) {
                        ForumBlockedItem.TYPE_THREAD -> "主题：${target.title}"
                        else -> "楼层：${target.title}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (target.authorName.isNotBlank()) {
                    Text(
                        "用户：${target.authorName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (target.authorUid.isNotBlank() && target.authorUid != GlobalData.currentUid) {
                    BlockActionRowV2(
                        icon = Icons.Default.Person,
                        title = "屏蔽用户",
                        description = "隐藏该用户的主题和楼层",
                        onClick = {
                            ForumBlocklistManager.add(
                                ForumBlockedItem.TYPE_USER,
                                target.authorUid,
                                target.authorName,
                                target.authorUid,
                                target.authorName
                            )
                            onDismiss()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                BlockActionRowV2(
                    icon = Icons.Default.Block,
                    title = if (target.type == ForumBlockedItem.TYPE_THREAD) "屏蔽主题" else "屏蔽楼层",
                    description = "只隐藏当前${if (target.type == ForumBlockedItem.TYPE_THREAD) "主题" else "楼层"}",
                    onClick = {
                        ForumBlocklistManager.add(
                            target.type,
                            target.id,
                            target.title,
                            target.authorUid,
                            target.authorName,
                            target.threadTitle
                        )
                        onDismiss()
                    }
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun BlockActionRowV2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun AuthorAvatarV2(name: String, avatarUrl: String?, size: Int) {
    Surface(Modifier.size(size.dp), CircleShape, MaterialTheme.colorScheme.surfaceVariant) {
        if (!avatarUrl.isNullOrBlank()) AsyncImage(
            model = avatarUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.clip(CircleShape)
        ) else Box(contentAlignment = Alignment.Center) {
            Text(name.take(1).ifBlank { "?" }, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmallTagV2(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(LanguageModeUtil.displayText(text), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallActionText(text: String, onClick: () -> Unit) {
    Text(
        LanguageModeUtil.displayText(text),
        modifier = Modifier.clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyForumMessageV2(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(LanguageModeUtil.displayText(message), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LoadingMoreV2() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun InlineErrorV2(message: String) {
    Text(LanguageModeUtil.displayText(message), Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
}

@Composable
private fun ForumErrorV2(
    message: String,
    onRetry: () -> Unit,
    onVerify: (() -> Unit)? = null
) {
    YamiboLoadError(
        title = "论坛暂时无法打开",
        message = message,
        onRetry = onVerify ?: onRetry,
        buttonText = if (onVerify == null) "刷新页面" else "打开网页验证"
    )
}

@Suppress("DEPRECATION")
@Composable
private fun ForumPostTextV2(block: ForumPostBlock.Text, onOpenLink: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(block, linkColor) { buildAnnotatedString {
        block.parts.forEach { part ->
            if (part.url == null) append(part.text) else {
                pushStringAnnotation("URL", part.url)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(part.text)
                pop(); pop()
            }
        }
    } }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 25.sp),
        onClick = { offset -> annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let(onOpenLink) }
    )
}

@Composable
private fun ForumPostImageV2(
    url: String,
    description: String,
    cookie: String,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val request = remember(context, url, cookie) {
        ImageRequest.Builder(context).data(url).crossfade(true)
            .addHeader("User-Agent", RequestConfig.UA)
            .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
            .bitmapConfig(Bitmap.Config.RGB_565).build()
    }
    Surface(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(10.dp),
        MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = description.ifBlank { "帖子图片" },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenImage(url) },
            loading = {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null)
                    Text("图片加载失败", style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
}

@Composable
private fun ForumAttachmentRowV2(attachment: ForumPostAttachment, onOpenLink: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpenLink(attachment.url) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(attachment.filename, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
@Composable
private fun ForumRatingsDialog(
    summary: ForumPostRatingSummary,
    loadAll: ((Result<List<ForumPostRating>>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val blocklistEnabled by ForumBlocklistManager.enabled.collectAsState()
    val blockedItems by ForumBlocklistManager.items.collectAsState()
    var ratings by remember { mutableStateOf<List<ForumPostRating>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadAll { result ->
            result
                .onSuccess { fullRatings ->
                    val visibleRatings = fullRatings.filterNot {
                        isBlocked(
                            ForumBlockedItem.TYPE_USER,
                            it.authorUid.orEmpty(),
                            it.authorUid,
                            blocklistEnabled,
                            blockedItems
                        )
                    }
                    // 完整评分接口的部分模板不返回理由，使用帖子预览中已有的理由补齐；
                    // 完整接口有值时优先保留服务端返回值。
                    ratings = visibleRatings.map { rating ->
                        if (rating.reason.isNotBlank()) {
                            rating
                        } else {
                            rating.copy(
                                reason = summary.ratings
                                    .firstOrNull { preview -> preview.userName == rating.userName }
                                    ?.reason
                                    .orEmpty()
                            )
                        }
                    }
                }
                .onFailure { loadError = it.message ?: "评分明细加载失败" }
        }
    }

    val displayRatings = (ratings ?: summary.ratings).filterNot {
        isBlocked(
            ForumBlockedItem.TYPE_USER,
            it.authorUid.orEmpty(),
            it.authorUid,
            blocklistEnabled,
            blockedItems
        )
    }
    val showLoading = ratings == null && loadError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "查看全部评分",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "评分",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "用户名",
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "时间",
                        modifier = Modifier.weight(1.25f),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when {
                    showLoading -> {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    displayRatings.isEmpty() -> {
                        Text(
                            text = loadError ?: "暂无评分明细",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                            color = if (loadError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        displayRatings.forEach { rating ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rating.score.ifBlank { "—" },
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = rating.userName.ifBlank { "未知用户" },
                                    modifier = Modifier.weight(1.2f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = rating.createdAt.orEmpty().ifBlank { "—" },
                                    modifier = Modifier.weight(1.25f),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (rating.reason.isNotBlank()) {
                                Text(
                                    text = rating.reason,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                if (summary.scoreText.isNotBlank() || summary.participantText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (summary.scoreText.isNotBlank()) {
                                    "总计: " + summary.scoreText
                                } else {
                                    summary.participantText
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (
                                summary.scoreText.isNotBlank() &&
                                summary.participantText.isNotBlank()
                            ) {
                                Text(
                                    text = summary.participantText,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ForumCommentDialog(
    post: ForumPost,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    YamiboTextEditorDialog(
        title = "点评 #" + post.floor + " 楼",
        subtitle = "点评内容会显示在该楼层下方",
        placeholder = "请输入点评内容",
        confirmLabel = "发表点评",
        minLines = 4,
        maxLines = 8,
        showForumSmilies = true,
        onDismiss = onDismiss,
        onConfirm = { message ->
            onSubmit(message)
            onDismiss()
        }
    )
}

/** 漫画相关板块 ID：30=中文百合漫画区，37=百合漫画图源区 */
private val MANGA_FORUM_IDS = setOf("30", "37")

/** Discuz 回复最短字数（与论坛 minpostsize 默认一致）。 */
private const val MIN_REPLY_CHARS = 21

/** 回复请求：quotePost 为 null 表示直接回复主题，非 null 表示引用该楼回复 */
private data class ForumReplyRequest(val quotePost: ForumPost?)

/** 原生图片预览参数 */
private data class ForumImagePreview(
    val urls: List<String>,
    val index: Int,
    val title: String
)

@Composable
private fun ForumRateDialog(
    post: ForumPost,
    loadPopout: ((Result<ForumRatePopout>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (score: Int, reason: String, formHash: String?) -> Unit
) {
    var popout by remember { mutableStateOf<ForumRatePopout?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedScore by remember { mutableStateOf<Int?>(null) }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(post.id) {
        loadPopout { result ->
            result
                .onSuccess { popout = it }
                .onFailure { loadError = it.message ?: "评分信息加载失败" }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        YamiboDialogSurface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.82f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "评分 #${post.floor} 楼",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                when {
                    popout != null -> {
                        val scores = remember(popout) {
                            popout?.availableScores.orEmpty().sortedBy { it.score }
                        }
                        if (scores.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                scores.forEach { option ->
                                    FilterChip(
                                        selected = selectedScore == option.score,
                                        onClick = {
                                            selectedScore = if (selectedScore == option.score) null else option.score
                                        },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                        }
                        val defaultReasons = popout?.defaultReasons.orEmpty()
                        if (defaultReasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                defaultReasons.forEach { item ->
                                    AssistChip(
                                        onClick = { reason = item },
                                        label = { Text(item) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }
                        TextField(
                            value = reason,
                            onValueChange = { reason = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("评分理由（可选）") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    loadError != null -> {
                        Text(
                            text = loadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Box(
                            Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val score = selectedScore ?: return@TextButton
                            onSubmit(score, reason, popout?.formHash)
                            onDismiss()
                        },
                        enabled = popout != null && selectedScore != null
                    ) { Text("提交评分") }
                }
            }
        }
    }
}

@Composable
private fun ForumReplyDialog(
    quotePost: ForumPost?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    YamiboTextEditorDialog(
        title = if (quotePost == null) {
            "回复主题"
        } else {
            "回复 #" + quotePost.floor + " 楼"
        },
        subtitle = if (quotePost == null) {
            null
        } else {
            "将以引用该楼层的形式发表回复"
        },
        placeholder = "请输入回复内容",
        confirmLabel = "发表回复",
        minimumLength = MIN_REPLY_CHARS,
        minLines = 5,
        maxLines = 12,
        showForumSmilies = true,
        onDismiss = onDismiss,
        onConfirm = { message ->
            onSubmit(message)
            onDismiss()
        }
    )
}

@Composable
private fun ForumImagePreviewDialog(
    preview: ForumImagePreview,
    cookie: String,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = preview.index,
        pageCount = { preview.urls.size }
    )
    val scope = rememberCoroutineScope()
    var showChrome by remember { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableForumImage(
                    url = preview.urls[page],
                    cookie = cookie,
                    onTap = { showChrome = !showChrome }
                )
            }
            if (showChrome) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preview.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${preview.urls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (preview.urls.size > 1) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一张",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + 1).coerceAtMost(preview.urls.size - 1)
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一张",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableForumImage(
    url: String,
    cookie: String,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        if (newScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        scale = newScale
    }
    val request = remember(context, url, cookie) {
        ImageRequest.Builder(context).data(url).crossfade(true)
            .addHeader("User-Agent", RequestConfig.UA)
            .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = "图片预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .transformable(transformState, canPan = { scale > 1f }),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp, color = Color.White)
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color.White)
                        Text("图片加载失败", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        )
    }
}

private fun forumText(vararg codePoints: Int): String =
    codePoints.map(Int::toChar).toCharArray().concatToString()

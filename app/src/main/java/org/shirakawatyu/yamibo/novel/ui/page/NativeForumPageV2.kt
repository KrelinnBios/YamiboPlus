package org.shirakawatyu.yamibo.novel.ui.page

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.state.ForumSort
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.state.ForumThreadState
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.vm.ForumThreadVM
import org.shirakawatyu.yamibo.novel.ui.vm.ForumVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.util.DarkThemeColors
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlockedItem
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlocklistManager
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteAddUtil
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast

internal object ForumActionUrls {
    private const val ORIGIN = "https://bbs.yamibo.com"
    val search get() = "$ORIGIN/search.php?mod=forum&mobile=2"
    val home get() = "$ORIGIN/forum.php?mobile=2"
    val creditLog get() = "$ORIGIN/home.php?mod=spacecp&ac=credit&op=log&mobile=2"
    val messages get() = "$ORIGIN/home.php?mod=space&do=pm&page=1&mobile=2"
    val reminders get() = "$ORIGIN/home.php?mod=space&do=notice&mobile=2"
    fun messageCenter(hasNewPrompt: Boolean) = if (hasNewPrompt) reminders else messages
    fun board(fid: String) = "$ORIGIN/forum.php?mod=forumdisplay&fid=$fid&mobile=2"
    fun desktopBoard(fid: String) = "$ORIGIN/forum.php?mod=forumdisplay&fid=$fid&mobile=no"
    fun newThread(fid: String) = "$ORIGIN/forum.php?mod=post&action=newthread&fid=$fid&mobile=2"
    fun thread(tid: String) = "$ORIGIN/forum.php?mod=viewthread&tid=$tid&mobile=2"
    fun desktopThread(tid: String) = "$ORIGIN/forum.php?mod=viewthread&tid=$tid&mobile=no"
    fun reply(tid: String, fid: String, pid: String? = null, page: Int = 1) = buildString {
        append(ORIGIN).append("/forum.php?mod=post&action=reply&fid=").append(fid)
        append("&tid=").append(tid)
        if (!pid.isNullOrBlank()) append("&repquote=").append(pid)
        append("&page=").append(page.coerceAtLeast(1)).append("&mobile=2")
    }
    fun comment(tid: String, pid: String) =
        "$ORIGIN/forum.php?mod=misc&action=postreview&tid=$tid&pid=$pid&mobile=2"
    fun rate(tid: String, pid: String) =
        "$ORIGIN/forum.php?mod=misc&action=rate&tid=$tid&pid=$pid&mobile=2"
    fun userSpace(uid: String, section: String) = when (section) {
        "reply" -> "$ORIGIN/home.php?mod=space&uid=$uid&do=thread&view=me&type=reply&mobile=2"
        "thread" -> "$ORIGIN/home.php?mod=space&uid=$uid&do=thread&view=me&mobile=2"
        else -> "$ORIGIN/home.php?mod=space&uid=$uid&do=$section&view=me&mobile=2"
    }
}

private data class NativeForumBlockTarget(
    val type: String,
    val id: String,
    val title: String,
    val authorUid: String = "",
    val authorName: String = ""
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
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    var blockTarget by remember { mutableStateOf<NativeForumBlockTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ForumBlocklistManager.initialize() }
    LaunchedEffect(state.selectedForum?.id) {
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
            .padding(bottom = navBottom + 50.dp)
    ) {
        ForumTopBarV2(
            title = state.selectedForum?.name ?: "论坛",
            forum = state.selectedForum,
            headerColor = headerColor,
            contentColor = headerContent,
            onBack = if (state.selectedForum == null) null else forumVM::showForumIndex
        ) {
            IconButton(onClick = { onOpenWeb(ForumActionUrls.search) }) {
                Icon(Icons.Default.Search, "搜索论坛")
            }
            state.selectedForum?.let { forum ->
                IconButton(onClick = { onOpenWeb(ForumActionUrls.newThread(forum.id)) }) {
                    Icon(Icons.Default.Add, "发表主题")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("收藏本版") },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val success = FavoriteAddUtil.addForumFavorite(forum.id)
                                    YamiboToast.show(message = if (success) "已收藏本版" else "收藏失败，请先登录")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("电脑版") },
                            onClick = { menuOpen = false; onOpenWeb(ForumActionUrls.desktopBoard(forum.id)) }
                        )
                    }
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
    headerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(color = headerColor, contentColor = contentColor) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .height(if (forum == null) 56.dp else 74.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack == null) Spacer(Modifier.width(48.dp)) else IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, forumText(36820, 22238))
            }
            if (forum == null) {
                Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Column(
                        modifier = Modifier.width(76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(forumText(20027, 39064) + forum.threadCount, color = contentColor, fontSize = 13.sp, maxLines = 1)
                        Text(forumText(20170, 26085) + forum.todayPostCount, color = contentColor, fontSize = 13.sp, maxLines = 1)
                    }
                    // 相邻搜索/发布图标的可见间距为 24dp；排名两侧保持同样留白。
                    Spacer(Modifier.width(24.dp))
                    Column(
                        modifier = Modifier.width(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(forumText(25490, 21517), color = contentColor, fontSize = 12.sp)
                        Text(forum.rank?.toString() ?: "--", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    // 搜索 IconButton 自带 12dp 左内边距，这里再补 12dp，合计同为 24dp。
                    Spacer(Modifier.width(12.dp))
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
    LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
        state.banners.firstOrNull()?.let { banner ->
            item("forum-home-banner", contentType = "banner") {
                ForumHomeBannerV2(banner, onBannerClick)
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
            .fillMaxWidth()
            .aspectRatio(2.63f)
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
    forumName: String? = null,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    bottomNavBarVM: BottomNavBarVM,
    vm: ForumThreadVM = viewModel(key = "NativeThreadPageV2-$threadId", factory = ForumThreadVM.factory(threadId))
) {
    val state by vm.uiState.collectAsState()
    val cookie by GlobalData.cookieFlow.collectAsState(initial = "")
    val enabled by ForumBlocklistManager.enabled.collectAsState()
    val blockedItems by ForumBlocklistManager.items.collectAsState()
    val context = LocalContext.current
    val favoriteVM: FavoriteVM = viewModel(
        factory = ViewModelFactory(context.applicationContext)
    )
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val componentColors = yamiboComponentColors()
    var menuOpen by remember { mutableStateOf(false) }
    var blockTarget by remember { mutableStateOf<NativeForumBlockTarget?>(null) }
    var ratingDialog by remember { mutableStateOf<ForumPostRatingSummary?>(null) }
    val forumId = state.thread?.forumId.orEmpty()
    val posts = state.posts.filterNot {
        isBlocked(ForumBlockedItem.TYPE_POST, it.id, it.author.id, enabled, blockedItems)
    }
    val displayPosts = if (state.reverseOrder) posts.asReversed() else posts

    LaunchedEffect(state.page) {
        if (state.posts.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.scrollToTopEvent.collect { index ->
            if (index == 2) listState.animateScrollToItem(0)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(bottom = navBottom + 50.dp)
    ) {
        ForumTopBarV2(
            title = state.thread?.forumName?.takeIf(String::isNotBlank)
                ?: forumName?.takeIf(String::isNotBlank)
                ?: "论坛",
            headerColor = componentColors.topBarContainer,
            contentColor = componentColors.topBarContent,
            onBack = onBack        ) {
            if (false) IconButton(onClick = { onOpenWeb(ForumActionUrls.search) }) {
                Icon(Icons.Default.Search, "搜索论坛")
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
                    DropdownMenuItem(
                        text = { Text(forumText(25910, 34255, 20027, 39064)) },
                        onClick = {
                            menuOpen = false
                            favoriteVM.toggleFavorite(
                                ForumActionUrls.thread(threadId),
                                state.thread?.subject.orEmpty(),
                                threadId
                            ) { message -> YamiboToast.show(message = message) }
                        }
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
                    DropdownMenuItem(
                        text = { Text("电脑版") },
                        onClick = { menuOpen = false; onOpenWeb(ForumActionUrls.desktopThread(threadId)) }
                    )
                    DropdownMenuItem(
                        text = { Text("复制链接") },
                        onClick = {
                            menuOpen = false
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "yamibo_link",
                                    ForumActionUrls.thread(threadId)
                                )
                            )
                            YamiboToast.show(message = "已复制链接")
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
                state.error != null && state.posts.isEmpty() -> ForumErrorV2(state.error.orEmpty(), vm::refresh)
                else -> ThreadBodyV2(
                    state,
                    displayPosts,
                    cookie,
                    listState,
                    onOpenLink,
                    onOpenWeb,
                    vm::goToPage,
                    { summary -> ratingDialog = summary }
                ) { post ->
                    if (post.author.id != GlobalData.currentUid) blockTarget = NativeForumBlockTarget(
                        ForumBlockedItem.TYPE_POST,
                        post.id,
                        "${post.floor} 楼",
                        post.author.id.orEmpty(),
                        post.author.name
                    )
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
            Button(
                onClick = { onOpenWeb(ForumActionUrls.reply(threadId, forumId, page = state.page)) },
                enabled = forumId.isNotBlank() && state.thread?.isClosed != true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (state.thread?.isClosed == true) "主题已关闭" else "回复主题") }
        }
    }
    blockTarget?.let { NativeBlockMenuV2(it) { blockTarget = null } }
    ratingDialog?.let { summary ->
        ForumRatingsDialog(summary) { ratingDialog = null }
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
    onOpenRatings: (ForumPostRatingSummary) -> Unit,
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
                onlyOriginalPoster = state.onlyOriginalPoster,
                cookie = cookie,
                onOpenLink = onOpenLink,
                onLongClick = { onLongClick(post) },
                onReply = {
                    onOpenWeb(
                        ForumActionUrls.reply(
                            post.threadId,
                            state.thread?.forumId.orEmpty(),
                            post.id,
                            state.page
                        )
                    )
                },
                onComment = { onOpenWeb(ForumActionUrls.comment(post.threadId, post.id)) },
                onRate = { onOpenWeb(ForumActionUrls.rate(post.threadId, post.id)) },
                onOpenRatings = onOpenRatings
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
    onlyOriginalPoster: Boolean,
    cookie: String,
    onOpenLink: (String) -> Unit,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onComment: () -> Unit,
    onRate: () -> Unit,
    onOpenRatings: (ForumPostRatingSummary) -> Unit
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
                    SmallTagV2(if (post.isOriginalPost) "楼主" else "${post.floor} 楼")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    ForumPostContentV2(post, cookie, onOpenLink)
                }
                post.poll?.let { poll ->
                    Spacer(Modifier.height(6.dp))
                    ForumPollV2(poll)
                }
                post.ratingSummary?.let { summary ->
                    Spacer(Modifier.height(6.dp))
                    ForumPostRatingV2(summary, onOpenRatings)
                }
                if (post.ratingSummary != null) Spacer(Modifier.height(6.dp))
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
                    SmallActionText("点评", onComment)
                    SmallActionText("评分", onRate)
                    SmallActionText("回复", onReply)
                }
            }
        }
    }
}

@Composable
private fun ForumPollV2(poll: ForumPoll) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = poll.typeText,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                poll.participantCount?.let { count ->
                    Text(
                        text = "共有 " + count + " 人参与",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            poll.remainingText?.let { remaining ->
                Text(
                    text = remaining,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            poll.options.forEach { option ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = option.text,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        val percentText = if (option.percent % 1f == 0f) {
                            option.percent.toInt().toString()
                        } else {
                            option.percent.toString()
                        }
                        Text(
                            text = percentText + "% (" + option.voteCount + "票)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((option.percent / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
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
                        text = "…",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
@Composable
private fun ForumPostContentV2(post: ForumPost, cookie: String, onOpenLink: (String) -> Unit) {
    if (post.blocks.isEmpty() && post.attachments.isEmpty()) {
        Text("该楼层没有可显示的内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    post.blocks.forEach { block -> when (block) {
        is ForumPostBlock.Text -> ForumPostTextV2(block, onOpenLink)
        is ForumPostBlock.Image -> ForumPostImageV2(block.url, block.description, cookie)
    } }
    val inlineImages = post.blocks.filterIsInstance<ForumPostBlock.Image>().mapTo(hashSetOf(), ForumPostBlock.Image::url)
    post.attachments.forEach { attachment ->
        if (attachment.isImage && attachment.url !in inlineImages) {
            ForumPostImageV2(attachment.url, attachment.filename, cookie)
        } else if (!attachment.isImage) {
            ForumAttachmentRowV2(attachment, onOpenLink)
        }
    }
}

@Composable
private fun NativeBlockMenuV2(target: NativeForumBlockTarget, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
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
                        "作者：${target.authorName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
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
                            target.authorName
                        )
                        onDismiss()
                    }
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
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
private fun ForumErrorV2(message: String, onRetry: () -> Unit) {
    YamiboLoadError(
        title = "论坛暂时无法打开",
        message = message,
        onRetry = onRetry
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
private fun ForumPostImageV2(url: String, description: String, cookie: String) {
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
            modifier = Modifier.fillMaxWidth(),
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
    onDismiss: () -> Unit
) {
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
                if (summary.ratings.isEmpty()) {
                    Text(
                        text = "暂无评分明细",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    summary.ratings.forEach { rating ->
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
private fun forumText(vararg codePoints: Int): String =
    codePoints.map(Int::toChar).toCharArray().concatToString()

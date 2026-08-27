package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.ForumInlineImage
import org.shirakawatyu.yamibo.novel.bean.space.BlogBatchOperation
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.SpaceTabSpec
import org.shirakawatyu.yamibo.novel.ui.component.YamiboDialogSurface
import org.shirakawatyu.yamibo.novel.ui.component.YamiboTextEditorDialog
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboDangerColor
import org.shirakawatyu.yamibo.novel.ui.vm.SpaceListVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarLazyListScroll
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast

@Composable
fun SpaceAvatar(
    url: String?,
    size: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeSpaceListPage(
    title: String,
    tabs: List<SpaceTabSpec>,
    navController: NavController,
    uid: String = "",
    showBottomNavBar: Boolean = false,
    initialTabIndex: Int = 0,
    useSelectedTabAsTitle: Boolean = false,
    showCategories: Boolean = false,
    onTopBarAction: (() -> Unit)? = null,
    onActionClick: (String) -> Unit = {},
    onItemClick: (SpaceListItem) -> Unit
) {
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    var selectedTab by rememberSaveable {
        mutableIntStateOf(initialTabIndex.coerceIn(tabs.indices))
    }
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    var selectedFriendUid by rememberSaveable { mutableStateOf("") }
    var blogMenuTarget by remember { mutableStateOf<SpaceListItem.Blog?>(null) }
    var blogManageMode by remember { mutableStateOf(false) }
    var selectedBlogIds by remember { mutableStateOf(emptySet<String>()) }
    var blogDeleteTargets by remember { mutableStateOf<List<SpaceListItem.Blog>>(emptyList()) }
    var doingReplyTarget by remember { mutableStateOf<DoingActionTarget?>(null) }
    var doingDeleteTarget by remember { mutableStateOf<DoingActionTarget?>(null) }
    val viewModel: SpaceListVM = viewModel(
        key = "SpaceList-${tabs.joinToString("-") { it.request.kind.name }}-$uid",
        factory = SpaceListVM.Factory(uid)
    )
    val baseRequest = tabs[selectedTab.coerceIn(tabs.indices)].request
    val currentRequest = baseRequest.copy(categoryId = selectedCategoryId, fuid = selectedFriendUid)
    val baseState = viewModel.stateFor(baseRequest)
    val state = viewModel.stateFor(currentRequest)
    val canManageBlogs = baseRequest.kind == SpacePageKind.BLOG && baseRequest.view == "me"
    val currentBlogs = state.items.filterIsInstance<SpaceListItem.Blog>()

    LaunchedEffect(selectedTab) {
        selectedCategoryId = ""
        selectedFriendUid = ""
        blogManageMode = false
        selectedBlogIds = emptySet()
        viewModel.load(currentRequest)
    }

    LaunchedEffect(currentRequest) {
        viewModel.load(currentRequest)
    }

    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val bottomNavBarVM: BottomNavBarVM? = if (showBottomNavBar) {
        viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    } else {
        null
    }
    bottomNavBarVM?.let { ObserveBottomBarLazyListScroll(listState, it) }
    val refreshing = state.isLoading && state.items.isNotEmpty()
    val systemNavigationPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomBarPadding = when {
        !showBottomNavBar -> 0.dp
        bottomNavBarVM?.bottomBarScrollSuppressed == true -> systemNavigationPadding
        else -> systemNavigationPadding + 52.dp
    }

    LaunchedEffect(currentRequest) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = bottomBarPadding)
    ) {
        Surface(color = headerColor, contentColor = headerContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (blogManageMode) {
                            blogManageMode = false
                            selectedBlogIds = emptySet()
                        } else {
                            navController.popBackStack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = headerContent
                    )
                }
                Text(
                    text = if (blogManageMode) {
                        "管理日志 (" + selectedBlogIds.size + ")"
                    } else if (useSelectedTabAsTitle) {
                        tabs[selectedTab.coerceIn(tabs.indices)].label
                    } else {
                        title
                    },
                    color = headerContent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (!blogManageMode && onTopBarAction != null) {
                    IconButton(onClick = onTopBarAction) {
                        Icon(Icons.Default.Add, contentDescription = "新增", tint = headerContent)
                    }
                }
                if (canManageBlogs && (currentBlogs.isNotEmpty() || blogManageMode)) {
                    IconButton(
                        enabled = !viewModel.actionBusy,
                        onClick = {
                            blogManageMode = !blogManageMode
                            selectedBlogIds = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = if (blogManageMode) Icons.Default.Check
                            else Icons.Default.Checklist,
                            contentDescription = if (blogManageMode) "完成" else "管理日志",
                            tint = headerContent
                        )
                    }
                }
            }
        }
        if (!blogManageMode && tabs.size > 1) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = headerColor,
                contentColor = headerContent
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.label,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        },
                        selectedContentColor = headerContent,
                        unselectedContentColor = headerContent.copy(alpha = 0.62f)
                    )
                }
            }
        }
        if (!blogManageMode && showCategories && baseState.categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpaceCategoryChip(
                    name = "全部",
                    selected = selectedCategoryId.isBlank(),
                    onClick = { selectedCategoryId = "" }
                )
                baseState.categories.forEach { category ->
                    SpaceCategoryChip(
                        name = category.name,
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id }
                    )
                }
            }
        }
        if (!blogManageMode && showCategories && baseRequest.kind == SpacePageKind.BLOG && baseRequest.view == "we" &&
            baseState.friendFilters.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpaceCategoryChip("全部好友", selectedFriendUid.isBlank()) { selectedFriendUid = "" }
                baseState.friendFilters.forEach { friend ->
                    SpaceCategoryChip(friend.name, selectedFriendUid == friend.uid) {
                        selectedFriendUid = friend.uid
                    }
                }
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.items.isEmpty() -> {
                YamiboLoadError(
                    title = "页面无法打开",
                    onRetry = { viewModel.load(currentRequest, refresh = true) }
                )
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.load(currentRequest, refresh = true) },
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = refreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        val shouldGroupThreads = currentRequest.kind == SpacePageKind.USER_THREAD &&
                            currentRequest.type == "reply"
                        val displayItems = remember(state.items, shouldGroupThreads) {
                            if (shouldGroupThreads) {
                                groupSpaceItems(state.items)
                            } else {
                                state.items.map(SpaceDisplayItem::Single)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = if (blogManageMode) 96.dp else 24.dp
                            )
                        ) {
                        itemsIndexed(displayItems, key = { index, displayItem ->
                            when (displayItem) {
                                is SpaceDisplayItem.Single -> when (val item = displayItem.item) {
                                    is SpaceListItem.PrivateMessage -> "pm-${item.touid}-$index"
                                    is SpaceListItem.Notice -> "notice-${item.url}-$index"
                                    is SpaceListItem.Friend -> "friend-${item.uid}-$index"
                                    is SpaceListItem.Doing -> "doing-${item.uid}-${item.time}-$index"
                                    is SpaceListItem.Blog -> "blog-${item.blogId}-$index"
                                    is SpaceListItem.UserThread -> "thread-${item.tid}-${item.url}-$index"
                                }
                                is SpaceDisplayItem.ThreadGroup -> "group-${displayItem.tid}-$index"
                            }
                        }) { index, displayItem ->
                            when (displayItem) {
                                is SpaceDisplayItem.Single -> {
                                    val item = displayItem.item
                                    val blog = item as? SpaceListItem.Blog
                                    val isSelected = blog?.blogId in selectedBlogIds
                                    SpaceListItemRow(
                                        item = item,
                                        onClick = {
                                            if (blogManageMode && blog != null) {
                                                selectedBlogIds = if (isSelected) {
                                                    selectedBlogIds - blog.blogId
                                                } else {
                                                    selectedBlogIds + blog.blogId
                                                }
                                            } else {
                                                onItemClick(item)
                                            }
                                        },
                                        onDoingReply = { doingReplyTarget = it },
                                        onDoingDelete = { doingDeleteTarget = it },
                                        showBlogAuthor = baseRequest.view != "me",
                                        blogManageMode = blogManageMode && blog != null,
                                        blogSelected = isSelected,
                                        onBlogLongClick = if (!blogManageMode && item is SpaceListItem.Blog &&
                                            (item.editUrl.isNotBlank() || item.stickUrl.isNotBlank() || item.deleteUrl.isNotBlank())
                                        ) {
                                            {
                                                blogMenuTarget = item
                                                viewModel.loadBlogMenuItem(item) { loaded ->
                                                    if (blogMenuTarget?.blogId == loaded.blogId) {
                                                        blogMenuTarget = loaded
                                                    }
                                                }
                                            }
                                        } else null
                                    )
                                }
                                is SpaceDisplayItem.ThreadGroup -> {
                                    UserThreadGroupCard(
                                        group = displayItem,
                                        onTitleClick = {
                                            onItemClick(
                                                displayItem.items.first().copy(
                                                    url = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=${displayItem.tid}&mobile=no",
                                                    postId = ""
                                                )
                                            )
                                        },
                                        onItemClick = onItemClick
                                    )
                                }
                            }
                            if (index >= displayItems.lastIndex - 2 && state.nextUrl != null) {
                                LaunchedEffect(state.nextUrl) {
                                    viewModel.loadMore(currentRequest, append = true)
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = blogManageMode,
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { it + 100 }
                        ),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { it + 100 }
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                            border = BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val allSelected = currentBlogs.isNotEmpty() &&
                                    currentBlogs.all { it.blogId in selectedBlogIds }
                                BlogManageActionButton(
                                    label = if (allSelected) "取消" else "全选",
                                    icon = Icons.Default.SelectAll,
                                    enabled = !viewModel.actionBusy,
                                    onClick = {
                                        selectedBlogIds = if (allSelected) emptySet()
                                        else currentBlogs.map { it.blogId }.toSet()
                                    }
                                )
                                BlogManageActionButton(
                                    label = "全站",
                                    icon = Icons.Default.Public,
                                    enabled = selectedBlogIds.isNotEmpty() && !viewModel.actionBusy,
                                    onClick = {
                                        viewModel.submitBlogBatchAction(
                                            currentRequest,
                                            currentBlogs.filter { it.blogId in selectedBlogIds },
                                            BlogBatchOperation.PUBLIC
                                        ) { message, success ->
                                            YamiboToast.show(message = message)
                                            if (success) selectedBlogIds = emptySet()
                                        }
                                    }
                                )
                                BlogManageActionButton(
                                    label = "好友",
                                    icon = Icons.Default.Group,
                                    enabled = selectedBlogIds.isNotEmpty() && !viewModel.actionBusy,
                                    onClick = {
                                        viewModel.submitBlogBatchAction(
                                            currentRequest,
                                            currentBlogs.filter { it.blogId in selectedBlogIds },
                                            BlogBatchOperation.FRIENDS
                                        ) { message, success ->
                                            YamiboToast.show(message = message)
                                            if (success) selectedBlogIds = emptySet()
                                        }
                                    }
                                )
                                BlogManageActionButton(
                                    label = "自己",
                                    icon = Icons.Default.Lock,
                                    enabled = selectedBlogIds.isNotEmpty() && !viewModel.actionBusy,
                                    onClick = {
                                        viewModel.submitBlogBatchAction(
                                            currentRequest,
                                            currentBlogs.filter { it.blogId in selectedBlogIds },
                                            BlogBatchOperation.PRIVATE
                                        ) { message, success ->
                                            YamiboToast.show(message = message)
                                            if (success) selectedBlogIds = emptySet()
                                        }
                                    }
                                )
                                BlogManageActionButton(
                                    label = "删除",
                                    icon = Icons.Default.Delete,
                                    enabled = selectedBlogIds.isNotEmpty() && !viewModel.actionBusy,
                                    danger = true,
                                    onClick = {
                                        blogDeleteTargets = currentBlogs.filter {
                                            it.blogId in selectedBlogIds
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                blogMenuTarget?.let { target ->
                    BlogActionMenu(
                        target = target,
                        onDismiss = { blogMenuTarget = null },
                        busy = viewModel.actionBusy,
                        onAction = { action ->
                            when (action) {
                                BlogMenuActionType.EDIT -> {
                                    blogMenuTarget = null
                                    onActionClick(target.editUrl)
                                }
                                BlogMenuActionType.DELETE -> {
                                    blogMenuTarget = null
                                    blogDeleteTargets = listOf(target)
                                }
                                BlogMenuActionType.PIN -> {
                                    viewModel.submitBlogBatchAction(
                                        currentRequest,
                                        listOf(target),
                                        BlogBatchOperation.PIN
                                    ) { message, success ->
                                        YamiboToast.show(message = message)
                                        if (success) blogMenuTarget = null
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    doingReplyTarget?.let { target ->
        DoingReplyDialog(
            target = target,
            busy = viewModel.actionBusy,
            onDismiss = { if (!viewModel.actionBusy) doingReplyTarget = null },
            onSubmit = { message ->
                viewModel.submitDoingAction(currentRequest, target.actionUrl, message) { result, success ->
                    YamiboToast.show(message = result)
                    if (success) doingReplyTarget = null
                }
            }
        )
    }
    doingDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.actionBusy) doingDeleteTarget = null },
            title = { Text(target.title) },
            text = { Text("确定删除${target.label}吗？") },
            confirmButton = {
                TextButton(
                    enabled = !viewModel.actionBusy,
                    onClick = {
                        viewModel.submitDoingAction(currentRequest, target.actionUrl) { result, success ->
                            YamiboToast.show(message = result)
                            if (success) doingDeleteTarget = null
                        }
                    }
                ) { Text(if (viewModel.actionBusy) "删除中" else "删除", color = yamiboDangerColor()) }
            },
            dismissButton = {
                TextButton(enabled = !viewModel.actionBusy, onClick = { doingDeleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    if (blogDeleteTargets.isNotEmpty()) {
        val count = blogDeleteTargets.size
        AlertDialog(
            onDismissRequest = {
                if (!viewModel.actionBusy) blogDeleteTargets = emptyList()
            },
            title = { Text(if (count == 1) "删除日志" else "批量删除日志") },
            text = {
                Text(
                    if (count == 1) "确定删除《${blogDeleteTargets.first().title}》吗？"
                    else "确定删除选中的 $count 篇日志吗？删除后无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !viewModel.actionBusy,
                    onClick = {
                        viewModel.submitBlogBatchAction(
                            currentRequest,
                            blogDeleteTargets,
                            BlogBatchOperation.DELETE
                        ) { message, success ->
                            YamiboToast.show(message = message)
                            if (success) {
                                selectedBlogIds = emptySet()
                                blogDeleteTargets = emptyList()
                            }
                        }
                    }
                ) {
                    Text(
                        if (viewModel.actionBusy) "删除中" else "删除",
                        color = yamiboDangerColor()
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !viewModel.actionBusy,
                    onClick = { blogDeleteTargets = emptyList() }
                ) { Text("取消") }
            }
        )
    }
}

private sealed class SpaceDisplayItem {
    data class Single(val item: SpaceListItem) : SpaceDisplayItem()
    data class ThreadGroup(
        val tid: String,
        val title: String,
        val forumName: String,
        val items: List<SpaceListItem.UserThread>
    ) : SpaceDisplayItem()
}

private fun groupSpaceItems(items: List<SpaceListItem>): List<SpaceDisplayItem> {
    val groups = linkedMapOf<String, MutableList<SpaceListItem.UserThread>>()
    val order = mutableListOf<String>()
    val singles = linkedMapOf<String, SpaceListItem>()

    items.forEachIndexed { index, item ->
        if (item is SpaceListItem.UserThread) {
            val key = "thread:${item.tid}"
            if (key !in groups) order += key
            groups.getOrPut(key) { mutableListOf() }.add(item)
        } else {
            val key = "single:$index"
            order += key
            singles[key] = item
        }
    }

    return order.map { key ->
        if (key.startsWith("thread:")) {
            val group = groups.getValue(key)
            val first = group.first()
            SpaceDisplayItem.ThreadGroup(first.tid, first.title, first.forumName, group.toList())
        } else {
            SpaceDisplayItem.Single(singles.getValue(key))
        }
    }
}

@Composable
private fun SpaceListItemRow(
    item: SpaceListItem,
    onClick: () -> Unit,
    onDoingReply: (DoingActionTarget) -> Unit,
    onDoingDelete: (DoingActionTarget) -> Unit,
    showBlogAuthor: Boolean,
    blogManageMode: Boolean,
    blogSelected: Boolean,
    onBlogLongClick: (() -> Unit)?
) {
    when (item) {
        is SpaceListItem.PrivateMessage -> PmItemRow(item, onClick)
        is SpaceListItem.Notice -> NoticeItemRow(item, onClick)
        is SpaceListItem.Friend -> FriendItemRow(item, onClick)
        is SpaceListItem.Doing -> DoingItemRow(item, onDoingReply, onDoingDelete)
        is SpaceListItem.Blog -> BlogItemRow(
            item = item,
            showAuthor = showBlogAuthor,
            onClick = onClick,
            onLongClick = onBlogLongClick,
            isManageMode = blogManageMode,
            isSelected = blogSelected
        )
        is SpaceListItem.UserThread -> UserThreadItemRow(item, onClick)
    }
}

@Composable
private fun ItemCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        content()
    }
}

@Composable
private fun PmItemRow(item: SpaceListItem.PrivateMessage, onClick: () -> Unit) {
    ItemCard(onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpaceAvatar(item.avatarUrl, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isUnread) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {}
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.time,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.summary)
                        if (item.messageCount.isNotBlank()) {
                            if (isNotBlank()) append("  ·  ")
                            append("共 ")
                            append(item.messageCount)
                            append(" 条")
                        }
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NoticeItemRow(item: SpaceListItem.Notice, onClick: () -> Unit) {
    ItemCard(onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpaceAvatar(item.avatarUrl, 42)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.time,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.summary,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendItemRow(item: SpaceListItem.Friend, onClick: () -> Unit) {
    ItemCard(onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpaceAvatar(item.avatarUrl, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.statusText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.statusText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DoingItemRow(
    item: SpaceListItem.Doing,
    onReply: (DoingActionTarget) -> Unit,
    onDelete: (DoingActionTarget) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpaceAvatar(
                    url = item.avatarUrl,
                    size = 36,
                    modifier = Modifier.clip(CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.time,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (item.replyUrl.isNotBlank()) {
                    DoingActionButton(
                        label = "回复",
                        onClick = {
                            onReply(
                                DoingActionTarget(
                                    "回复 ${item.name}",
                                    "这条记录",
                                    item.replyUrl
                                )
                            )
                        }
                    )
                }
                if (item.deleteUrl.isNotBlank()) {
                    DoingActionButton(
                        label = "删除",
                        danger = true,
                        onClick = {
                            onDelete(
                                DoingActionTarget(
                                    "删除记录",
                                    "这条记录",
                                    item.deleteUrl
                                )
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ForumInlineText(
                text = item.content,
                images = item.contentImages,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            if (item.comments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        item.comments.forEachIndexed { index, comment ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row {
                                    Text(
                                        text = "${comment.authorName}：",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    ForumInlineText(
                                        text = comment.content,
                                        images = comment.contentImages,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = comment.time,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (comment.replyUrl.isNotBlank()) {
                                        DoingActionButton(
                                            label = "回复",
                                            onClick = {
                                                onReply(
                                                    DoingActionTarget(
                                                        "回复 ${comment.authorName}",
                                                        "这条评论",
                                                        comment.replyUrl
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    if (comment.deleteUrl.isNotBlank()) {
                                        DoingActionButton(
                                            label = "删除",
                                            danger = true,
                                            onClick = {
                                                onDelete(
                                                    DoingActionTarget(
                                                        "删除评论",
                                                        "这条评论",
                                                        comment.deleteUrl
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            if (index != item.comments.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoingActionButton(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (danger) {
                yamiboDangerColor().copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

private data class DoingActionTarget(
    val title: String,
    val label: String,
    val actionUrl: String
)

@Composable
private fun ForumInlineText(
    text: String,
    images: List<ForumInlineImage>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            modifier = modifier
        )
        return
    }

    val inlineContent = linkedMapOf<String, InlineTextContent>()
    val annotatedText = buildAnnotatedString {
        var cursor = 0
        images.sortedBy(ForumInlineImage::offset).forEachIndexed { index, image ->
            val start = image.offset.coerceIn(cursor, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            val id = "forum-image-$index"
            inlineContent[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = fontSize * 1.35f,
                    height = fontSize * 1.35f,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(image.url)
                        .crossfade(false)
                        .build(),
                    contentDescription = image.alternateText,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            appendInlineContent(id, image.alternateText)
            cursor = (start + image.alternateText.length).coerceAtMost(text.length)
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun DoingReplyDialog(
    target: DoingActionTarget,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    YamiboTextEditorDialog(
        title = target.title,
        placeholder = "请输入回复内容",
        confirmLabel = "发表回复",
        busy = busy,
        maximumLength = 200,
        minLines = 4,
        maxLines = 8,
        showForumSmilies = true,
        onDismiss = onDismiss,
        onConfirm = onSubmit
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlogItemRow(
    item: SpaceListItem.Blog,
    showAuthor: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    isManageMode: Boolean,
    isSelected: Boolean
) {
    val hapticFeedback = LocalHapticFeedback.current
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    Surface(
        modifier = if (onLongClick != null) {
            cardModifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
        } else {
            cardModifier.clickable(onClick = onClick)
        },
        shape = RoundedCornerShape(14.dp),
        color = if (isManageMode && isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isManageMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (isManageMode) 2.dp else 13.dp,
                    top = 13.dp,
                    end = if (item.isPinned) 42.dp else 13.dp,
                    bottom = 13.dp
                ),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (showAuthor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpaceAvatar(
                        url = item.authorAvatarUrl,
                        size = 36,
                        modifier = Modifier.clip(CircleShape)
                    )
                    Spacer(Modifier.width(9.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.authorName.ifBlank { "未知好友" },
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.time.isNotBlank()) {
                            Text(
                                text = " · " + item.time,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (item.visibilityText.isNotBlank() || item.category.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        BlogMetadataBadges(item)
                    }
                }
            }
            if (!showAuthor &&
                (item.visibilityText.isNotBlank() || item.category.isNotBlank())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlogMetadataBadges(item)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!showAuthor && item.time.isNotBlank()) {
                Text(
                    text = item.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
        if (item.isPinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "已置顶",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(19.dp)
                    .rotate(35f)
            )
        }
        }
    }
}

@Composable
private fun BlogMetadataBadges(item: SpaceListItem.Blog) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.visibilityText.takeIf(String::isNotBlank)?.let { visibility ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = visibility,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        item.category.takeIf(String::isNotBlank)?.let { category ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = category,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private enum class BlogMenuActionType {
    PIN,
    EDIT,
    DELETE
}

@Composable
private fun BlogManageActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = if (danger) yamiboDangerColor()
    else MaterialTheme.colorScheme.onSurfaceVariant
    val color = activeColor.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(text = label, color = color, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun BlogActionMenu(
    target: SpaceListItem.Blog,
    onDismiss: () -> Unit,
    busy: Boolean,
    onAction: (BlogMenuActionType) -> Unit
) {
    val metadataLines = listOf(
        "分类：" + target.category.ifBlank { "未分类" },
        "标签：" + target.tags.joinToString("、").ifBlank { "无" }
    )

    Dialog(onDismissRequest = onDismiss) {
        YamiboDialogSurface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (target.title.isNotBlank()) {
                    Text(
                        text = target.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                metadataLines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (target.title.isNotBlank() || metadataLines.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (target.stickUrl.isNotBlank()) {
                    BlogMenuAction(
                        label = if (target.isPinned) "取消置顶" else "置顶",
                        icon = Icons.Filled.PushPin,
                        iconRotation = 35f,
                        enabled = !busy,
                        onClick = { onAction(BlogMenuActionType.PIN) }
                    )
                }
                if (target.editUrl.isNotBlank()) {
                    BlogMenuAction(
                        label = "编辑",
                        icon = Icons.Filled.EditNote,
                        enabled = !busy,
                        onClick = { onAction(BlogMenuActionType.EDIT) }
                    )
                }
                if (target.deleteUrl.isNotBlank()) {
                    BlogMenuAction(
                        label = "删除",
                        icon = Icons.Filled.Delete,
                        enabled = !busy,
                        danger = true,
                        onClick = { onAction(BlogMenuActionType.DELETE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlogMenuAction(
    label: String,
    icon: ImageVector,
    iconRotation: Float = 0f,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (danger) yamiboDangerColor()
    else MaterialTheme.colorScheme.primary
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(iconRotation),
                tint = contentColor
            )
            Spacer(Modifier.width(12.dp))
            Text(label, color = contentColor)
        }
    }
}

@Composable
private fun SpaceCategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            text = name,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun UserThreadGroupCard(
    group: SpaceDisplayItem.ThreadGroup,
    onTitleClick: () -> Unit,
    onItemClick: (SpaceListItem.UserThread) -> Unit
) {
    ItemCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            UserThreadInlineTitle(
                item = group.items.first(),
                modifier = Modifier.clickable(onClick = onTitleClick)
            )
            Spacer(Modifier.height(7.dp))
            group.items.forEachIndexed { index, item ->
                val accent = when (item.entryType) {
                    "点评" -> MaterialTheme.colorScheme.secondary
                    "回复" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                color = accent,
                                topLeft = Offset(0f, 0f),
                                size = Size(3.dp.toPx(), size.height)
                            )
                        }
                        .padding(start = 10.dp)
                        .padding(vertical = 3.dp)
                        .clickable { onItemClick(item) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        if (item.replyExcerpt.isNotBlank()) {
                            Text(
                                text = item.replyExcerpt,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.time.isNotBlank()) {
                            Text(
                                text = item.time,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                if (index < group.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 10.dp, top = 3.dp, bottom = 3.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserThreadItemRow(item: SpaceListItem.UserThread, onClick: () -> Unit) {
    ItemCard(onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            UserThreadInlineTitle(item)
            if (item.replyExcerpt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.replyExcerpt,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.time,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.weight(1f))
                if (item.viewCount.isNotBlank()) {
                    Text(
                        text = "浏览 ${item.viewCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (item.replyCount.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "回复 ${item.replyCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserThreadInlineTitle(
    item: SpaceListItem.UserThread,
    modifier: Modifier = Modifier
) {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    val title = buildAnnotatedString {
        fun appendBadge(id: String, text: String, primary: Boolean) {
            inlineContent[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = (text.length * 11 + 14).sp,
                    height = 20.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                UserThreadBadge(
                    text = text,
                    primary = primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            appendInlineContent(id, "[$text]")
            append(" ")
        }

        fun appendStatusIcon(id: String, alternateText: String, content: @Composable () -> Unit) {
            inlineContent[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = 19.sp,
                    height = 18.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) { content() }
            appendInlineContent(id, alternateText)
            append(" ")
        }

        if (item.forumName.isNotBlank()) {
            appendBadge("forum", item.forumName, primary = false)
        }
        if (item.isClosed) {
            appendStatusIcon("closed", "[已关闭]") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "已关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        if (item.isPoll) {
            appendStatusIcon("poll", "[投票]") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.HowToVote,
                        contentDescription = "投票",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        append(item.title)
    }

    Text(
        text = title,
        inlineContent = inlineContent,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun UserThreadBadge(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                color = if (primary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                maxLines = 1,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

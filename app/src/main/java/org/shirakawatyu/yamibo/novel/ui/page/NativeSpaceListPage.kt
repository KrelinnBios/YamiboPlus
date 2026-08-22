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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.SpaceTabSpec
import org.shirakawatyu.yamibo.novel.ui.component.YamiboDialogSurface
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

    LaunchedEffect(selectedTab) {
        selectedCategoryId = ""
        selectedFriendUid = ""
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

    LaunchedEffect(currentRequest, state.page) {
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
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = headerContent
                    )
                }
                Text(
                    text = title,
                    color = headerContent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (onTopBarAction != null) {
                    IconButton(onClick = onTopBarAction) {
                        Icon(Icons.Default.Add, contentDescription = "新增", tint = headerContent)
                    }
                }
            }
        }
        if (tabs.size > 1) {
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
        if (showCategories && baseState.categories.isNotEmpty()) {
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
        if (showCategories && baseRequest.kind == SpacePageKind.BLOG && baseRequest.view == "we" &&
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        )
                    ) {
                        itemsIndexed(state.items, key = { index, item ->
                            when (item) {
                                is SpaceListItem.PrivateMessage -> "pm-${item.touid}-$index"
                                is SpaceListItem.Notice -> "notice-${item.url}-$index"
                                is SpaceListItem.Friend -> "friend-${item.uid}-$index"
                                is SpaceListItem.Doing -> "doing-${item.uid}-${item.time}-$index"
                                is SpaceListItem.Blog -> "blog-${item.blogId}-$index"
                                is SpaceListItem.UserThread -> "thread-${item.tid}-${item.url}-$index"
                            }
                        }) { _, item ->
                            SpaceListItemRow(
                                item = item,
                                onClick = { onItemClick(item) },
                                onDoingReply = { doingReplyTarget = it },
                                onDoingDelete = { doingDeleteTarget = it },
                                showBlogAuthor = baseRequest.view != "me",
                                onBlogLongClick = if (item is SpaceListItem.Blog &&
                                    (item.editUrl.isNotBlank() || item.stickUrl.isNotBlank() || item.deleteUrl.isNotBlank())
                                ) {
                                    { blogMenuTarget = item }
                                } else null
                            )
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
                        } else if (state.previousUrl != null || state.nextUrl != null) {
                            item(key = "load-more") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    TextButton(
                                        enabled = state.previousUrl != null,
                                        onClick = { viewModel.loadPrevious(currentRequest) }
                                    ) { Text("上一页") }
                                    TextButton(
                                        enabled = state.nextUrl != null,
                                        onClick = { viewModel.loadMore(currentRequest) }
                                    ) { Text("下一页") }
                                }
                            }
                        }
                    }
                }
                blogMenuTarget?.let { target ->
                    BlogActionMenu(
                        target = target,
                        onDismiss = { blogMenuTarget = null },
                        onAction = { url ->
                            blogMenuTarget = null
                            onActionClick(url)
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
}

@Composable
private fun SpaceListItemRow(
    item: SpaceListItem,
    onClick: () -> Unit,
    onDoingReply: (DoingActionTarget) -> Unit,
    onDoingDelete: (DoingActionTarget) -> Unit,
    showBlogAuthor: Boolean,
    onBlogLongClick: (() -> Unit)?
) {
    when (item) {
        is SpaceListItem.PrivateMessage -> PmItemRow(item, onClick)
        is SpaceListItem.Notice -> NoticeItemRow(item, onClick)
        is SpaceListItem.Friend -> FriendItemRow(item, onClick)
        is SpaceListItem.Doing -> DoingItemRow(item, onDoingReply, onDoingDelete)
        is SpaceListItem.Blog -> BlogItemRow(item, showBlogAuthor, onClick, onBlogLongClick)
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

@Composable
private fun NoticeItemRow(item: SpaceListItem.Notice, onClick: () -> Unit) {
    ItemCard(onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
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
            Text(
                text = item.content,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
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
                                    Text(
                                        text = comment.content,
                                        fontSize = 13.sp,
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
private fun DoingReplyDialog(
    target: DoingActionTarget,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var message by remember(target) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(200) },
                placeholder = { Text("请输入回复内容") },
                supportingText = { Text("${message.length}/200") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().widthIn(min = 240.dp).imePadding(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = message.isNotBlank() && !busy,
                onClick = { onSubmit(message.trim()) }
            ) { Text(if (busy) "提交中" else "发表") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlogItemRow(
    item: SpaceListItem.Blog,
    showAuthor: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
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

@Composable
private fun BlogActionMenu(
    target: SpaceListItem.Blog,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val metadataLines = buildList {
        target.category.takeIf(String::isNotBlank)?.let { add("分类：$it") }
        target.tags
            .joinToString("、")
            .takeIf(String::isNotBlank)
            ?.let { add("标签：$it") }
    }

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
                        label = "置顶",
                        icon = Icons.Filled.KeyboardArrowUp,
                        onClick = { onAction(target.stickUrl) }
                    )
                }
                if (target.editUrl.isNotBlank()) {
                    BlogMenuAction(
                        label = "编辑",
                        icon = Icons.Filled.EditNote,
                        onClick = { onAction(target.editUrl) }
                    )
                }
                if (target.deleteUrl.isNotBlank()) {
                    BlogMenuAction(
                        label = "删除",
                        icon = Icons.Filled.Delete,
                        danger = true,
                        onClick = { onAction(target.deleteUrl) }
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
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (danger) yamiboDangerColor()
    else MaterialTheme.colorScheme.primary
    TextButton(
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
                modifier = Modifier.size(20.dp),
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
private fun UserThreadItemRow(item: SpaceListItem.UserThread, onClick: () -> Unit) {
    ItemCard(onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.forumName.isNotBlank()) {
                    UserThreadBadge(text = item.forumName)
                    Spacer(Modifier.width(6.dp))
                }
                if (item.entryType.isNotBlank()) {
                    UserThreadBadge(
                        text = item.entryType,
                        primary = true
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (item.isClosed) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "已关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (item.isPoll) {
                    Icon(
                        imageVector = Icons.Filled.HowToVote,
                        contentDescription = "投票",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
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
private fun UserThreadBadge(text: String, primary: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

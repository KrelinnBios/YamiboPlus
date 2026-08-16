package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.SpaceTabSpec
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.vm.SpaceListVM

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
    var selectedTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(tabs.indices)) }
    var selectedCategoryId by remember { mutableStateOf("") }
    val viewModel: SpaceListVM = viewModel(
        key = "SpaceList-${tabs.joinToString("-") { it.request.kind.name }}-$uid",
        factory = SpaceListVM.Factory(uid)
    )
    val baseRequest = tabs[selectedTab.coerceIn(tabs.indices)].request
    val currentRequest = baseRequest.copy(categoryId = selectedCategoryId)
    val baseState = viewModel.stateFor(baseRequest)
    val state = viewModel.stateFor(currentRequest)

    LaunchedEffect(selectedTab) {
        selectedCategoryId = ""
        viewModel.load(currentRequest)
    }

    LaunchedEffect(currentRequest) {
        viewModel.load(currentRequest)
    }

    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val refreshing = state.isLoading && state.items.isNotEmpty()

    LaunchedEffect(currentRequest, state.page) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    fontWeight = FontWeight.Medium
                )
                if (onTopBarAction != null) {
                    IconButton(onClick = onTopBarAction) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = headerContent)
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
                            bottom = if (showBottomNavBar) 96.dp else 24.dp
                        )
                    ) {
                        items(state.items, key = { item ->
                            when (item) {
                                is SpaceListItem.PrivateMessage -> "pm-${item.touid}"
                                is SpaceListItem.Notice -> "notice-${item.url}"
                                is SpaceListItem.Friend -> "friend-${item.uid}"
                                is SpaceListItem.Doing -> "doing-${item.uid}-${item.time}"
                                is SpaceListItem.Blog -> "blog-${item.blogId}"
                                is SpaceListItem.UserThread -> "thread-${item.tid}"
                            }
                        }) { item ->
                            SpaceListItemRow(
                                item = item,
                                onClick = { onItemClick(item) },
                                onActionClick = onActionClick
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
                        } else {
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
                                    Text(
                                        text = "第 ${state.page} 页",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    TextButton(
                                        enabled = state.nextUrl != null,
                                        onClick = { viewModel.loadMore(currentRequest) }
                                    ) { Text("下一页") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceListItemRow(
    item: SpaceListItem,
    onClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    when (item) {
        is SpaceListItem.PrivateMessage -> PmItemRow(item, onClick)
        is SpaceListItem.Notice -> NoticeItemRow(item, onClick)
        is SpaceListItem.Friend -> FriendItemRow(item, onClick)
        is SpaceListItem.Doing -> DoingItemRow(item)
        is SpaceListItem.Blog -> BlogItemRow(item, onClick, onActionClick)
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
private fun DoingItemRow(item: SpaceListItem.Doing) {
    ItemCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpaceAvatar(item.avatarUrl, 38)
                Spacer(Modifier.width(10.dp))
                Column {
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
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.comments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item.comments.forEach { comment ->
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
                                if (comment.time.isNotBlank()) {
                                    Text(
                                        text = comment.time,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlogItemRow(
    item: SpaceListItem.Blog,
    onClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    ItemCard(onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.summary,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.authorName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.time,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (item.editUrl.isNotBlank() || item.stickUrl.isNotBlank() || item.deleteUrl.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (item.editUrl.isNotBlank()) {
                        TextButton(onClick = { onActionClick(item.editUrl) }) { Text("编辑") }
                    }
                    if (item.stickUrl.isNotBlank()) {
                        TextButton(onClick = { onActionClick(item.stickUrl) }) { Text("置顶") }
                    }
                    if (item.deleteUrl.isNotBlank()) {
                        TextButton(onClick = { onActionClick(item.deleteUrl) }) { Text("删除") }
                    }
                }
            }
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
                if (item.isClosed) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "已关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
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
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.forumName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = item.forumName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
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

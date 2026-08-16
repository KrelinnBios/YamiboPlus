package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.ui.widget.blockedItemPostUrl
import org.shirakawatyu.yamibo.novel.ui.widget.favorite.FavoriteTopSearchField
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlockedItem
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlocklistManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeBlocklistPage(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit = {}
) {
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var isSearchBarExpanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val tabs = listOf("全部", "主题", "楼层", "用户")

    val syncState by ForumBlocklistManager.syncState.collectAsState()
    var wasSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(syncState.isSyncing) {
        if (syncState.isSyncing) {
            wasSyncing = true
        } else if (wasSyncing) {
            wasSyncing = false
            YamiboToast.show(message = syncState.message)
        }
    }

    BackHandler(enabled = isSearchBarExpanded) {
        searchText = ""
        isSearchBarExpanded = false
    }

    val items by ForumBlocklistManager.items.collectAsState()
    val orderedItems = items.asReversed()
    val filteredItems = remember(orderedItems, searchText, selectedTab) {
        orderedItems.filter { item ->
            val matchesSearch = searchText.isEmpty() ||
                item.id.contains(searchText, ignoreCase = true) ||
                item.title.contains(searchText, ignoreCase = true) ||
                item.threadTitle.contains(searchText, ignoreCase = true)
            val matchesTab = when (selectedTab) {
                0 -> true
                1 -> item.type == "thread"
                2 -> item.type == "post"
                3 -> item.type == "user"
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopBar
        Surface(color = headerColor, contentColor = headerContent) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = headerContent)
                }
                if (isSearchBarExpanded) {
                    FavoriteTopSearchField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        onClose = {
                            searchText = ""
                            isSearchBarExpanded = false
                        },
                        resultText = if (searchText.isNotBlank()) "${filteredItems.size}项" else null,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp, end = 2.dp)
                    )
                } else {
                    Text(
                        "屏蔽管理",
                        Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = headerContent
                    )
                }
                if (!isSearchBarExpanded) {
                    IconButton(onClick = { isSearchBarExpanded = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索屏蔽项",
                            tint = headerContent
                        )
                    }
                }
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空",
                        tint = headerContent
                    )
                }
            }
        }

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = syncState.isSyncing,
            onRefresh = { ForumBlocklistManager.syncRemote(force = true) },
            state = pullState,
            modifier = Modifier.weight(1f),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = syncState.isSyncing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                // Content
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchText.isEmpty()) "暂无屏蔽项" else "未找到匹配项",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredItems, key = { "${it.type}-${it.id}" }) { item ->
                            BlocklistItem(
                                item = item,
                                onRemove = {
                                    scope.launch(Dispatchers.IO) {
                                        ForumBlocklistManager.remove(item.type, item.id)
                                        withContext(Dispatchers.Main) {
                                            YamiboToast.show(message = "已移除")
                                        }
                                    }
                                },
                                onOpenPost = { url ->
                                    onOpenPost(url)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Clear Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("清空屏蔽列表", fontSize = 18.sp) },
            text = {
                Text(
                    "确定要清空所有屏蔽项吗？此操作不可撤销。",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch(Dispatchers.IO) {
                        ForumBlocklistManager.clear()
                        withContext(Dispatchers.Main) {
                            YamiboToast.show(message = "已清空")
                        }
                    }
                }) {
                    Text(
                        "确认",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", fontSize = 15.sp)
                }
            }
        )
    }
}

@Composable
private fun BlocklistItem(
    item: ForumBlockedItem,
    onRemove: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    blockedItemPostUrl(item)?.let(onOpenPost)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Text(
                    text = when (item.type) {
                        ForumBlockedItem.TYPE_THREAD -> "主题"
                        ForumBlockedItem.TYPE_POST -> "楼层"
                        ForumBlockedItem.TYPE_USER -> "用户"
                        else -> item.type
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.type == ForumBlockedItem.TYPE_POST && item.threadTitle.isNotBlank()) {
                        item.threadTitle
                    } else {
                        item.title.ifEmpty { item.id }
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = blocklistItemMeta(item, item.title),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun blocklistItemMeta(item: ForumBlockedItem, floorTitle: String = ""): String {
    val authorPart = when {
        item.authorName.isNotBlank() && item.authorUid.isNotBlank() ->
            "${item.authorName}（UID ${item.authorUid}）"
        item.authorName.isNotBlank() -> item.authorName
        item.authorUid.isNotBlank() -> "UID ${item.authorUid}"
        else -> null
    }
    return when (item.type) {
        ForumBlockedItem.TYPE_USER -> "UID ${item.id}"
        ForumBlockedItem.TYPE_POST -> if (floorTitle.isNotBlank() && item.threadTitle.isNotBlank()) {
            listOfNotNull(floorTitle, authorPart).joinToString(" · ")
        } else {
            authorPart ?: "ID ${item.id}"
        }
        else -> authorPart ?: "ID ${item.id}"
    }
}

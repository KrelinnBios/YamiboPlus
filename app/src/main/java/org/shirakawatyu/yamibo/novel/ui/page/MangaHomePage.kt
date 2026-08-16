package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.MangaHomeItem
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboFocusBorderColor
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteTypeResolver
import org.shirakawatyu.yamibo.novel.ui.vm.MangaHomeVM
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingOverlay
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingStep

import org.shirakawatyu.yamibo.novel.util.OnboardingUtil
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.manga.MangaProber
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner
import java.net.URLEncoder

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MangaHomePage(
    navController: NavController,
    mangaHomeVM: MangaHomeVM = viewModel()
) {
    val state by mangaHomeVM.uiState.collectAsState()
    val context = LocalContext.current
    val bottomNavBarVM: BottomNavBarVM =
        viewModel(viewModelStoreOwner = context as ComponentActivity)
    val listState = rememberLazyListState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()
    var openingTid by remember { mutableStateOf<String?>(null) }

    // 收藏流：构建“已收藏漫画”的去集数标题集合，用于卡片右下角收藏标记。
    // 收藏流由 DataStore 驱动，收藏页同步、收藏/取消收藏后都会自动触发集合重建。
    val favoritesState = FavoriteUtil.getFavoriteFlow().collectAsState(initial = emptyList())
    val favoritedCleanTitles = remember(favoritesState.value) {
        favoritesState.value
            .asSequence()
            .filterNot(FavoriteTypeResolver::isNovelFavorite)
            .mapTo(HashSet()) { MangaTitleCleaner.getCleanBookName(it.title) }
    }

    OnboardingOverlay(
        page = OnboardingUtil.Page.MANGA_HOME,
        enabled = GlobalData.currentUid.isNotBlank(),
        steps = listOf(
            OnboardingStep(
                title = "漫画发现小提示",
                description = "可在顶部分区切换中文漫画区和漫画图源区，点击搜索图标按书名/作者搜索。"
            ),
            OnboardingStep(
                title = "漫画发现小提示",
                description = "点开一部作品后会自动生成本地漫画目录，之后可以在目录里更新、整理章节顺序。"
            )
        )
    )

    val componentColors = yamiboComponentColors()
    val headerContainerColor = componentColors.topBarContainer
    val headerContentColor = componentColors.topBarContent
    val sectionContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val sectionOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val selectedSectionColor = componentColors.baseRow
    val selectedSectionContentColor = MaterialTheme.colorScheme.onSurface
    val unselectedSectionContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    val selectedSectionBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.isLoadingMore) {
        if (!state.isLoading && !state.isLoadingMore) {
            isRefreshing = false
        }
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.scrollToTopEvent.collect { index ->
            if (index == 0) listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.goHomeEvent.collect { route ->
            if (route == "MangaHomePage") {
                // 只有确实处于搜索态才清空并重新拉取；
                // 否则单纯切回本页不要触发网络刷新，网络波动时会把好端端的列表刷成错误页。
                if (mangaHomeVM.uiState.value.query.isNotBlank()) {
                    mangaHomeVM.clearSearch()
                }
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = navBottom + 50.dp)
    ) {
        Surface(
            color = headerContainerColor,
            contentColor = headerContentColor
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .width(220.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(sectionContainerColor)
                        .border(
                            width = 1.dp,
                            color = sectionOutlineColor,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(3.dp)
                        .align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MangaHomeVM.sections.forEach { (fid, label) ->
                        val selected = state.selectedFid == fid
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { mangaHomeVM.setSection(fid) },
                            color = if (selected) selectedSectionColor
                            else Color.Transparent,
                            contentColor = if (selected) selectedSectionContentColor
                            else unselectedSectionContentColor,
                            border = if (selected) {
                                BorderStroke(
                                    width = 1.dp,
                                    color = selectedSectionBorderColor
                                )
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
                var searchFocused by remember { mutableStateOf(false) }
                val searchFocusColor = yamiboFocusBorderColor()
                OutlinedTextField(
                    value = state.query,
                    onValueChange = mangaHomeVM::updateQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(52.dp)
                        .onFocusChanged { searchFocused = it.isFocused }
                        .then(
                            if (searchFocused) {
                                Modifier.border(2.dp, searchFocusColor, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            text = "搜索当前版区漫画",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotBlank()) {
                            IconButton(
                                onClick = mangaHomeVM::clearSearch,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清空搜索",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { mangaHomeVM.submitSearch() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 聚焦描边用主题高亮色的提亮版（浅色主题大幅提亮），并叠加 2dp 加粗边框。
                        focusedBorderColor = searchFocusColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = searchFocusColor
                    )
                )
            }
        }

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                mangaHomeVM.refresh()
            },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && state.items.isEmpty() -> {
                    YamiboLoadError(
                        title = "漫画首页无法打开",
                        onRetry = mangaHomeVM::refresh
                    )
                }
                state.items.isEmpty() -> {
                    Text(
                        text = if (state.query.isBlank()) "暂无漫画" else "没有精确匹配的漫画",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.tid }
                        ) { index, item ->
                            MangaHomeRow(
                                item = item,
                                alternate = index % 2 == 1,
                                baseRowColor = componentColors.baseRow,
                                alternateRowColor = componentColors.alternateRow,
                                isOpening = openingTid == item.tid,
                                isFavorited = MangaTitleCleaner.getCleanBookName(item.title) in favoritedCleanTitles,
                                onClick = {
                                    if (openingTid == null) {
                                        openingTid = item.tid
                                        // 看门狗：探测请求挂死时自动解锁，否则整页列表会永久不可点击
                                        scope.launch {
                                            delay(12_000L)
                                            if (openingTid == item.tid) openingTid = null
                                        }
                                        scope.launch {
                                            MangaProber().probeUrl(
                                                context = context,
                                                url = item.url,
                                                forceRefresh = true,
                                                onSuccess = { urls, title, html ->
                                                    val normalizedUrls = urls
                                                        .map(String::trim)
                                                        .filter(String::isNotBlank)
                                                        .distinct()
                                                    GlobalData.tempMangaUrls = normalizedUrls
                                                    GlobalData.tempMangaIndex = 0
                                                    GlobalData.tempHtml = html
                                                    GlobalData.tempTitle = title
                                                    val encoded =
                                                        URLEncoder.encode(item.url, "utf-8")
                                                    navController.navigate(
                                                        "NativeMangaPage?url=$encoded&originalUrl=$encoded"
                                                    )
                                                    openingTid = null
                                                },
                                                onFallback = {
                                                    val encoded =
                                                        URLEncoder.encode(item.url, "utf-8")
                                                    navController.navigate(
                                                        "MangaWebPage/$encoded/$encoded?fastForward=false&initialPage=0"
                                                    )
                                                    openingTid = null
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                            if (index >= state.items.lastIndex - 4) {
                                LaunchedEffect(state.page, state.items.size) {
                                    mangaHomeVM.loadMore()
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
private fun MangaHomeRow(
    item: MangaHomeItem,
    alternate: Boolean,
    baseRowColor: Color,
    alternateRowColor: Color,
    isOpening: Boolean,
    isFavorited: Boolean,
    onClick: () -> Unit
) {
    val rowColor = if (alternate) alternateRowColor else baseRowColor
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(70.dp)
                    .height(94.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (item.coverUrl != null) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = listOf(item.authorName, item.date)
                        .filter(String::isNotBlank)
                        .joinToString("  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isOpening) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        // 收藏心形标记叠加在卡片右下角，不参与布局尺寸，避免卡片结构变化
        if (isFavorited) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "已收藏",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

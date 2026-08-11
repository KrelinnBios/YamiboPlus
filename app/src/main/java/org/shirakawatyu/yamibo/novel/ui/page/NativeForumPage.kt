package org.shirakawatyu.yamibo.novel.ui.page

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.ui.vm.ForumThreadVM
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.vm.ForumVM
import org.shirakawatyu.yamibo.novel.util.DarkThemeColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NativeForumPage(
    onThreadClick: (ForumThread) -> Unit,
    forumVM: ForumVM = viewModel()
) {
    val state by forumVM.uiState.collectAsState()
    val isDarkMode by GlobalData.isDarkMode.collectAsState()
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val classicDarkColors = DarkThemeColors.CLASSIC
    val headerColor = if (isDarkMode) classicDarkColors.statusBar else MaterialTheme.colorScheme.primary
    val headerContentColor = if (isDarkMode) classicDarkColors.onPrimary else MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(state.selectedForum?.id) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = navBottom + 50.dp)
    ) {
        Surface(color = headerColor, contentColor = headerContentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.selectedForum != null) {
                    IconButton(onClick = forumVM::showForumIndex) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回论坛首页")
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    text = state.selectedForum?.name ?: "论坛",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = forumVM::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = forumVM::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = if (isDarkMode) classicDarkColors.surfaceVariant else MaterialTheme.colorScheme.surface,
                    color = if (isDarkMode) classicDarkColors.primary else MaterialTheme.colorScheme.primary
                )
            }
        ) {
            when {
                state.isLoading && currentItemsEmpty(state) -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && currentItemsEmpty(state) -> {
                    ForumError(message = state.error.orEmpty(), onRetry = forumVM::refresh)
                }
                state.selectedForum == null -> {
                    ForumIndexList(state, forumVM::openForum, listState)
                }
                else -> {
                    ForumThreadList(state, onThreadClick, forumVM::loadMore, listState)
                }
            }
        }
    }
}

private fun currentItemsEmpty(state: ForumState): Boolean =
    if (state.selectedForum == null) state.categories.isEmpty() else state.threads.isEmpty()

@Composable
private fun ForumIndexList(
    state: ForumState,
    onForumClick: (ForumBoard) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (state.categories.isEmpty()) {
        Text(
            text = "暂无可浏览板块",
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        state.categories.forEach { category ->
            item(key = "category-${category.id}") {
                Text(
                    text = category.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            category.forums.forEachIndexed { index, forum ->
                item(key = "forum-${forum.id}") {
                    ForumBoardRow(
                        forum = forum,
                        alternate = index % 2 == 1,
                        onClick = { onForumClick(forum) }
                    )
                    forum.subforums.forEach { subforum ->
                        ForumBoardRow(
                            forum = subforum,
                            alternate = index % 2 == 1,
                            nested = true,
                            onClick = { onForumClick(subforum) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumBoardRow(
    forum: ForumBoard,
    alternate: Boolean,
    nested: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (alternate) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = if (nested) 30.dp else 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp)
    ) {
        Text(
            text = forum.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (nested) 14.sp else 15.sp,
            fontWeight = if (nested) FontWeight.Medium else FontWeight.SemiBold
        )
        if (forum.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = forum.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = "今日 ${forum.todayPostCount}  ·  主题 ${forum.threadCount}  ·  帖子 ${forum.postCount}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ForumThreadList(
    state: ForumState,
    onThreadClick: (ForumThread) -> Unit,
    onLoadMore: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (state.threads.isEmpty()) {
        Text(
            text = "这个板块暂时没有主题",
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val stickyThreads = state.threads.filter(ForumThread::isSticky)
    val regularThreads = state.threads.filterNot(ForumThread::isSticky)
    val visibleThreads = stickyThreads + regularThreads
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        state.selectedForum?.description?.takeIf(String::isNotBlank)?.let { description ->
            item(key = "forum-description") {
                Text(
                    text = description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                        .padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp
                )
            }
        }
        itemsIndexed(visibleThreads, key = { _, thread -> thread.id }) { index, thread ->
            ForumThreadRow(thread = thread, onClick = { onThreadClick(thread) })
            if (index < visibleThreads.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (index >= visibleThreads.lastIndex - 4) {
                LaunchedEffect(state.page, visibleThreads.size) { onLoadMore() }
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        state.error?.let { error ->
            item(key = "page-error") {
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ForumThreadRow(thread: ForumThread, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thread.isSticky) {
                Text(
                    text = "置顶",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 7.dp)
                )
            }
            thread.typeName?.let { type ->
                Text(
                    text = type,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 7.dp)
                )
            }
            Text(
                text = thread.subject,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = listOf(thread.authorName, thread.createdAt)
                .filter(String::isNotBlank)
                .joinToString("  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "回复 ${thread.replyCount}  ·  查看 ${thread.viewCount}" +
                thread.lastPoster.takeIf(String::isNotBlank)?.let { "  ·  最后回复 $it" }.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ForumError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "加载失败",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("论坛暂时无法打开", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("刷新页面")
        }
    }
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NativeThreadPage(
    threadId: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    forumThreadVM: ForumThreadVM = viewModel(
        key = "NativeThreadPage-$threadId",
        factory = ForumThreadVM.factory(threadId)
    )
) {
    val state by forumThreadVM.uiState.collectAsState()
    val isDarkMode by GlobalData.isDarkMode.collectAsState()
    val cookie by GlobalData.cookieFlow.collectAsState(initial = "")
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val classicDarkColors = DarkThemeColors.CLASSIC
    val headerColor = if (isDarkMode) classicDarkColors.statusBar else MaterialTheme.colorScheme.primary
    val headerContentColor = if (isDarkMode) classicDarkColors.onPrimary else MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = navBottom + 50.dp)
    ) {
        Surface(color = headerColor, contentColor = headerContentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回主题列表")
                }
                Text(
                    text = state.thread?.subject ?: "主题详情",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = forumThreadVM::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = forumThreadVM::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = if (isDarkMode) classicDarkColors.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    color = if (isDarkMode) classicDarkColors.primary
                    else MaterialTheme.colorScheme.primary
                )
            }
        ) {
            when {
                state.isLoading && state.posts.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.posts.isEmpty() ->
                    ForumError(state.error.orEmpty(), forumThreadVM::refresh)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    state.thread?.let { thread ->
                        item(key = "thread-summary") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    thread.subject,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "楼主 " + thread.author.name +
                                        "  ·  回复 " + thread.replyCount +
                                        "  ·  查看 " + thread.viewCount,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    itemsIndexed(state.posts, key = { _, post -> post.id }) { index, post ->
                        ForumPostCard(
                            post = post,
                            alternate = index % 2 == 1,
                            cookie = cookie,
                            onOpenLink = onOpenLink
                        )
                        if (index >= state.posts.lastIndex - 3) {
                            LaunchedEffect(state.page, state.posts.size) {
                                forumThreadVM.loadMore()
                            }
                        }
                    }
                    if (state.isLoadingMore) {
                        item(key = "loading-more-posts") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "thread-page-error") {
                            Text(
                                error,
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumPostCard(
    post: ForumPost,
    alternate: Boolean,
    cookie: String,
    onOpenLink: (String) -> Unit
) {
    val background = if (alternate) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (post.author.avatarUrl != null) {
                    AsyncImage(
                        model = post.author.avatarUrl,
                        contentDescription = post.author.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            post.author.name.take(1).ifBlank { "?" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    post.author.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (post.createdAt.isNotBlank()) {
                    Text(
                        post.createdAt,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (post.isOriginalPost) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (post.isOriginalPost) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    if (post.isOriginalPost) "楼主" else post.floor.toString() + " 楼",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (post.blocks.isEmpty() && post.attachments.isEmpty()) {
            Text(
                "该楼层没有可显示的内容",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            post.blocks.forEach { block ->
                when (block) {
                    is ForumPostBlock.Text -> ForumPostText(block, onOpenLink)
                    is ForumPostBlock.Image -> ForumPostImage(
                        url = block.url,
                        description = block.description,
                        cookie = cookie
                    )
                }
            }
            val inlineImages = post.blocks.filterIsInstance<ForumPostBlock.Image>()
                .mapTo(hashSetOf(), ForumPostBlock.Image::url)
            post.attachments.forEach { attachment ->
                if (attachment.isImage && attachment.url !in inlineImages) {
                    ForumPostImage(attachment.url, attachment.filename, cookie)
                } else if (!attachment.isImage) {
                    ForumAttachmentRow(attachment, onOpenLink)
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Suppress("DEPRECATION")
@Composable
private fun ForumPostText(block: ForumPostBlock.Text, onOpenLink: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(block, linkColor) {
        buildAnnotatedString {
            block.parts.forEach { part ->
                if (part.url == null) {
                    append(part.text)
                } else {
                    pushStringAnnotation("URL", part.url)
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    append(part.text)
                    pop()
                    pop()
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 25.sp
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.item
                ?.let(onOpenLink)
        }
    )
}

@Composable
private fun ForumPostImage(url: String, description: String, cookie: String) {
    val context = LocalContext.current
    val request = remember(context, url, cookie) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .addHeader("User-Agent", RequestConfig.UA)
            .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = description.ifBlank { "帖子图片" },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            loading = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "图片加载失败",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}

@Composable
private fun ForumAttachmentRow(
    attachment: ForumPostAttachment,
    onOpenLink: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpenLink(attachment.url) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                attachment.filename,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

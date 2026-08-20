package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.space.BlogContentBlock
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboDangerColor
import org.shirakawatyu.yamibo.novel.ui.vm.BlogDetailVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionSnapshot
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarLazyListScroll

@Composable
fun NativeBlogDetailPage(
    navController: NavController,
    url: String,
    onOpenWeb: (String) -> Unit,
    bottomBarPadding: Dp = 0.dp
) {
    val vm: BlogDetailVM = viewModel(
        key = "BlogDetail-$url",
        factory = BlogDetailVM.Factory(url)
    )
    val detail by vm.detail
    val isLoading by vm.isLoading
    val error by vm.error
    val reactions by vm.reactionSnapshot
    val reactionBusy by vm.reactionBusy
    val reactionMessage by vm.reactionMessage
    val commentBusy by vm.commentBusy
    val colors = yamiboComponentColors()
    val listState = rememberLazyListState()
    val bottomNavBarVM: BottomNavBarVM = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
    ObserveBottomBarLazyListScroll(listState, bottomNavBarVM)
    var editorRequest by remember { mutableStateOf<BlogCommentEditorRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<org.shirakawatyu.yamibo.novel.bean.space.BlogComment?>(null) }

    // 隐藏底栏时移除其 52dp 占位，但保留系统导航栏安全区。
    val systemNavigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = if (bottomNavBarVM.bottomBarScrollSuppressed) {
        systemNavigationPadding
    } else {
        bottomBarPadding
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = effectiveBottomPadding)
    ) {
        Surface(color = colors.topBarContainer, contentColor = colors.topBarContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.topBarContent)
                }
                Text(
                    text = detail?.title ?: "日志",
                    color = colors.topBarContent,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val loaded = detail
                if (loaded != null && loaded.favoriteUrl.isNotBlank()) {
                    IconButton(onClick = { onOpenWeb(loaded.favoriteUrl) }) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = colors.topBarContent
                        )
                    }
                }
                if (loaded != null && loaded.shareUrl.isNotBlank()) {
                    IconButton(onClick = { onOpenWeb(loaded.shareUrl) }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "分享",
                            tint = colors.topBarContent
                        )
                    }
                }
                if (loaded != null && loaded.editUrl.isNotBlank()) {
                    IconButton(onClick = { onOpenWeb(loaded.editUrl) }) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = "编辑",
                            tint = colors.topBarContent
                        )
                    }
                }
            }
        }

        when {
            isLoading && detail == null -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            detail == null -> {
                YamiboLoadError(
                    title = error ?: "日志无法打开",
                    onRetry = vm::load,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
            else -> {
                val current = detail ?: return@Column
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        BlogDetailHeader(current)
                    }
                    items(current.blocks) { block ->
                        BlogContentBlockView(block)
                    }
                    item {
                        BlogReactionPanel(
                            snapshot = reactions,
                            busy = reactionBusy,
                            message = reactionMessage,
                            disabled = current.ownerUid == GlobalData.currentUid,
                            onReact = vm::react
                        )
                    }
                    item {
                        Text(
                            text = "日志评论 (${current.comments.size})",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    if (current.comments.isEmpty()) {
                        item {
                            Text(
                                text = "暂无评论",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        items(current.comments, key = { it.id }) { comment ->
                            BlogCommentCard(
                                comment = comment,
                                onReply = comment.replyUrl.takeIf(String::isNotBlank)?.let {
                                    { editorRequest = BlogCommentEditorRequest(comment, edit = false) }
                                },
                                onEdit = comment.editUrl.takeIf(String::isNotBlank)?.let {
                                    { editorRequest = BlogCommentEditorRequest(comment, edit = true) }
                                },
                                onDelete = comment.deleteUrl.takeIf(String::isNotBlank)?.let {
                                    { deleteTarget = comment }
                                }
                            )
                        }
                    }
                }
            }
        }
        if (detail != null) {
            BlogReplyBar(
                enabled = !commentBusy,
                onClick = {
                    if (GlobalData.currentUid.isBlank()) {
                        YamiboToast.show(message = "请先登录后再发表评论")
                    } else {
                        editorRequest = BlogCommentEditorRequest(comment = null, edit = false)
                    }
                }
            )
        }
    }

    editorRequest?.let { request ->
        BlogCommentEditorDialog(
            request = request,
            busy = commentBusy,
            onDismiss = { if (!commentBusy) editorRequest = null },
            onSubmit = { message ->
                val callback: (String, Boolean) -> Unit = { result, success ->
                    YamiboToast.show(message = result)
                    if (success) editorRequest = null
                }
                when {
                    request.comment == null -> vm.submitComment(message, callback)
                    request.edit -> vm.editComment(request.comment, message, callback)
                    else -> vm.replyComment(request.comment, message, callback)
                }
            }
        )
    }

    deleteTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { if (!commentBusy) deleteTarget = null },
            title = { Text("删除评论") },
            text = {
                Text(
                    "确定删除 ${comment.authorName.ifBlank { "这条" }} 的评论吗？",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !commentBusy,
                    onClick = {
                        deleteTarget = null
                        vm.deleteComment(comment) { result, _ -> YamiboToast.show(message = result) }
                    }
                ) { Text("删除", color = yamiboDangerColor()) }
            },
            dismissButton = {
                TextButton(enabled = !commentBusy, onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BlogDetailHeader(detail: org.shirakawatyu.yamibo.novel.bean.space.BlogDetail) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.category.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        detail.category,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
            Text(
                detail.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpaceAvatar(detail.authorAvatarUrl, 34)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(detail.authorName, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text(detail.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "浏览 ${detail.viewCount.ifBlank { "0" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    "评论 ${detail.comments.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BlogContentBlockView(block: BlogContentBlock) {
    when (block) {
        is BlogContentBlock.Text -> Text(
            text = block.value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            lineHeight = 27.sp
        )
        is BlogContentBlock.Quote -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = block.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        is BlogContentBlock.Image -> AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(block.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BlogReactionPanel(
    snapshot: BlogReactionSnapshot?,
    busy: Boolean,
    message: String?,
    disabled: Boolean,
    onReact: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(12.dp)) {
            if (snapshot == null) {
                Text(
                    if (busy) "正在提交表态…" else "正在加载票数…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                val maximum = snapshot.options.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    snapshot.options.forEach { option ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(84.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        option.count.toString(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(
                                                (option.count.toFloat() / maximum * 60f)
                                                    .coerceAtLeast(if (option.count > 0) 8f else 3f)
                                                    .dp
                                            ),
                                        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {}
                                }
                            }
                            TextButton(
                                enabled = !disabled && !busy,
                                onClick = { onReact(option.clickId) }
                            ) {
                                Text(
                                    option.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            when {
                busy -> Text(
                    "正在提交表态…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                !message.isNullOrBlank() -> Text(
                    message,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                disabled -> Text(
                    "自己的日志仅可查看表态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                else -> Text(
                    "点击选项即可给帖主表态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class BlogCommentEditorRequest(
    val comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment?,
    val edit: Boolean
)

@Composable
private fun BlogReplyBar(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Button(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("回复日志")
        }
    }
}

@Composable
private fun BlogCommentEditorDialog(
    request: BlogCommentEditorRequest,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var message by remember(request) {
        mutableStateOf(request.comment?.content.orEmpty())
    }
    val title = when {
        request.comment == null -> "发表评论"
        request.edit -> "编辑评论"
        else -> "回复评论"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("请输入内容") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 240.dp)
                    .imePadding(),
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

@Composable
private fun BlogCommentCard(
    comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment,
    onReply: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpaceAvatar(comment.avatarUrl, 32)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        comment.authorName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        comment.time,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        onReply?.let {
                            TextButton(
                                onClick = it,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("回复", fontSize = 12.sp) }
                        }
                        onEdit?.let {
                            TextButton(
                                onClick = it,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("编辑", fontSize = 12.sp) }
                        }
                        onDelete?.let {
                            TextButton(
                                onClick = it,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    "删除",
                                    color = yamiboDangerColor(),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(comment.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 23.sp)
        }
    }
}

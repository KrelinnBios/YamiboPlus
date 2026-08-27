package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.FavoriteBorder
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.space.BlogContentBlock
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.component.YamiboDialogSurface
import org.shirakawatyu.yamibo.novel.ui.component.YamiboTextEditorDialog
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboDangerColor
import org.shirakawatyu.yamibo.novel.ui.vm.BlogDetailVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionSnapshot
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarLazyListScroll

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    var actionMenuVisible by remember { mutableStateOf(false) }

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
                        BlogDetailHeader(
                            detail = current,
                            onLongClick = { actionMenuVisible = true }
                        )
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

    val loadedDetail = detail
    if (actionMenuVisible && loadedDetail != null) {
        BlogDetailActionMenu(
            detail = loadedDetail,
            onDismiss = { actionMenuVisible = false },
            onAction = { actionUrl ->
                actionMenuVisible = false
                onOpenWeb(actionUrl)
            }
        )
    }
}

@Composable
private fun BlogDetailHeader(
    detail: org.shirakawatyu.yamibo.novel.bean.space.BlogDetail,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.visibilityText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            detail.visibilityText,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
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
            SpaceAvatar(detail.authorAvatarUrl, 42)
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.height(42.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(detail.authorName, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text(detail.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.height(42.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
private fun BlogDetailActionMenu(
    detail: org.shirakawatyu.yamibo.novel.bean.space.BlogDetail,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
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
                Text(
                    detail.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (detail.category.isNotBlank()) {
                    Text(
                        "分类：${detail.category}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (detail.visibilityText.isNotBlank()) {
                    Text(
                        "权限：" + detail.visibilityText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (detail.tags.isNotEmpty()) {
                    Text(
                        "标签：${detail.tags.joinToString("、")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                BlogDetailMenuAction("置顶", Icons.Filled.PushPin, detail.stickUrl, onAction, iconRotation = 35f)
                BlogDetailMenuAction("编辑", Icons.Filled.EditNote, detail.editUrl, onAction)
                BlogDetailMenuAction("删除", Icons.Filled.Delete, detail.deleteUrl, onAction, danger = true)
            }
        }
    }
}

@Composable
private fun BlogDetailMenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    url: String,
    onAction: (String) -> Unit,
    danger: Boolean = false,
    iconRotation: Float = 0f
) {
    val color = if (danger) yamiboDangerColor() else MaterialTheme.colorScheme.primary
    TextButton(
        enabled = url.isNotBlank(),
        onClick = { onAction(url) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).rotate(iconRotation), tint = color)
            Spacer(Modifier.width(12.dp))
            Text(label, color = color)
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
    val title = when {
        request.comment == null -> "发表评论"
        request.edit -> "编辑评论"
        else -> "回复评论"
    }
    YamiboTextEditorDialog(
        title = title,
        subtitle = when {
            request.comment == null -> "评论会显示在这篇日志下方"
            request.edit -> "修改后将更新原评论内容"
            else -> "回复 " + request.comment.authorName
        },
        placeholder = if (request.edit) "请输入修改后的评论" else "请输入评论内容",
        confirmLabel = if (request.edit) "保存修改" else "发表",
        initialText = request.comment?.content.orEmpty(),
        busy = busy,
        minLines = 4,
        maxLines = 8,
        onDismiss = onDismiss,
        onConfirm = onSubmit
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
                SpaceAvatar(
                    url = comment.avatarUrl,
                    size = 36,
                    modifier = Modifier.clip(CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.authorName,
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (comment.time.isNotBlank()) {
                        Text(
                            text = " · " + comment.time,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
            Spacer(Modifier.height(7.dp))
            if (comment.quotedContent.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (comment.quotedAuthor.isBlank()) {
                                "回复内容"
                            } else {
                                "回复 ${comment.quotedAuthor}"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = comment.quotedContent,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = comment.content,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 23.sp
            )
        }
    }
}

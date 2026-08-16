package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.shirakawatyu.yamibo.novel.ui.vm.BlogDetailVM
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionSnapshot

@Composable
fun NativeBlogDetailPage(
    navController: NavController,
    url: String,
    onOpenWeb: (String) -> Unit
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
    val colors = yamiboComponentColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            }
        }

        when {
            isLoading && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            detail == null -> {
                YamiboLoadError(
                    title = error ?: "日志无法打开",
                    onRetry = vm::load
                )
            }
            else -> {
                val current = detail ?: return@Column
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
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
                        BlogActionRow(current, onOpenWeb)
                    }
                    item {
                        Text(
                            text = "日志评论 (${current.commentCount.ifBlank { current.comments.size.toString() }})",
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
                            BlogCommentCard(comment)
                        }
                    }
                }
            }
        }
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
            Text(detail.authorName, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(detail.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "浏览 ${detail.viewCount.ifBlank { "0" }} · 评论 ${detail.commentCount.ifBlank { "0" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
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
            Text("表态", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (snapshot == null) {
                Text("表态数据暂时无法加载", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    snapshot.options.forEach { option ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(
                                enabled = !disabled && !busy,
                                onClick = { onReact(option.clickId) }
                            ) { Text(option.label) }
                            Text(
                                option.count.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            if (disabled) {
                Text("日志作者不能给自己的日志表态", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (!message.isNullOrBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BlogActionRow(
    detail: org.shirakawatyu.yamibo.novel.bean.space.BlogDetail,
    onOpenWeb: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        listOf(
            "收藏" to detail.favoriteUrl,
            "分享" to detail.shareUrl,
            "邀请" to detail.inviteUrl,
            "编辑" to detail.editUrl,
            "删除" to detail.deleteUrl
        ).filter { it.second.isNotBlank() }.forEach { (label, url) ->
            TextButton(onClick = { onOpenWeb(url) }) { Text(label) }
        }
    }
}

@Composable
private fun BlogCommentCard(comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpaceAvatar(comment.avatarUrl, 32)
                Spacer(Modifier.width(8.dp))
                Text(comment.authorName, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(comment.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(comment.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 23.sp)
        }
    }
}

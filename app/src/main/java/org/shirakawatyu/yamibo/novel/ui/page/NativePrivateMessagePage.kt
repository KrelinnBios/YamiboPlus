package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageBubble
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.vm.PrivateMessageVM

@Composable
fun NativePrivateMessagePage(
    navController: NavController,
    url: String
) {
    val vm: PrivateMessageVM = viewModel(
        key = "PrivateMessage-$url",
        factory = PrivateMessageVM.Factory(url)
    )
    val conversation by vm.conversation
    val isLoading by vm.isLoading
    val error by vm.error
    val isSending by vm.isSending
    val colors = yamiboComponentColors()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(conversation?.messages) {
        val count = conversation?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

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
                    text = conversation?.title?.ifBlank { "私信" } ?: "私信",
                    color = colors.topBarContent,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading && conversation == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            conversation == null -> {
                YamiboLoadError(title = error ?: "私信无法打开", onRetry = { vm.load() })
            }
            else -> {
                val current = conversation ?: return@Column
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    current.previousUrl?.let { previous ->
                        item(key = "previous") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { vm.loadPage(previous) }) {
                                    Text("查看更早的消息")
                                }
                            }
                        }
                    }
                    items(
                        items = current.messages,
                        key = { message -> "pm-${current.messages.indexOf(message)}-${message.time}-${message.content.hashCode()}" }
                    ) { bubble ->
                        MessageBubble(bubble)
                    }
                    current.nextUrl?.let { next ->
                        item(key = "next") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { vm.loadPage(next) }) {
                                    Text("查看更晚的消息")
                                }
                            }
                        }
                    }
                }
                error?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("请输入内容...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        TextButton(
                            enabled = input.isNotBlank() && !isSending,
                            onClick = {
                                val text = input
                                input = ""
                                vm.send(text)
                            }
                        ) {
                            Text(
                                if (isSending) "发送中" else "发送",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(bubble: PrivateMessageBubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!bubble.isSelf) {
            SpaceAvatar(bubble.avatarUrl, 34)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (bubble.isSelf) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (bubble.isSelf) 14.dp else 4.dp,
                    topEnd = if (bubble.isSelf) 4.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                color = if (bubble.isSelf) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    bubble.content,
                    color = if (bubble.isSelf) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                bubble.time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        if (bubble.isSelf) {
            Spacer(Modifier.width(8.dp))
            SpaceAvatar(bubble.avatarUrl, 34)
        }
    }
}

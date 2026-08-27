package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        key = "PrivateMessage-" + url,
        factory = PrivateMessageVM.Factory(url)
    )
    val conversation by vm.conversation
    val isLoading by vm.isLoading
    val isLoadingMore by vm.isLoadingMore
    val error by vm.error
    val isSending by vm.isSending
    val colors = yamiboComponentColors()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val peerAvatar = conversation?.messages
        ?.firstOrNull { !it.isSelf && !it.avatarUrl.isNullOrBlank() }
        ?.avatarUrl

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
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.topBarContent
                    )
                }
                if (!peerAvatar.isNullOrBlank()) {
                    SpaceAvatar(peerAvatar, 32)
                    Spacer(Modifier.width(9.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation?.title?.ifBlank { "私信" } ?: "私信",
                        color = colors.topBarContent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (conversation != null) {
                        Text(
                            text = "私信",
                            color = colors.topBarContent.copy(alpha = 0.68f),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when {
            isLoading && conversation == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            conversation == null -> {
                YamiboLoadError(
                    title = error ?: "私信无法打开",
                    onRetry = { vm.load() }
                )
            }
            else -> {
                val current = conversation ?: return@Column
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 14.dp,
                        bottom = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoading) {
                        item(key = "page-loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    if (current.messages.isEmpty() && !isLoading) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无私信记录",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = current.messages,
                        key = { index, message ->
                            "pm-" + index + "-" + message.time + "-" + message.content.hashCode()
                        }
                    ) { index, bubble ->
                        MessageBubble(bubble)
                        val nextUrl = current.nextUrl
                        if (index >= current.messages.lastIndex - 4 && nextUrl != null) {
                            LaunchedEffect(nextUrl) {
                                vm.loadMore(nextUrl)
                            }
                        }
                    }
                    if (isLoadingMore) {
                        item(key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                error?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                enabled = !isSending,
                                placeholder = { Text("发送私信") },
                                minLines = 1,
                                maxLines = 5,
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            val canSend = input.isNotBlank() && !isSending
                            FilledIconButton(
                                enabled = canSend || isSending,
                                onClick = {
                                    if (!isSending) {
                                        val draft = input
                                        vm.send(draft) {
                                            if (input == draft) input = ""
                                        }
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "发送"
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
private fun MessageBubble(bubble: PrivateMessageBubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!bubble.isSelf) {
            SpaceAvatar(bubble.avatarUrl, 36)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (bubble.isSelf) Alignment.End else Alignment.Start
        ) {
            if (!bubble.isSelf && bubble.authorName.isNotBlank()) {
                Text(
                    text = bubble.authorName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (bubble.isSelf) 18.dp else 5.dp,
                    topEnd = if (bubble.isSelf) 5.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = if (bubble.isSelf) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = if (bubble.isSelf) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
            ) {
                SelectionContainer {
                    Text(
                        text = bubble.content,
                        color = if (bubble.isSelf) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                    )
                }
            }
            if (bubble.time.isNotBlank()) {
                Text(
                    text = bubble.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                )
            }
        }
    }
}

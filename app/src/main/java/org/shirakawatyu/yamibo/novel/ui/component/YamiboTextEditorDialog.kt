package org.shirakawatyu.yamibo.novel.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 回复、点评与评论共用的原生编辑弹窗。
 *
 * 业务页面只提供文案和长度规则；容器、输入框、计数、表情与操作按钮保持一致。
 */
@Composable
fun YamiboTextEditorDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialText: String = "",
    subtitle: String? = null,
    busy: Boolean = false,
    minimumLength: Int = 1,
    maximumLength: Int? = null,
    minLines: Int = 4,
    maxLines: Int = 9,
    showForumSmilies: Boolean = false
) {
    var value by remember(title, initialText) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }
    var showSmilies by remember(title) { mutableStateOf(false) }
    val trimmedLength = value.text.trim().length
    val withinMaximum = maximumLength == null || value.text.length <= maximumLength
    val canConfirm = !busy && trimmedLength >= minimumLength && withinMaximum
    val countText = when {
        maximumLength != null -> trimmedLength.toString() + " / " + maximumLength
        minimumLength > 1 -> trimmedLength.toString() + " / 至少 " + minimumLength + " 字"
        else -> trimmedLength.toString() + " 字"
    }
    val countColor = if (
        value.text.isNotBlank() && (trimmedLength < minimumLength || !withinMaximum)
    ) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy
        )
    ) {
        YamiboDialogSurface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.takeIf(String::isNotBlank)?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        if (maximumLength == null || next.text.length <= maximumLength) {
                            value = next
                        }
                    },
                    enabled = !busy,
                    placeholder = { Text(placeholder) },
                    minLines = minLines,
                    maxLines = maxLines,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp, max = 320.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showForumSmilies) {
                        TextButton(
                            enabled = !busy,
                            onClick = { showSmilies = !showSmilies },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(if (showSmilies) "收起表情" else "论坛表情")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = countText,
                        color = countColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (showForumSmilies && showSmilies) {
                    ForumSmileyGrid(
                        enabled = !busy,
                        onSelect = { token ->
                            val start = value.selection.min
                            val end = value.selection.max
                            val newLength = value.text.length - (end - start) + token.length
                            if (maximumLength == null || newLength <= maximumLength) {
                                val newText = value.text.replaceRange(start, end, token)
                                value = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(start + token.length)
                                )
                            }
                        }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = !busy, onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canConfirm || busy,
                        onClick = {
                            if (!busy) onConfirm(value.text.trim())
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("提交中")
                        } else {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumSmileyGrid(
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..30).chunked(10).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { id ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(
                                    "https://bbs.yamibo.com/static/image/smiley/comcom/" +
                                        id + ".gif"
                                )
                                .crossfade(false)
                                .build(),
                            contentDescription = "论坛表情 " + id,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(enabled = enabled) {
                                    onSelect("[em:" + id + ":]")
                                }
                        )
                    }
                }
            }
        }
    }
}

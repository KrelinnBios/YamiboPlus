package org.shirakawatyu.yamibo.novel.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties

val YamiboDialogShape: Shape = RoundedCornerShape(28.dp)

@Composable
fun yamiboDialogContainerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        scheme.surfaceContainerHigh
    } else {
        scheme.surface
    }
}

/** 用于输入框、菜单等需要自定义内容的弹窗容器。 */
@Composable
fun YamiboDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = YamiboDialogShape,
        color = yamiboDialogContainerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        content = content
    )
}

/**
 * 应用统一的标准弹窗：浅色主题使用浅色 surface，标题和操作统一跟随主题主色。
 * 旧调用保留了部分 Material AlertDialog 颜色参数，这里有意忽略它们，避免各页面重新分叉。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun YamiboAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    containerColor: Color? = null,
    iconContentColor: Color? = null,
    titleContentColor: Color? = null,
    textContentColor: Color? = null,
    shape: Shape? = null,
    tonalElevation: Dp? = null
) {
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        shape = YamiboDialogShape,
        containerColor = yamiboDialogContainerColor(),
        iconContentColor = scheme.primary,
        titleContentColor = scheme.primary,
        textContentColor = scheme.onSurface,
        tonalElevation = 6.dp,
        icon = icon,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

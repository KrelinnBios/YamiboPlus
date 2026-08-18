package org.shirakawatyu.yamibo.novel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class YamiboComponentColors(
    val topBarContainer: Color,
    val topBarContent: Color,
    val contentBackground: Color,
    val baseRow: Color,
    val alternateRow: Color,
    val bottomBarContainer: Color,
    val destructiveIcon: Color
)

fun yamiboComponentColors(scheme: ColorScheme): YamiboComponentColors {
    val isDark = scheme.background.luminance() < 0.5f
    return YamiboComponentColors(
        topBarContainer = if (isDark) scheme.primaryContainer else scheme.primary,
        topBarContent = if (isDark) scheme.onPrimaryContainer else scheme.onPrimary,
        contentBackground = scheme.background,
        baseRow = scheme.surfaceContainerLow,
        alternateRow = scheme.surfaceContainerHigh,
        bottomBarContainer = if (isDark) scheme.surfaceContainerHighest else scheme.surfaceContainer,
        destructiveIcon = scheme.primary
    )
}

@Composable
fun yamiboComponentColors(): YamiboComponentColors =
    yamiboComponentColors(MaterialTheme.colorScheme)

/**
 * 聚焦态高亮色：直接复用主题高亮色（primary），避免在棕色 / 暖色系主题下
 * HSL 提亮导致色相漂移变红。聚焦描边本身通过加粗 + alpha 增强存在感。
 */
@Composable
fun yamiboFocusBorderColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun yamiboSwitchColors(): SwitchColors {
    val colors = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onPrimary,
        checkedTrackColor = colors.primary,
        checkedBorderColor = colors.primary,
        uncheckedThumbColor = colors.onSurfaceVariant,
        uncheckedTrackColor = colors.surfaceVariant,
        uncheckedBorderColor = colors.outline,
        disabledCheckedThumbColor = colors.onPrimary.copy(alpha = 0.7f),
        disabledCheckedTrackColor = colors.primary.copy(alpha = 0.4f),
        disabledCheckedBorderColor = colors.primary.copy(alpha = 0.4f),
        disabledUncheckedThumbColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
        disabledUncheckedTrackColor = colors.surfaceVariant.copy(alpha = 0.5f),
        disabledUncheckedBorderColor = colors.outline.copy(alpha = 0.5f)
    )
}

@Composable
fun yamiboSliderColors(): SliderColors {
    val colors = MaterialTheme.colorScheme
    return SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        activeTickColor = colors.primary,
        inactiveTrackColor = colors.primary.copy(alpha = 0.24f),
        inactiveTickColor = colors.primary.copy(alpha = 0.24f),
        disabledThumbColor = colors.onSurface.copy(alpha = 0.38f),
        disabledActiveTrackColor = colors.onSurface.copy(alpha = 0.38f),
        disabledInactiveTrackColor = colors.onSurface.copy(alpha = 0.12f),
        disabledActiveTickColor = colors.surface,
        disabledInactiveTickColor = colors.onSurface.copy(alpha = 0.12f)
    )
}

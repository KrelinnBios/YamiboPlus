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
import kotlin.math.abs

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
 * 弹窗/菜单里破坏性操作（删除、清空、移除等）统一使用的警示色。
 * 大部分主题直接复用主题自带 error（浅色 #BA1A1A / 深色 #FFB4AB），既醒目又贴合主题。
 * 但红色系主题（经典/绯红/樱粉/蜜橙/品红等）的 primary 本身就是红/橙/粉，色相与 error
 * 几乎重合——尤其深色下 primary 与 error 都是 #FFB4xx 的浅红，删除按钮会和普通按钮
 * 完全分不开。此时改用一个更深、更饱和的「信号红」：与主色拉开差距（看得出是危险操作），
 * 同时保持红色系暖调，贴合当前主题的明暗基调。
 */
fun yamiboDangerColor(scheme: ColorScheme): Color {
    val error = scheme.error
    val primaryHue = scheme.primary.rgbHueDegrees()
    val errorHue = error.rgbHueDegrees()
    // 主色或 error 是纯灰/黑白（如月白、墨黑）时没有色相概念，直接沿用 error。
    if (primaryHue < 0f || errorHue < 0f) return error
    // 主色与 error 色相距离足够大（蓝/绿/紫/黄等主题）时，error 本就醒目，直接沿用。
    if (hueDistanceDegrees(primaryHue, errorHue) >= 30f) return error
    // 红色系主题：深色下用偏深的饱和红、浅色下用更纯的深红，既区分主色又贴合暖调。
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFE5484D) else Color(0xFFC62828)
}

@Composable
fun yamiboDangerColor(): Color = yamiboDangerColor(MaterialTheme.colorScheme)

private fun Color.rgbHueDegrees(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return -1f
    var hue = when (max) {
        red -> ((green - blue) / delta) % 6f
        green -> ((blue - red) / delta) + 2f
        else -> ((red - green) / delta) + 4f
    } * 60f
    if (hue < 0f) hue += 360f
    return hue
}

private fun hueDistanceDegrees(a: Float, b: Float): Float {
    val diff = abs(a - b)
    return minOf(diff, 360f - diff)
}

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

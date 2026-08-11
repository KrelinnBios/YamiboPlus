package org.shirakawatyu.yamibo.novel.util

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import org.shirakawatyu.yamibo.novel.global.GlobalData

interface ThemeColors {
    val statusBar: Color
    val navBar: Color
    val background: Color
    val surface: Color
    val surfaceVariant: Color
    val primary: Color
    val onPrimary: Color
    val onBackground: Color
    val onSurface: Color
    val onSurfaceVariant: Color
    val outline: Color
    val tertiary: Color
    val onSecondary: Color
}

data class DarkThemeColors(
    override val statusBar: Color,
    override val navBar: Color,
    override val background: Color,
    override val surface: Color,
    override val surfaceVariant: Color,
    override val primary: Color,
    override val onPrimary: Color,
    override val onBackground: Color,
    override val onSurface: Color,
    override val onSurfaceVariant: Color,
    override val outline: Color,
    override val tertiary: Color,
    override val onSecondary: Color,
) : ThemeColors {
    fun toDarkColorScheme() = darkColorScheme(
        primary = primary,
        primaryContainer = surfaceVariant,
        secondary = surface,
        secondaryContainer = surfaceVariant,
        tertiary = tertiary,
        background = background,
        surface = surface,
        // 必须显式映射 surfaceVariant：否则它会回落到 Material3 默认的灰色，与蓝黑主题不搭
        // （搜索框背景、未开启开关 yamiboSwitchColors.uncheckedTrackColor 等都用它）。统一为面板色 #223247。
        surfaceVariant = surfaceVariant,
        onPrimary = onPrimary,
        onPrimaryContainer = onSecondary,
        onSecondary = onSecondary,
        onSecondaryContainer = onSecondary,
        onTertiary = onSecondary,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
    )

    companion object {
        val CLASSIC = DarkThemeColors(
            statusBar = Color(0xFF121B27),
            navBar = Color(0xFF121B27),
            background = Color(0xFF0D141D),
            surface = Color(0xFF182332),
            surfaceVariant = Color(0xFF223247),
            primary = Color(0xFF4EA1FF),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFFD7E3F1),
            onSurface = Color(0xFFD7E3F1),
            onSurfaceVariant = Color(0xFF95ACC4),
            outline = Color(0xFF3C5677),
            tertiary = Color(0xFF223247),
            onSecondary = Color(0xFFD7E3F1),
        )
    }
}

data class LightThemeColors(
    override val statusBar: Color,
    override val navBar: Color,
    override val background: Color,
    override val surface: Color,
    override val surfaceVariant: Color,
    override val primary: Color,
    override val onPrimary: Color,
    override val onBackground: Color,
    override val onSurface: Color,
    override val onSurfaceVariant: Color,
    override val outline: Color,
    override val tertiary: Color,
    override val onSecondary: Color,
) : ThemeColors {
    companion object {
        val MODERN_WHITE = LightThemeColors(
            statusBar = Color(0xFF64748B),
            navBar = Color(0xFF64748B),
            background = Color(0xFFF7F8FA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEEF2F6),
            primary = Color(0xFF64748B),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827),
            onSurfaceVariant = Color(0xFF4B5563),
            outline = Color(0xFFE2E8F0),
            tertiary = Color(0xFFEEF2F6),
            onSecondary = Color(0xFF111827),
        )
    }
}

@Composable
fun currentDarkThemeColors(): DarkThemeColors? {
    val isDark by GlobalData.isDarkMode.collectAsState()
    return if (isDark) DarkThemeColors.CLASSIC else null
}

@Composable
fun currentLightThemeColors(): LightThemeColors? {
    return null
}

@Composable
fun darkThemeColor(light: Color, pick: ThemeColors.() -> Color): Color {
    val darkTheme = currentDarkThemeColors()
    val lightTheme = currentLightThemeColors()
    return when {
        darkTheme != null -> pick(darkTheme)
        lightTheme != null -> pick(lightTheme)
        else -> light
    }
}

@Composable
fun darkModeColor(light: Color, dark: Color): Color {
    val isDark by GlobalData.isDarkMode.collectAsState()
    return if (isDark) dark else light
}

package org.shirakawatyu.yamibo.novel.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.util.DarkThemeColors

private val LightColorScheme = lightColorScheme(
    primary = RedLight,
    onPrimary = YellowLightLight,
    primaryContainer = YamiboColors.onSurface,
    onPrimaryContainer = RedLight,
    secondary = YamiboColors.secondary,
    onSecondary = YamiboColors.onSecondary,
    secondaryContainer = YellowLightDark,
    onSecondaryContainer = RedLight,
    tertiary = YellowLightLight,
    onTertiary = RedLight,
    tertiaryContainer = YamiboColors.onSurface,
    onTertiaryContainer = RedLight,
    background = YellowLightDark,
    onBackground = RedLight,
    surface = YellowLightLight,
    onSurface = RedLight,
    surfaceVariant = YamiboColors.onSurface,
    onSurfaceVariant = RedLight,
    outline = YamiboColors.secondary.copy(alpha = 0.55f),
    outlineVariant = RedLight.copy(alpha = 0.18f),
    inverseSurface = RedLight,
    inverseOnSurface = YellowLightLight,
    inversePrimary = YamiboColors.onSecondary,
    surfaceTint = RedLight
)

@Composable
fun _300文学Theme(
    content: @Composable () -> Unit
) {
    val palette by GlobalData.themePalette.collectAsState()
    val themeMode by GlobalData.themeMode.collectAsState()
    val pureBlack by GlobalData.pureBlackMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val appTheme = palette.resolve(themeMode.resolveDark(systemDark))
    val colorScheme = appTheme.effectiveScheme(pureBlack)
    val view = LocalView.current
    SideEffect {
        if (GlobalData.appTheme.value != appTheme) {
            GlobalData.appTheme.value = appTheme
        }
        if (GlobalData.isDarkMode.value != appTheme.isDark) {
            GlobalData.isDarkMode.value = appTheme.isDark
        }
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            val backgroundArgb = colorScheme.background.toArgb()
            window.statusBarColor = backgroundArgb
            window.navigationBarColor = backgroundArgb
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !appTheme.isDark
                isAppearanceLightNavigationBars = !appTheme.isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )

}

/**
 * 阅读器浅色模式
 */
private val ReaderLightColorScheme = lightColorScheme(
    primary = RedLight,
    onPrimary = YellowLightLight,
    primaryContainer = YamiboColors.onSurface,
    onPrimaryContainer = RedLight,
    secondary = YamiboColors.secondary,
    onSecondary = YamiboColors.onSecondary,
    secondaryContainer = YellowLightDark,
    onSecondaryContainer = RedLight,
    tertiary = YellowLightLight,
    onTertiary = RedLight,
    tertiaryContainer = YamiboColors.onSurface,
    onTertiaryContainer = RedLight,
    background = YellowLightDark,
    onBackground = Color.Black,
    surface = YellowLightLight,
    onSurface = RedLight,
    surfaceVariant = YellowLightLight,
    onSurfaceVariant = RedLight,
    outline = YamiboColors.secondary.copy(alpha = 0.55f),
    outlineVariant = RedLight.copy(alpha = 0.18f),
    inverseSurface = RedLight,
    inverseOnSurface = YellowLightLight,
    inversePrimary = YamiboColors.onSecondary,
    surfaceTint = RedLight
)

/**
 * 应用于阅读器页面的专用主题
 * */
@Composable
fun ReaderTheme(
    content: @Composable () -> Unit
) {
    val appTheme by GlobalData.appTheme.collectAsState()
    val pureBlack by GlobalData.pureBlackMode.collectAsState()
    MaterialTheme(
        // 阅读器沿用应用当前主题，避免正文、顶部工具栏和底部控制面板
        // 仍固定使用经典米黄色/蓝黑色。
        colorScheme = appTheme.effectiveScheme(pureBlack),
        typography = Typography,
        content = content
    )
}

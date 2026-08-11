package org.shirakawatyu.yamibo.novel.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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
    val isDark by GlobalData.isDarkMode.collectAsState()
    val darkColors = DarkThemeColors.CLASSIC
    val colorScheme = if (isDark) darkColors.toDarkColorScheme() else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.navigationBarColor = if (isDark) {
                darkColors.navBar.toArgb()
            } else {
                YamiboColors.onSurface.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
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
    val isDark by GlobalData.isDarkMode.collectAsState()
    MaterialTheme(
        colorScheme = if (isDark) {
            DarkThemeColors.CLASSIC.toDarkColorScheme()
        } else {
            ReaderLightColorScheme
        },
        typography = Typography,
        content = content
    )
}

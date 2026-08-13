package org.shirakawatyu.yamibo.novel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 应用主题枚举。移植自 NeoDBLite 的配色，并保留 YamiboReaderLite 的经典浅色主题作为默认。
 */
enum class YamiboAppTheme(val label: String, val isDark: Boolean, val scheme: ColorScheme) {

    CLASSIC_LIGHT(
        label = "经典·浅",
        isDark = false,
        scheme = lightColorScheme(
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
            surfaceDim = Color(0xFFE6D9B8),
            surfaceBright = YellowLightLight,
            surfaceContainerLowest = Color(0xFFFFF8DE),
            surfaceContainerLow = Color(0xFFFAF0D1),
            surfaceContainer = Color(0xFFF4E8C3),
            surfaceContainerHigh = Color(0xFFEEDFB6),
            surfaceContainerHighest = Color(0xFFE8D6A8),
            inverseSurface = RedLight,
            inverseOnSurface = YellowLightLight,
            inversePrimary = YamiboColors.onSecondary,
            surfaceTint = RedLight
        )
    ),

    CLASSIC_DARK(
        label = "经典·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB59A),
            primaryContainer = Color(0xFF6D301C),
            secondary = Color(0xFFE7B9A8),
            secondaryContainer = Color(0xFF553024),
            background = Color(0xFF17110F),
            surface = Color(0xFF211815),
            surfaceVariant = Color(0xFF4F3630)
        )
    ),

    BLUE_BLACK(
        label = "蓝黑·深",
        isDark = true,
        scheme = darkColorScheme(
            primary = Color(0xFF4EA1FF),
            onPrimary = Color(0xFF04243F),
            primaryContainer = Color(0xFF1C3A57),
            onPrimaryContainer = Color(0xFFCFE5FF),
            secondary = Color(0xFF8FC0F2),
            onSecondary = Color(0xFF06243C),
            secondaryContainer = Color(0xFF223247),
            onSecondaryContainer = Color(0xFFCFE5FF),
            tertiary = Color(0xFF9CC7E8),
            onTertiary = Color(0xFF06243C),
            tertiaryContainer = Color(0xFF294761),
            onTertiaryContainer = Color(0xFFD8ECFF),
            background = Color(0xFF0D141D),
            onBackground = Color(0xFFE6EDF5),
            surface = Color(0xFF182332),
            onSurface = Color(0xFFE6EDF5),
            surfaceVariant = Color(0xFF223247),
            onSurfaceVariant = Color(0xFFA9BBD0),
            outline = Color(0xFF3A4A5E),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            surfaceDim = Color(0xFF0D141D),
            surfaceBright = Color(0xFF2A3B50),
            surfaceContainerLowest = Color(0xFF080D14),
            surfaceContainerLow = Color(0xFF141D29),
            surfaceContainer = Color(0xFF1A2735),
            surfaceContainerHigh = Color(0xFF223247),
            surfaceContainerHighest = Color(0xFF293B50)
        )
    ),

    BLUE_LIGHT(
        label = "晴蓝·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF1466B8),
            primaryContainer = Color(0xFFD5E7FF),
            secondary = Color(0xFF536780),
            secondaryContainer = Color(0xFFD9E7F7),
            background = Color(0xFFF7F9FD),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDDE7F3)
        )
    ),

    TEAL_LIGHT(
        label = "海青·浅",
        isDark = false,
        scheme = lightColorScheme(
            primary = Color(0xFF006B5B),
            onPrimary = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF00201A),
            secondary = Color(0xFF4A635C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD5EAE3),
            onSecondaryContainer = Color(0xFF06201A),
            tertiary = Color(0xFF3A766A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFCDE8DF),
            onTertiaryContainer = Color(0xFF06201A),
            primaryContainer = Color(0xFFB9EBDD),
            background = Color(0xFFF8FBF9),
            onBackground = Color(0xFF191C1B),
            surface = Color(0xFFF8FBF9),
            onSurface = Color(0xFF191C1B),
            surfaceVariant = Color(0xFFDCEAE5),
            onSurfaceVariant = Color(0xFF3F4946),
            outline = Color(0xFF6F7975),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFDBE1DD),
            surfaceBright = Color(0xFFF8FBF9),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF1F8F5),
            surfaceContainer = Color(0xFFE8F3EF),
            surfaceContainerHigh = Color(0xFFDDEDE7),
            surfaceContainerHighest = Color(0xFFD2E6DF)
        )
    ),

    TEAL_DARK(
        label = "墨绿·深",
        isDark = true,
        scheme = darkColorScheme(
            primary = Color(0xFF50DBC2),
            onPrimary = Color(0xFF00382F),
            primaryContainer = Color(0xFF005044),
            onPrimaryContainer = Color(0xFF6FF7E0),
            secondary = Color(0xFFB1CCC3),
            onSecondary = Color(0xFF1C352F),
            secondaryContainer = Color(0xFF324B45),
            onSecondaryContainer = Color(0xFFCCE8DF),
            tertiary = Color(0xFF9DD0C3),
            onTertiary = Color(0xFF17352E),
            tertiaryContainer = Color(0xFF2A5148),
            onTertiaryContainer = Color(0xFFC5E8DE),
            background = Color(0xFF191C1B),
            onBackground = Color(0xFFE0E3E0),
            surface = Color(0xFF191C1B),
            onSurface = Color(0xFFE0E3E0),
            surfaceVariant = Color(0xFF3F4946),
            onSurfaceVariant = Color(0xFFBFC9C4),
            outline = Color(0xFF899390),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            surfaceDim = Color(0xFF111413),
            surfaceBright = Color(0xFF363A38),
            surfaceContainerLowest = Color(0xFF0F1211),
            surfaceContainerLow = Color(0xFF1B201E),
            surfaceContainer = Color(0xFF1F2523),
            surfaceContainerHigh = Color(0xFF2A302E),
            surfaceContainerHighest = Color(0xFF353B39)
        )
    ),

    SAKURA(
        label = "樱粉·浅",
        isDark = false,
        scheme = lightColorScheme(
            primary = Color(0xFFB3325E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9E1),
            onPrimaryContainer = Color(0xFF3E001D),
            secondary = Color(0xFF75565C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFD9E1),
            onSecondaryContainer = Color(0xFF2B151A),
            tertiary = Color(0xFFA64B69),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFF5D9E1),
            onTertiaryContainer = Color(0xFF3E001D),
            background = Color(0xFFFFF8F8),
            onBackground = Color(0xFF201A1B),
            surface = Color(0xFFFFF8F8),
            onSurface = Color(0xFF201A1B),
            surfaceVariant = Color(0xFFF2DDE1),
            onSurfaceVariant = Color(0xFF514347),
            outline = Color(0xFF837377),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE7D6D9),
            surfaceBright = Color(0xFFFFF8F8),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFCEFF1),
            surfaceContainer = Color(0xFFFCE9EC),
            surfaceContainerHigh = Color(0xFFF8E3E6),
            surfaceContainerHighest = Color(0xFFF2DDE1)
        )
    ),

    SAKURA_DARK(
        label = "樱粉·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB0C8),
            primaryContainer = Color(0xFF7E294B),
            secondary = Color(0xFFE4B9C4),
            secondaryContainer = Color(0xFF58333D),
            background = Color(0xFF181115),
            surface = Color(0xFF23191E),
            surfaceVariant = Color(0xFF514249)
        )
    ),

    PURPLE_LIGHT(
        label = "薰衣草·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF6750A4),
            primaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFF625B71),
            secondaryContainer = Color(0xFFE8DEF8),
            background = Color(0xFFFFF7FF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE7E0EC)
        )
    ),

    MIDNIGHT_PURPLE(
        label = "暮紫·深",
        isDark = true,
        scheme = darkColorScheme(
            primary = Color(0xFFCFBCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCBC2DB),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFD0B9F2),
            onTertiary = Color(0xFF35205F),
            tertiaryContainer = Color(0xFF4B3972),
            onTertiaryContainer = Color(0xFFEBDFFF),
            background = Color(0xFF141218),
            onBackground = Color(0xFFE6E0E9),
            surface = Color(0xFF1D1B20),
            onSurface = Color(0xFFE6E0E9),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            surfaceDim = Color(0xFF141218),
            surfaceBright = Color(0xFF3B383F),
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F26),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B)
        )
    ),

    GREEN_LIGHT(
        label = "新叶·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF3C6B35),
            primaryContainer = Color(0xFFBDF2AE),
            secondary = Color(0xFF54634F),
            secondaryContainer = Color(0xFFD7E8CF),
            background = Color(0xFFF8FBF3),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDFE6DA)
        )
    ),

    GREEN_DARK(
        label = "森林·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFA2D693),
            primaryContainer = Color(0xFF255020),
            secondary = Color(0xFFBBCBB4),
            secondaryContainer = Color(0xFF3C4B37),
            background = Color(0xFF11150F),
            surface = Color(0xFF1A2018),
            surfaceVariant = Color(0xFF43493F)
        )
    ),

    ORANGE_LIGHT(
        label = "蜜橙·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF9A4520),
            primaryContainer = Color(0xFFFFDBCC),
            secondary = Color(0xFF77574A),
            secondaryContainer = Color(0xFFFFDBCC),
            background = Color(0xFFFFF8F5),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF5DED5)
        )
    ),

    ORANGE_DARK(
        label = "琥珀·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB596),
            primaryContainer = Color(0xFF783209),
            secondary = Color(0xFFE7BDB0),
            secondaryContainer = Color(0xFF5D4036),
            background = Color(0xFF17120F),
            surface = Color(0xFF211A16),
            surfaceVariant = Color(0xFF52443E)
        )
    ),

    CYAN_LIGHT(
        label = "天青·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF006A6A),
            primaryContainer = Color(0xFF9CF1F0),
            secondary = Color(0xFF4A6363),
            secondaryContainer = Color(0xFFCCE8E7),
            background = Color(0xFFF4FBFA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFD6E5E4)
        )
    ),

    CYAN_DARK(
        label = "深海·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFF7ADBD9),
            primaryContainer = Color(0xFF005050),
            secondary = Color(0xFFB0CCCB),
            secondaryContainer = Color(0xFF314B4A),
            background = Color(0xFF0E1515),
            surface = Color(0xFF172020),
            surfaceVariant = Color(0xFF3F4948)
        )
    );

    companion object {
        val DEFAULT = CLASSIC_LIGHT
        fun fromName(name: String?): YamiboAppTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class YamiboThemePalette(
    val label: String,
    val lightTheme: YamiboAppTheme,
    val darkTheme: YamiboAppTheme,
    val swatches: List<Color>
) {
    CLASSIC(
        "经典",
        YamiboAppTheme.CLASSIC_LIGHT,
        YamiboAppTheme.CLASSIC_DARK,
        listOf(Color(0xFF551200), Color(0xFFFFFBE7), Color(0xFFFFB59A), Color(0xFF211815))
    ),
    BLUE(
        "晴蓝",
        YamiboAppTheme.BLUE_LIGHT,
        YamiboAppTheme.BLUE_BLACK,
        listOf(Color(0xFF1466B8), Color(0xFFD5E7FF), Color(0xFF4EA1FF), Color(0xFF182332))
    ),
    TEAL(
        "海青",
        YamiboAppTheme.TEAL_LIGHT,
        YamiboAppTheme.TEAL_DARK,
        listOf(Color(0xFF006B5B), Color(0xFFB9EBDD), Color(0xFF50DBC2), Color(0xFF1F2523))
    ),
    SAKURA(
        "樱粉",
        YamiboAppTheme.SAKURA,
        YamiboAppTheme.SAKURA_DARK,
        listOf(Color(0xFFB3325E), Color(0xFFFFD9E1), Color(0xFFFFB0C8), Color(0xFF23191E))
    ),
    PURPLE(
        "暮紫",
        YamiboAppTheme.PURPLE_LIGHT,
        YamiboAppTheme.MIDNIGHT_PURPLE,
        listOf(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFFCFBCFF), Color(0xFF211F26))
    ),
    GREEN(
        "新叶",
        YamiboAppTheme.GREEN_LIGHT,
        YamiboAppTheme.GREEN_DARK,
        listOf(Color(0xFF3C6B35), Color(0xFFBDF2AE), Color(0xFFA2D693), Color(0xFF1A2018))
    ),
    ORANGE(
        "蜜橙",
        YamiboAppTheme.ORANGE_LIGHT,
        YamiboAppTheme.ORANGE_DARK,
        listOf(Color(0xFF9A4520), Color(0xFFFFDBCC), Color(0xFFFFB596), Color(0xFF211A16))
    ),
    CYAN(
        "天青",
        YamiboAppTheme.CYAN_LIGHT,
        YamiboAppTheme.CYAN_DARK,
        listOf(Color(0xFF006A6A), Color(0xFF9CF1F0), Color(0xFF7ADBD9), Color(0xFF172020))
    );

    fun resolve(isDark: Boolean): YamiboAppTheme = if (isDark) darkTheme else lightTheme

    companion object {
        val DEFAULT = CLASSIC

        fun parse(name: String?): YamiboThemePalette? =
            entries.firstOrNull { it.name == name }

        fun fromTheme(theme: YamiboAppTheme): YamiboThemePalette = when (theme) {
            YamiboAppTheme.CLASSIC_LIGHT, YamiboAppTheme.CLASSIC_DARK -> CLASSIC
            YamiboAppTheme.BLUE_LIGHT, YamiboAppTheme.BLUE_BLACK -> BLUE
            YamiboAppTheme.TEAL_LIGHT, YamiboAppTheme.TEAL_DARK -> TEAL
            YamiboAppTheme.SAKURA, YamiboAppTheme.SAKURA_DARK -> SAKURA
            YamiboAppTheme.PURPLE_LIGHT, YamiboAppTheme.MIDNIGHT_PURPLE -> PURPLE
            YamiboAppTheme.GREEN_LIGHT, YamiboAppTheme.GREEN_DARK -> GREEN
            YamiboAppTheme.ORANGE_LIGHT, YamiboAppTheme.ORANGE_DARK -> ORANGE
            YamiboAppTheme.CYAN_LIGHT, YamiboAppTheme.CYAN_DARK -> CYAN
        }
    }
}

enum class YamiboThemeMode(val label: String) {
    SYSTEM("自动"),
    LIGHT("浅色"),
    DARK("深色");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun parse(name: String?): YamiboThemeMode? =
            entries.firstOrNull { it.name == name }
    }
}

data class YamiboThemePreference(
    val palette: YamiboThemePalette = YamiboThemePalette.DEFAULT,
    val mode: YamiboThemeMode = YamiboThemeMode.SYSTEM,
    val pureBlack: Boolean = false
) {
    fun resolve(systemDark: Boolean): YamiboAppTheme =
        palette.resolve(mode.resolveDark(systemDark))

    companion object {
        fun fromStored(
            paletteName: String?,
            modeName: String?,
            pureBlackValue: String?,
            legacyThemeName: String?
        ): YamiboThemePreference {
            val legacyTheme = legacyThemeName?.let(YamiboAppTheme::fromName)
            val palette = YamiboThemePalette.parse(paletteName)
                ?: legacyTheme?.let(YamiboThemePalette::fromTheme)
                ?: YamiboThemePalette.DEFAULT
            val mode = YamiboThemeMode.parse(modeName) ?: when {
                legacyTheme == null -> YamiboThemeMode.SYSTEM
                legacyTheme.isDark -> YamiboThemeMode.DARK
                else -> YamiboThemeMode.LIGHT
            }
            return YamiboThemePreference(
                palette = palette,
                mode = mode,
                pureBlack = pureBlackValue?.toBooleanStrictOrNull() ?: false
            )
        }
    }
}

fun YamiboAppTheme.effectiveScheme(pureBlack: Boolean): ColorScheme {
    if (!pureBlack || !isDark) return scheme
    return scheme.copy(
        background = Color.Black,
        surface = Color(0xFF090909),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF242424),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF070707),
        surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerHigh = Color(0xFF151515),
        surfaceContainerHighest = Color(0xFF202020)
    )
}

private fun yamiboLightScheme(
    primary: Color,
    primaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = Color(0xFF17191C),
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFF17191C),
    tertiary = primary,
    onTertiary = Color.White,
    tertiaryContainer = primaryContainer,
    onTertiaryContainer = Color(0xFF17191C),
    background = background,
    onBackground = Color(0xFF191C1E),
    surface = surface,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = Color(0xFF41474B),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC1C7CD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surfaceDim = androidx.compose.ui.graphics.lerp(background, surfaceVariant, 0.72f),
    surfaceBright = surface,
    surfaceContainerLowest = surface,
    surfaceContainerLow = androidx.compose.ui.graphics.lerp(background, surfaceVariant, 0.16f),
    surfaceContainer = androidx.compose.ui.graphics.lerp(background, surfaceVariant, 0.28f),
    surfaceContainerHigh = androidx.compose.ui.graphics.lerp(background, surfaceVariant, 0.42f),
    surfaceContainerHighest = androidx.compose.ui.graphics.lerp(background, surfaceVariant, 0.58f),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF0F1F3),
    inversePrimary = primaryContainer,
    surfaceTint = primary
)

private fun yamiboDarkScheme(
    primary: Color,
    primaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF172028),
    primaryContainer = primaryContainer,
    onPrimaryContainer = Color(0xFFFFEDEA),
    secondary = secondary,
    onSecondary = Color(0xFF20252A),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFFF2E5E1),
    tertiary = primary,
    onTertiary = Color(0xFF20252A),
    tertiaryContainer = primaryContainer,
    onTertiaryContainer = Color(0xFFFFEDEA),
    background = background,
    onBackground = Color(0xFFE8E1DE),
    surface = surface,
    onSurface = Color(0xFFE8E1DE),
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = Color(0xFFD0C4C0),
    outline = Color(0xFF9B8F8B),
    outlineVariant = Color(0xFF4E4542),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surfaceDim = background,
    surfaceBright = androidx.compose.ui.graphics.lerp(surface, surfaceVariant, 0.55f),
    surfaceContainerLowest = androidx.compose.ui.graphics.lerp(Color.Black, background, 0.72f),
    surfaceContainerLow = androidx.compose.ui.graphics.lerp(background, surface, 0.55f),
    surfaceContainer = surface,
    surfaceContainerHigh = androidx.compose.ui.graphics.lerp(surface, surfaceVariant, 0.36f),
    surfaceContainerHighest = androidx.compose.ui.graphics.lerp(surface, surfaceVariant, 0.58f),
    inverseSurface = Color(0xFFE8E1DE),
    inverseOnSurface = Color(0xFF302A28),
    inversePrimary = primaryContainer,
    surfaceTint = primary
)

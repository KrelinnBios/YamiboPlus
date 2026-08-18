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
            primary = Color(0xFFB0452D),
            primaryContainer = Color(0xFFFFDAD0),
            secondary = Color(0xFF79564C),
            secondaryContainer = Color(0xFFFFDACF),
            background = Color(0xFFFFF8F6),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF5DED8)
        )
    ),

    ORANGE_DARK(
        label = "蜜橙·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB4A2),
            primaryContainer = Color(0xFF7A2E1A),
            secondary = Color(0xFFE6BCB0),
            secondaryContainer = Color(0xFF5C3E35),
            background = Color(0xFF1E120D),
            surface = Color(0xFF291813),
            surfaceVariant = Color(0xFF55372E)
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
    ),

    GOLD_LIGHT(
        label = "鎏金·浅",
        isDark = false,
        // 金黄底 + 深棕字，避免黄色系配白字显得又土又脏。
        scheme = lightColorScheme(
            primary = Color(0xFFF2B705),
            onPrimary = Color(0xFF3A2C00),
            primaryContainer = Color(0xFFFFE08A),
            onPrimaryContainer = Color(0xFF3A2C00),
            secondary = Color(0xFF8A6D00),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF8E2A0),
            onSecondaryContainer = Color(0xFF2C2200),
            tertiary = Color(0xFFB08900),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFE08A),
            onTertiaryContainer = Color(0xFF3A2C00),
            background = Color(0xFFFFFBF0),
            onBackground = Color(0xFF211A05),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF211A05),
            surfaceVariant = Color(0xFFF0E5C0),
            onSurfaceVariant = Color(0xFF4E4527),
            outline = Color(0xFF7E7355),
            outlineVariant = Color(0xFFD0C59E),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE0D6B6),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFAF3DD),
            surfaceContainer = Color(0xFFF5EDD2),
            surfaceContainerHigh = Color(0xFFEFE7C8),
            surfaceContainerHighest = Color(0xFFE9E1BE),
            inverseSurface = Color(0xFF332C14),
            inverseOnSurface = Color(0xFFF6EED6),
            inversePrimary = Color(0xFFFFE08A),
            surfaceTint = Color(0xFFF2B705)
        )
    ),

    GOLD_DARK(
        label = "鎏金·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFE057),
            primaryContainer = Color(0xFF665000),
            secondary = Color(0xFFE6CB94),
            secondaryContainer = Color(0xFF55471C),
            background = Color(0xFF1C1504),
            surface = Color(0xFF26200B),
            surfaceVariant = Color(0xFF504417)
        )
    ),

    MIST_LIGHT(
        label = "雾蓝·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF46627F),
            primaryContainer = Color(0xFFCFE3F5),
            secondary = Color(0xFF53606E),
            secondaryContainer = Color(0xFFD9E4EF),
            background = Color(0xFFF7FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCE4EC)
        )
    ),

    MIST_DARK(
        label = "雾蓝·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFA8C7E4),
            primaryContainer = Color(0xFF2E4A63),
            secondary = Color(0xFFB7C8D8),
            secondaryContainer = Color(0xFF37495A),
            background = Color(0xFF10151A),
            surface = Color(0xFF191F25),
            surfaceVariant = Color(0xFF3B4750)
        )
    ),

    MAGENTA_LIGHT(
        label = "品红·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF9B2E7E),
            primaryContainer = Color(0xFFFFD7F0),
            secondary = Color(0xFF7A4E6B),
            secondaryContainer = Color(0xFFFFD7EE),
            background = Color(0xFFFFF8FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF3DCEA)
        )
    ),

    MAGENTA_DARK(
        label = "品红·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFADE0),
            primaryContainer = Color(0xFF7B1F62),
            secondary = Color(0xFFE3B6D3),
            secondaryContainer = Color(0xFF5B3D51),
            background = Color(0xFF1D1218),
            surface = Color(0xFF281821),
            surfaceVariant = Color(0xFF523C48)
        )
    ),

    CHESTNUT_LIGHT(
        label = "栗棕·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF6D4C41),
            primaryContainer = Color(0xFFF3DDD0),
            secondary = Color(0xFF7C5C4E),
            secondaryContainer = Color(0xFFF0D9CB),
            background = Color(0xFFFFF9F5),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1DFD6)
        )
    ),

    CHESTNUT_DARK(
        label = "栗棕·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFE3BBA8),
            primaryContainer = Color(0xFF52342A),
            secondary = Color(0xFFD2B5A6),
            secondaryContainer = Color(0xFF4A342C),
            background = Color(0xFF1D1411),
            surface = Color(0xFF281C18),
            surfaceVariant = Color(0xFF513B34)
        )
    ),

    CRIMSON_LIGHT(
        label = "绯红·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFFA31530),
            primaryContainer = Color(0xFFFFDAD5),
            secondary = Color(0xFF7A4A4E),
            secondaryContainer = Color(0xFFF9DADB),
            background = Color(0xFFFFF8F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF5DEDC)
        )
    ),

    CRIMSON_DARK(
        label = "绯红·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB3AC),
            primaryContainer = Color(0xFF8C1D2E),
            secondary = Color(0xFFE5BDBD),
            secondaryContainer = Color(0xFF5D3A3D),
            background = Color(0xFF1D1212),
            surface = Color(0xFF281819),
            surfaceVariant = Color(0xFF523C3D)
        )
    ),

    AMBER_LIGHT(
        label = "琥珀·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFFB06000),
            primaryContainer = Color(0xFFFFDCBB),
            secondary = Color(0xFF7A5B33),
            secondaryContainer = Color(0xFFFFDFB8),
            background = Color(0xFFFFF9F2),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF4E0CC)
        )
    ),

    AMBER_DARK(
        label = "琥珀·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFFFB875),
            primaryContainer = Color(0xFF7A3D00),
            secondary = Color(0xFFE8C29A),
            secondaryContainer = Color(0xFF5A401F),
            background = Color(0xFF1E1308),
            surface = Color(0xFF291D0E),
            surfaceVariant = Color(0xFF513D22)
        )
    ),

    LIME_LIGHT(
        label = "柠黄·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF7B8E00),
            primaryContainer = Color(0xFFE8F58B),
            secondary = Color(0xFF5F632B),
            secondaryContainer = Color(0xFFE5E8A8),
            background = Color(0xFFFBFCE9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE6E7C4)
        )
    ),

    LIME_DARK(
        label = "柠黄·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFC8DB66),
            primaryContainer = Color(0xFF4E5D00),
            secondary = Color(0xFFCCD08F),
            secondaryContainer = Color(0xFF464B1E),
            background = Color(0xFF16180A),
            surface = Color(0xFF1F2210),
            surfaceVariant = Color(0xFF474A2F)
        )
    ),

    MOSS_LIGHT(
        label = "苔绿·浅",
        isDark = false,
        scheme = yamiboLightScheme(
            primary = Color(0xFF556B2F),
            primaryContainer = Color(0xFFD6E9AC),
            secondary = Color(0xFF59624A),
            secondaryContainer = Color(0xFFDCE7C8),
            background = Color(0xFFF8FAEE),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE1E6D2)
        )
    ),

    MOSS_DARK(
        label = "苔绿·深",
        isDark = true,
        scheme = yamiboDarkScheme(
            primary = Color(0xFFBBD37F),
            primaryContainer = Color(0xFF354617),
            secondary = Color(0xFFC4CDA6),
            secondaryContainer = Color(0xFF3A452A),
            background = Color(0xFF131608),
            surface = Color(0xFF1C200E),
            surfaceVariant = Color(0xFF444A33)
        )
    ),

    WHITE(
        label = "月白",
        isDark = false,
        scheme = lightColorScheme(
            primary = Color(0xFF3F3F46),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE8E8EC),
            onPrimaryContainer = Color(0xFF101014),
            secondary = Color(0xFF58585F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0E0E5),
            onSecondaryContainer = Color(0xFF16161A),
            tertiary = Color(0xFF4A4A51),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE2E2E7),
            onTertiaryContainer = Color(0xFF101014),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF101014),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF101014),
            surfaceVariant = Color(0xFFEDEDF0),
            onSurfaceVariant = Color(0xFF55555C),
            outline = Color(0xFF8E8E96),
            outlineVariant = Color(0xFFD9D9DE),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFDEDEE3),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF6F6F8),
            surfaceContainer = Color(0xFFF0F0F3),
            surfaceContainerHigh = Color(0xFFEAEAEE),
            surfaceContainerHighest = Color(0xFFE4E4E9),
            inverseSurface = Color(0xFF2A2A2F),
            inverseOnSurface = Color(0xFFF1F1F4),
            inversePrimary = Color(0xFFE8E8EC),
            surfaceTint = Color(0xFF3F3F46)
        )
    ),

    BLACK(
        label = "墨黑",
        isDark = true,
        scheme = darkColorScheme(
            primary = Color(0xFFE4E4E7),
            onPrimary = Color(0xFF101014),
            primaryContainer = Color(0xFF3F3F46),
            onPrimaryContainer = Color(0xFFE8E8EC),
            secondary = Color(0xFFC4C4CA),
            onSecondary = Color(0xFF26262B),
            secondaryContainer = Color(0xFF3A3A40),
            onSecondaryContainer = Color(0xFFE0E0E5),
            tertiary = Color(0xFFB9B9C0),
            onTertiary = Color(0xFF202025),
            tertiaryContainer = Color(0xFF34343A),
            onTertiaryContainer = Color(0xFFE2E2E7),
            background = Color(0xFF000000),
            onBackground = Color(0xFFEDEDF0),
            surface = Color(0xFF0A0A0A),
            onSurface = Color(0xFFEDEDF0),
            surfaceVariant = Color(0xFF2A2A2E),
            onSurfaceVariant = Color(0xFFB8B8BF),
            outline = Color(0xFF55555C),
            outlineVariant = Color(0xFF3A3A40),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            surfaceDim = Color(0xFF000000),
            surfaceBright = Color(0xFF3A3A40),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF282828),
            inverseSurface = Color(0xFFEDEDF0),
            inverseOnSurface = Color(0xFF26262B),
            inversePrimary = Color(0xFF3F3F46),
            surfaceTint = Color(0xFFE4E4E7)
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
    CHESTNUT(
        "栗棕",
        YamiboAppTheme.CHESTNUT_LIGHT,
        YamiboAppTheme.CHESTNUT_DARK,
        listOf(Color(0xFF6D4C41), Color(0xFFF3DDD0), Color(0xFFE3BBA8), Color(0xFF281C18))
    ),
    SAKURA(
        "樱粉",
        YamiboAppTheme.SAKURA,
        YamiboAppTheme.SAKURA_DARK,
        listOf(Color(0xFFB3325E), Color(0xFFFFD9E1), Color(0xFFFFB0C8), Color(0xFF23191E))
    ),
    CRIMSON(
        "绯红",
        YamiboAppTheme.CRIMSON_LIGHT,
        YamiboAppTheme.CRIMSON_DARK,
        listOf(Color(0xFFA31530), Color(0xFFFFDAD5), Color(0xFFFFB3AC), Color(0xFF281819))
    ),
    ORANGE(
        "蜜橙",
        YamiboAppTheme.ORANGE_LIGHT,
        YamiboAppTheme.ORANGE_DARK,
        listOf(Color(0xFFB0452D), Color(0xFFFFDAD0), Color(0xFFFFB4A2), Color(0xFF291813))
    ),
    AMBER(
        "琥珀",
        YamiboAppTheme.AMBER_LIGHT,
        YamiboAppTheme.AMBER_DARK,
        listOf(Color(0xFFB06000), Color(0xFFFFDCBB), Color(0xFFFFB875), Color(0xFF291D0E))
    ),
    GOLD(
        "鎏金",
        YamiboAppTheme.GOLD_LIGHT,
        YamiboAppTheme.GOLD_DARK,
        listOf(Color(0xFFF2B705), Color(0xFFFFE08A), Color(0xFFFFE057), Color(0xFF26200B))
    ),
    LIME(
        "柠黄",
        YamiboAppTheme.LIME_LIGHT,
        YamiboAppTheme.LIME_DARK,
        listOf(Color(0xFF7B8E00), Color(0xFFE8F58B), Color(0xFFC8DB66), Color(0xFF1F2210))
    ),
    GREEN(
        "新叶",
        YamiboAppTheme.GREEN_LIGHT,
        YamiboAppTheme.GREEN_DARK,
        listOf(Color(0xFF3C6B35), Color(0xFFBDF2AE), Color(0xFFA2D693), Color(0xFF1A2018))
    ),
    MOSS(
        "苔绿",
        YamiboAppTheme.MOSS_LIGHT,
        YamiboAppTheme.MOSS_DARK,
        listOf(Color(0xFF556B2F), Color(0xFFD6E9AC), Color(0xFFBBD37F), Color(0xFF1C200E))
    ),
    TEAL(
        "海青",
        YamiboAppTheme.TEAL_LIGHT,
        YamiboAppTheme.TEAL_DARK,
        listOf(Color(0xFF006B5B), Color(0xFFB9EBDD), Color(0xFF50DBC2), Color(0xFF1F2523))
    ),
    CYAN(
        "天青",
        YamiboAppTheme.CYAN_LIGHT,
        YamiboAppTheme.CYAN_DARK,
        listOf(Color(0xFF006A6A), Color(0xFF9CF1F0), Color(0xFF7ADBD9), Color(0xFF172020))
    ),
    BLUE(
        "晴蓝",
        YamiboAppTheme.BLUE_LIGHT,
        YamiboAppTheme.BLUE_BLACK,
        listOf(Color(0xFF1466B8), Color(0xFFD5E7FF), Color(0xFF4EA1FF), Color(0xFF182332))
    ),
    MIST(
        "雾蓝",
        YamiboAppTheme.MIST_LIGHT,
        YamiboAppTheme.MIST_DARK,
        listOf(Color(0xFF46627F), Color(0xFFCFE3F5), Color(0xFFA8C7E4), Color(0xFF191F25))
    ),
    PURPLE(
        "暮紫",
        YamiboAppTheme.PURPLE_LIGHT,
        YamiboAppTheme.MIDNIGHT_PURPLE,
        listOf(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFFCFBCFF), Color(0xFF211F26))
    ),
    MAGENTA(
        "品红",
        YamiboAppTheme.MAGENTA_LIGHT,
        YamiboAppTheme.MAGENTA_DARK,
        listOf(Color(0xFF9B2E7E), Color(0xFFFFD7F0), Color(0xFFFFADE0), Color(0xFF281821))
    ),
    WHITE(
        "月白",
        YamiboAppTheme.WHITE,
        YamiboAppTheme.WHITE,
        listOf(Color(0xFF3F3F46), Color(0xFFE8E8EC), Color(0xFF3F3F46), Color(0xFFFFFFFF))
    ),
    BLACK(
        "墨黑",
        YamiboAppTheme.BLACK,
        YamiboAppTheme.BLACK,
        listOf(Color(0xFFE4E4E7), Color(0xFF3F3F46), Color(0xFFE4E4E7), Color(0xFF0A0A0A))
    );

    fun resolve(isDark: Boolean): YamiboAppTheme = if (isDark) darkTheme else lightTheme

    companion object {
        val DEFAULT = CLASSIC

        fun parse(name: String?): YamiboThemePalette? =
            entries.firstOrNull { it.name == name }

        fun fromTheme(theme: YamiboAppTheme): YamiboThemePalette = when (theme) {
            YamiboAppTheme.CLASSIC_LIGHT, YamiboAppTheme.CLASSIC_DARK -> CLASSIC
            YamiboAppTheme.CHESTNUT_LIGHT, YamiboAppTheme.CHESTNUT_DARK -> CHESTNUT
            YamiboAppTheme.SAKURA, YamiboAppTheme.SAKURA_DARK -> SAKURA
            YamiboAppTheme.CRIMSON_LIGHT, YamiboAppTheme.CRIMSON_DARK -> CRIMSON
            YamiboAppTheme.ORANGE_LIGHT, YamiboAppTheme.ORANGE_DARK -> ORANGE
            YamiboAppTheme.AMBER_LIGHT, YamiboAppTheme.AMBER_DARK -> AMBER
            YamiboAppTheme.GOLD_LIGHT, YamiboAppTheme.GOLD_DARK -> GOLD
            YamiboAppTheme.LIME_LIGHT, YamiboAppTheme.LIME_DARK -> LIME
            YamiboAppTheme.GREEN_LIGHT, YamiboAppTheme.GREEN_DARK -> GREEN
            YamiboAppTheme.MOSS_LIGHT, YamiboAppTheme.MOSS_DARK -> MOSS
            YamiboAppTheme.TEAL_LIGHT, YamiboAppTheme.TEAL_DARK -> TEAL
            YamiboAppTheme.CYAN_LIGHT, YamiboAppTheme.CYAN_DARK -> CYAN
            YamiboAppTheme.BLUE_LIGHT, YamiboAppTheme.BLUE_BLACK -> BLUE
            YamiboAppTheme.MIST_LIGHT, YamiboAppTheme.MIST_DARK -> MIST
            YamiboAppTheme.PURPLE_LIGHT, YamiboAppTheme.MIDNIGHT_PURPLE -> PURPLE
            YamiboAppTheme.MAGENTA_LIGHT, YamiboAppTheme.MAGENTA_DARK -> MAGENTA
            YamiboAppTheme.WHITE -> WHITE
            YamiboAppTheme.BLACK -> BLACK
        }
    }
}

enum class YamiboThemeMode(val label: String) {
    SYSTEM("自动"),
    LIGHT("浅色"),
    DARK("深色");

    fun displayLabel(pureBlack: Boolean): String = if (pureBlack) "纯黑" else label

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
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF090909),
        onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color.Black,
        onSurfaceVariant = Color(0xFFD6D6D6),
        primaryContainer = Color.Black,
        onPrimaryContainer = Color.White,
        secondaryContainer = Color.Black,
        onSecondaryContainer = Color.White,
        tertiaryContainer = Color.Black,
        onTertiaryContainer = Color.White,
        outline = Color(0xFF9E9E9E),
        outlineVariant = Color(0xFF4A4A4A),
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        surfaceTint = scheme.primary
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

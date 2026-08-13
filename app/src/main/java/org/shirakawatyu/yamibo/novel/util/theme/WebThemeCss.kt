package org.shirakawatyu.yamibo.novel.util.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboAppTheme
import org.shirakawatyu.yamibo.novel.ui.theme.effectiveScheme
import java.util.Locale

/** 将现有网页选择器复用到应用当前色板，避免每套主题维护一份庞大的 Discuz CSS。 */
internal fun webThemeCssRules(
    theme: YamiboAppTheme,
    pureBlack: Boolean = false
): List<String> = when {
    !pureBlack && theme == YamiboAppTheme.CLASSIC_LIGHT -> LIGHT_MODE_CSS_RULES_CLASSIC
    !pureBlack && theme == YamiboAppTheme.BLUE_BLACK -> DARK_MODE_CSS_RULES_CLASSIC
    else -> recolorClassicWebRules(theme, pureBlack)
}

internal fun webThemeEditorCss(
    theme: YamiboAppTheme,
    pureBlack: Boolean = false
): String = with(theme.effectiveScheme(pureBlack)) {
    "html,body{background:" + surface.toCssHex() + " !important;" +
        "color:" + onSurface.toCssHex() + " !important;}" +
        "a{color:" + primary.toCssHex() + " !important;}"
}

private fun recolorClassicWebRules(theme: YamiboAppTheme, pureBlack: Boolean) : List<String> {
    val colors = theme.effectiveScheme(pureBlack)
    val replacements = linkedMapOf(
        "#0d141d" to colors.background.toCssHex(),
        "#121b27" to colors.surfaceContainerLow.toCssHex(),
        "#182332" to colors.surface.toCssHex(),
        "#1f2c3d" to colors.surfaceContainerHigh.toCssHex(),
        "#223247" to colors.surfaceVariant.toCssHex(),
        "#274766" to colors.secondaryContainer.toCssHex(),
        "#31577a" to colors.secondaryContainer.toCssHex(),
        "#3c5677" to colors.outline.toCssHex(),
        "#4b6a8f" to colors.outline.toCssHex(),
        "#edf4fb" to colors.onBackground.toCssHex(),
        "#c7d8ea" to colors.onSurface.toCssHex(),
        "#acbed1" to colors.onSurfaceVariant.toCssHex(),
        "#95acc4" to colors.onSurfaceVariant.toCssHex(),
        "#8099b2" to colors.onSurfaceVariant.toCssHex(),
        "#6f89a3" to colors.outline.toCssHex(),
        "#4ea1ff" to colors.primary.toCssHex(),
        "#7dbdf2" to colors.primary.toCssHex(),
        "#8ccbff" to colors.primary.toCssHex(),
        "#ffffff" to colors.onSecondaryContainer.toCssHex(),
        "#fff" to colors.onSecondaryContainer.toCssHex(),
        "#fcf4cf" to colors.surface.toCssHex(),
        "#ffeebb" to colors.primaryContainer.toCssHex(),
        "#dbc38c" to colors.secondaryContainer.toCssHex(),
        "#ebd7a9" to colors.surfaceContainerHigh.toCssHex(),
        "#6e2b19" to colors.primary.toCssHex(),
        "#551200" to colors.primary.toCssHex(),
        "#7a3f4b" to colors.primary.toCssHex()
    )
    return DARK_MODE_CSS_RULES_CLASSIC.map { rule ->
        val recolored = replacements.entries.fold(rule) { themedRule, (classic, current) ->
            themedRule.replace(
                Regex(Regex.escape(classic) + "(?![0-9a-f])", RegexOption.IGNORE_CASE),
                current
            )
        }
        if (theme.isDark) {
            recolored
        } else {
            recolored.replace(
                Regex("""filter:\s*invert\(1\)[^;]*!\s*important;"""),
                "filter: none !important;"
            )
        }
    }
}

private fun Color.toCssHex(): String = String.format(
    Locale.ROOT,
    "#%06x",
    toArgb() and 0x00ffffff
)

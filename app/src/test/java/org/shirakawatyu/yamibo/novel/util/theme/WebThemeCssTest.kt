package org.shirakawatyu.yamibo.novel.util.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboAppTheme

class WebThemeCssTest {
    @Test
    fun classicThemesKeepTheirEstablishedRules() {
        assertEquals(
            LIGHT_MODE_CSS_RULES_CLASSIC,
            webThemeCssRules(YamiboAppTheme.CLASSIC_LIGHT)
        )
        assertEquals(
            DARK_MODE_CSS_RULES_CLASSIC,
            webThemeCssRules(YamiboAppTheme.BLUE_BLACK)
        )
    }

    @Test
    fun tealLightRecolorsDesktopRulesWithCurrentPalette() {
        val css = webThemeCssRules(YamiboAppTheme.TEAL_LIGHT).joinToString("\n")

        assertTrue(css.contains("#f8fbf9"))
        assertTrue(css.contains("#006b5b"))
        assertTrue(css.contains("#191c1b"))
        assertFalse(css.contains("#0d141d"))
        assertFalse(css.contains("#4ea1ff"))
    }

    @Test
    fun midnightPurpleRecolorsDesktopAndEditorRules() {
        val css = webThemeCssRules(YamiboAppTheme.MIDNIGHT_PURPLE).joinToString("\n")
        val editorCss = webThemeEditorCss(YamiboAppTheme.MIDNIGHT_PURPLE)

        assertTrue(css.contains("#141218"))
        assertTrue(css.contains("#cfbcff"))
        assertTrue(editorCss.contains("background:#1d1b20"))
        assertTrue(editorCss.contains("color:#cfbcff"))
        assertFalse(css.contains("#4ea1ff"))
    }
    @Test
    fun pureBlackRecolorsWebBackgroundAndEditor() {
        val css = webThemeCssRules(YamiboAppTheme.BLUE_BLACK, pureBlack = true)
            .joinToString("\n")
        val editorCss = webThemeEditorCss(YamiboAppTheme.BLUE_BLACK, pureBlack = true)

        assertTrue(css.contains("#000000"))
        assertTrue(editorCss.contains("background:#090909"))
        assertFalse(editorCss.contains("background:#182332"))
    }
}

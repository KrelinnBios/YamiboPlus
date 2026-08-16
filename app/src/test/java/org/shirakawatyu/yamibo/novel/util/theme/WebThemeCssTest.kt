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

    @Test
    fun lightThemeRecolorsNewlyMappedDarkBlocks() {
        // SAKURA（浅色）：新增映射的深色块应替换为主题浅色，不残留 #2a1b1f / rgba(51, 51, 51, 0.85) / #ff9a9a
        val css = webThemeCssRules(YamiboAppTheme.SAKURA).joinToString("\n")

        // #2a1b1f（锁定提示背景）→ surfaceContainerHigh(#f8e3e6)
        assertTrue(css.contains("#f8e3e6"))
        assertFalse(css.contains("#2a1b1f"))
        // rgba(51, 51, 51, 0.85)（浮动菜单）→ 半透明 surfaceContainerHigh，原串不再出现
        assertFalse(css.contains("rgba(51, 51, 51, 0.85)"))
        assertTrue(css.contains("rgba(248, 227, 230, 0.85)"))
        // #ff9a9a（锁定提示文字）→ error 色，原串不再出现
        assertFalse(css.contains("#ff9a9a"))
    }

    @Test
    fun darkThemeRecolorsNewlyMappedDarkBlocks() {
        // TEAL_DARK（深色）：新增映射应替换为墨绿深色对应色
        val css = webThemeCssRules(YamiboAppTheme.TEAL_DARK).joinToString("\n")

        assertFalse(css.contains("#2a1b1f"))
        assertFalse(css.contains("rgba(51, 51, 51, 0.85)"))
        assertFalse(css.contains("#ff9a9a"))
        // surfaceContainerHigh(#2a302e) 应出现在锁定提示与浮动菜单（半透明）处
        assertTrue(css.contains("#2a302e"))
        assertTrue(css.contains("rgba(42, 48, 46, 0.85)"))
    }

    @Test
    fun fallbackAlwaysReturnsNonEmptyRulesForAnyTheme() {
        // 回退守卫：任意主题、任意 pureBlack 组合均返回非空列表（WI3）
        YamiboAppTheme.entries.forEach { theme ->
            assertTrue(webThemeCssRules(theme, pureBlack = false).isNotEmpty())
            assertTrue(webThemeCssRules(theme, pureBlack = true).isNotEmpty())
        }
    }
}

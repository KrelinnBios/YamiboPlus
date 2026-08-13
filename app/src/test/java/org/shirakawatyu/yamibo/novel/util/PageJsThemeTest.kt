package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboAppTheme

class PageJsThemeTest {
    @Test
    fun htmlProxyUsesSelectedLightPalette() {
        val html = PageJsScripts.injectThemeCssIntoHtml(
            html = "<html><head></head><body class=\"pg_forumdisplay\"></body></html>",
            isDark = false,
            darkThemeId = 0,
            lightThemeId = 0,
            appTheme = YamiboAppTheme.SAKURA
        )

        assertTrue(html.contains("id=\"yamibo-light-mode\""))
        assertTrue(html.contains("#fff8f8"))
        assertTrue(html.contains("#b3325e"))
        assertFalse(html.contains("#0d141d"))
    }

    @Test
    fun runtimeInjectionUsesSelectedDarkPalette() {
        val script = PageJsScripts.getThemeSetJs(
            isDark = true,
            darkThemeId = 0,
            lightThemeId = 0,
            appTheme = YamiboAppTheme.TEAL_DARK
        )

        assertTrue(script.contains("#191c1b"))
        assertTrue(script.contains("#50dbc2"))
        assertFalse(script.contains("#4ea1ff"))
    }
}

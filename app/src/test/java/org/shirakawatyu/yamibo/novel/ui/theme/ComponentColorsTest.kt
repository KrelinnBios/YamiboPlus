package org.shirakawatyu.yamibo.novel.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentColorsTest {
    @Test
    fun everyTheme_hasDistinctChromeAndAlternatingRows() {
        YamiboAppTheme.entries.forEach { theme ->
            val colors = yamiboComponentColors(theme.scheme)

            assertNotEquals(theme.label, colors.topBarContainer, colors.contentBackground)
            assertNotEquals(theme.label, colors.bottomBarContainer, colors.contentBackground)
            assertNotEquals(theme.label, colors.baseRow, colors.alternateRow)
            assertEquals(theme.label, theme.scheme.primary, colors.destructiveIcon)
        }
    }

    @Test
    fun darkThemes_useLightTopBarText() {
        YamiboAppTheme.entries.filter(YamiboAppTheme::isDark).forEach { theme ->
            val colors = yamiboComponentColors(theme.scheme)
            assertTrue(theme.label, colors.topBarContent.luminance() > 0.5f)
        }
    }
}

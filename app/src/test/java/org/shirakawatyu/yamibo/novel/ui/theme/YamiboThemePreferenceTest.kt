package org.shirakawatyu.yamibo.novel.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboThemePreferenceTest {
    @Test
    fun systemModeFollowsSystemAppearance() {
        val preference = YamiboThemePreference(
            palette = YamiboThemePalette.TEAL,
            mode = YamiboThemeMode.SYSTEM
        )

        assertEquals(YamiboAppTheme.TEAL_LIGHT, preference.resolve(systemDark = false))
        assertEquals(YamiboAppTheme.TEAL_DARK, preference.resolve(systemDark = true))
    }

    @Test
    fun explicitModesIgnoreSystemAppearance() {
        val light = YamiboThemePreference(YamiboThemePalette.SAKURA, YamiboThemeMode.LIGHT)
        val dark = YamiboThemePreference(YamiboThemePalette.SAKURA, YamiboThemeMode.DARK)

        assertEquals(YamiboAppTheme.SAKURA, light.resolve(systemDark = true))
        assertEquals(YamiboAppTheme.SAKURA_DARK, dark.resolve(systemDark = false))
    }

    @Test
    fun legacyFixedThemeMigratesToMatchingPaletteAndMode() {
        val migrated = YamiboThemePreference.fromStored(
            paletteName = null,
            modeName = null,
            pureBlackValue = null,
            legacyThemeName = YamiboAppTheme.TEAL_DARK.name
        )

        assertEquals(YamiboThemePalette.TEAL, migrated.palette)
        assertEquals(YamiboThemeMode.DARK, migrated.mode)
        assertFalse(migrated.pureBlack)
    }

    @Test
    fun missingStoredThemeUsesClassicAndFollowsSystem() {
        val preference = YamiboThemePreference.fromStored(null, null, null, null)

        assertEquals(YamiboThemePalette.CLASSIC, preference.palette)
        assertEquals(YamiboThemeMode.SYSTEM, preference.mode)
    }

    @Test
    fun everyPaletteResolvesToPairedLightAndDarkThemes() {
        YamiboThemePalette.entries.forEach { palette ->
            assertFalse(palette.label, palette.resolve(isDark = false).isDark)
            assertTrue(palette.label, palette.resolve(isDark = true).isDark)
            assertNotEquals(palette.label, palette.lightTheme, palette.darkTheme)
        }
    }

    @Test
    fun pureBlackOnlyOverridesDarkSurfaceHierarchy() {
        val light = YamiboAppTheme.BLUE_LIGHT
        val dark = YamiboAppTheme.BLUE_BLACK

        assertEquals(light.scheme, light.effectiveScheme(pureBlack = true))
        assertEquals(Color.Black, dark.effectiveScheme(pureBlack = true).background)
        assertEquals(Color.Black, dark.effectiveScheme(pureBlack = true).surfaceContainerLowest)
        assertNotEquals(dark.scheme.background, dark.effectiveScheme(pureBlack = true).background)
    }

    @Test
    fun pureBlackUsesItsOwnDisplayLabel() {
        assertEquals("\u7EAF\u9ED1", YamiboThemeMode.DARK.displayLabel(pureBlack = true))
        assertEquals("\u6DF1\u8272", YamiboThemeMode.DARK.displayLabel(pureBlack = false))
    }
}
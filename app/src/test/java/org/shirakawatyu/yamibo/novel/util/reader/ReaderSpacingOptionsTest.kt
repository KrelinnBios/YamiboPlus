package org.shirakawatyu.yamibo.novel.util.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSpacingOptionsTest {
    @Test
    fun settingsHaveExpectedOptionCounts() {
        assertEquals(6, ReaderSpacingOptions.FONT_SIZES.size)
        assertEquals(6, ReaderSpacingOptions.LINE_HEIGHT_RATIOS.size)
        assertEquals(6, ReaderSpacingOptions.PAGE_PADDINGS.size)
    }

    @Test
    fun legacyValuesSnapToNearestNewOption() {
        assertEquals(16f, ReaderSpacingOptions.snapFontSize(14f))
        assertEquals(27f, ReaderSpacingOptions.snapFontSize(34f))
        assertEquals(43.2f, ReaderSpacingOptions.snapLineHeight(43f, 24f), 0.001f)
        assertEquals(4f, ReaderSpacingOptions.snapPadding(2f))
        assertEquals(24f, ReaderSpacingOptions.snapPadding(40f))
    }

    @Test
    fun lineHeightOptionsHaveVisibleSpacingAtDefaultFontSize() {
        val options = ReaderSpacingOptions.lineHeights(24f)

        assertEquals(28.8f, options[0], 0.001f)
        assertEquals(31.68f, options[1], 0.001f)
        assertEquals(34.56f, options[2], 0.001f)
        assertEquals(37.44f, options[3], 0.001f)
        assertEquals(40.32f, options[4], 0.001f)
        assertEquals(43.2f, options[5], 0.001f)
    }
}

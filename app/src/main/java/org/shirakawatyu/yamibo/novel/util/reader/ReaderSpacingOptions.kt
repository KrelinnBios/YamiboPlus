package org.shirakawatyu.yamibo.novel.util.reader

import kotlin.math.abs

/**
 * 小说阅读器排版设置的固定档位。
 *
 * 所有入口共用这里的固定档位；读取旧版本连续滑块保存的数值时，会吸附到最近档位。
 */
object ReaderSpacingOptions {
    val FONT_SIZES = listOf(16f, 18f, 20f, 22f, 24f, 27f)
    val LINE_HEIGHT_RATIOS = listOf(1.2f, 1.32f, 1.44f, 1.56f, 1.68f, 1.8f)
    val PAGE_PADDINGS = listOf(4f, 8f, 12f, 16f, 20f, 24f)

    fun lineHeights(fontSize: Float): List<Float> =
        LINE_HEIGHT_RATIOS.map { ratio -> fontSize * ratio }

    fun snapFontSize(value: Float): Float = nearest(FONT_SIZES, value)

    fun snapLineHeight(value: Float, fontSize: Float): Float =
        nearest(lineHeights(fontSize), value)

    fun snapPadding(value: Float): Float = nearest(PAGE_PADDINGS, value)

    private fun nearest(options: List<Float>, value: Float): Float =
        options.minBy { option -> abs(option - value) }
}

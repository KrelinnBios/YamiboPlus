package org.shirakawatyu.yamibo.novel.util.reader

/**
 * 小说楼层「章节标题」识别。
 *
 * 译者分章的写法很杂，这里集中处理两类纯文本标题（结构性标题——居中/大字号/标题标签——仍在
 * ReaderVM 里按 jsoup 节点判断）：
 *
 * 1. 中文编号标题：「序章 / 楔子 / 第N章 / 第N话…」，多见于直接写「第一章 造化弄人」的小说。
 * 2. 英文分话标记：「Episode / Teresa / Intermission / Extra + 编号」以及「Epilogue / Prologue」。
 *    这类标记常被作者放进 <strong> 或 <div align=left> 里、且紧跟正文（标记与正文之间没有换行），
 *    经 HTMLUtil.toText 后会和正文挤在同一行，故按「行首」匹配并只取标记本身作为标题。
 *    编号形态多样：1、5-10、2-3.5、7-7.5、3-Teresa（如《如果你愿意成为我的朋友》的目录）。
 *
 * 抽成独立对象便于单元测试，并供「整帖目录」构建复用。
 */
object NovelChapterHeading {

    private val WHITESPACE = Regex("\\s+")

    /** 中文编号标题：序章 / 楔子 / 第N章 / 第N话… */
    val chineseHeadingRegex = Regex(
        """^(?:序章|楔子|引子|尾声|尾聲|后记|後記|番外|第[零〇一二三四五六七八九十百千万兩两\d]+(?:章|节|節|卷|篇|幕|话|話|回))"""
    )

    /**
     * 英文分话标记：
     * - episode/ep/chapter/teresa/intermission/extra + 编号（编号可含 - 和 . 以及「-teresa」）
     * - epilogue/prologue（无编号）
     */
    val englishHeadingRegex = Regex(
        """^(?:(?:episode|ep|chapter|teresa|intermission|extra)\s*\d+(?:\s*[.\-]\s*(?:\d+|teresa))*|(?:epilogue|prologue)\b)""",
        RegexOption.IGNORE_CASE
    )

    /** 楼层首行（已折叠空白）命中中文编号标题 → 返回标题（截断 30 字），否则 null。 */
    fun chineseHeading(firstLineNormalized: String): String? {
        if (firstLineNormalized.isBlank()) return null
        if (!chineseHeadingRegex.containsMatchIn(firstLineNormalized)) return null
        return firstLineNormalized.take(30)
    }

    /**
     * 扫描楼层正文前几行，命中英文分话标记 → 返回标记本身（折叠空白），否则 null。
     * 只看前几行，避免命中首楼自动生成目录里的「Episode N」链接列表。
     */
    fun englishHeading(postText: String): String? {
        if (postText.isBlank()) return null
        postText.lineSequence()
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .forEach { line ->
                englishHeadingRegex.find(line)?.let { match ->
                    return match.value.replace(WHITESPACE, " ").trim()
                }
            }
        return null
    }
}

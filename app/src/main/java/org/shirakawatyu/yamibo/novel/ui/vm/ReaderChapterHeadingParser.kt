package org.shirakawatyu.yamibo.novel.ui.vm

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.shirakawatyu.yamibo.novel.util.reader.HTMLUtil

private val readerDateHeadingRegex = Regex(
    """^(?:(?:现在|如今|当时|过去|未来|[零〇一二三四五六七八九十百\d]+年(?:前|后))\s*[-—–~～：:]\s*)?\d{3,4}\s*年\s*\d{1,2}\s*月(?:\s*\d{1,2}\s*[日号])?$"""
)
private val readerTextChapterHeadingRegex = Regex(
    """^(?:序章|楔子|引子|尾声|尾聲|后记|後記|番外|第[零〇一二三四五六七八九十百千万兩两\d]+(?:章|节|節|卷|篇|幕|话|話|回))"""
)
private const val readerStructuralHeadingSelector =
    "h1, h2, h3, center, div[align=center], font[size=4], font[size=5], font[size=6], font[size=7]"
private val readerHeadingSkipAncestors = setOf("li", "a", "ul", "ol", "table")
private val readerNumberedChapterLineRegex = Regex("""^\s*(\d{1,2})(?:\s+.*)?$""")
private val readerEmbeddedChineseChapterHeadingRegex = Regex(
    """^(?:[零〇一二三四五六七八九十百千万兩两\d]{1,8}[、，,.．]\s*\S.+|第[零〇一二三四五六七八九十百千万兩两\d]+其[零〇一二三四五六七八九十百千万兩两\d]+[：:]\s*\S.+)$"""
)
private val readerHeadingTrailingDeny = "。！？!?".toSet()
private val readerTitleContinuationPunctuation = "、，,；;：:".toSet()
private const val readerEmbeddedHeadingStartMarker = "|||YAMIBO_CHAPTER_START|||"
private const val readerEmbeddedHeadingEndMarker = "|||YAMIBO_CHAPTER_END|||"
private const val readerEmbeddedHeadingSelector =
    "strong, b, h1, h2, h3, h4, center, div[align=center], font[size=4], font[size=5], font[size=6], font[size=7]"
private val readerDirectoryLabelRegex = Regex("""(?:目录|目錄)""")
private val readerLinkedPostTidRegex = Regex("""(?:[?&](?:ptid|tid)=(\d+))""")
private val readerLinkedPostPidRegex = Regex("""(?:[?&]pid=(\d+)|#pid(\d+))""")

internal data class ReaderNumberedChapterSegment(
    val text: String,
    val title: String?
)

internal fun containsReaderChapterStart(
    segments: List<ReaderNumberedChapterSegment>
): Boolean = segments.any { it.title != null }

/**
 * 识别一楼中由作者维护的本帖楼层目录。
 *
 * 目录里的“第一章/第二章”和更新说明只是导航分组，不是正文章节；真正章节会在后续楼层
 * 再按 pid 与楼层标题解析。必须同时出现目录文字和至少两个指向当前帖不同 pid 的链接，
 * 避免普通正文里提到“目录”或偶尔引用一个楼层时被误判。
 */
internal fun isReaderLinkedChapterDirectory(node: Element, threadId: String): Boolean {
    if (threadId.isBlank() || !readerDirectoryLabelRegex.containsMatchIn(node.text())) return false

    val linkedPostIds = node.select("a[href]")
        .asSequence()
        .mapNotNull { link ->
            val href = link.attr("href").replace("&amp;", "&")
            val linkedTid = readerLinkedPostTidRegex.find(href)?.groupValues?.get(1)
            val pidMatch = readerLinkedPostPidRegex.find(href) ?: return@mapNotNull null
            val linkedPid = pidMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            linkedPid?.takeIf { linkedTid == threadId }
        }
        .distinct()
        .take(2)
        .count()

    return linkedPostIds >= 2
}

private fun normalizeReaderHeading(value: String): String = value
    .replace(' ', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()
    .replace(Regex("^[#＃]+\\s*"), "")
    .trim()

/** 带 href 的链接属于购买、引用或楼层导航，不作为正文开头的章节标题。 */
internal fun isReaderLinkedTitleElement(element: Element): Boolean {
    return (element.tagName().equals("a", ignoreCase = true) && element.hasAttr("href")) ||
        element.selectFirst("a[href]") != null
}

/** 以连接性标点结尾的短句仍是正文片段，不作为章节标题。 */
internal fun endsWithReaderTitleContinuationPunctuation(value: String): Boolean {
    return value.lastOrNull() in readerTitleContinuationPunctuation
}

/** 提取楼层首行的“第 N 话/番外/后记”等章节标题，并兼容作者使用的 Markdown # 前缀。 */
internal fun extractReaderTextChapterHeading(firstLine: String): String? {
    val candidate = normalizeReaderHeading(firstLine)
    return candidate.takeIf {
        it.isNotBlank() && readerTextChapterHeadingRegex.containsMatchIn(it)
    }
}

/**
 * 提取作者留言分隔线后的正式章节标题。
 *
 * 有些连载楼层会先写更新说明或致谢，再用 `<hr>` 分隔正文；真正的“第 N 章/序章/番外”
 * 位于分隔线之后。只扫描分隔线后的前几行，避免把后续正文里提到的章节号误当标题。
 */
internal fun extractReaderChapterHeadingAfterDivider(node: Element): String? {
    return node.select("hr")
        .asSequence()
        .firstNotNullOfOrNull { divider ->
            val followingHtml = buildString {
                var sibling = divider.nextSibling()
                while (sibling != null) {
                    append(sibling.outerHtml())
                    sibling = sibling.nextSibling()
                }
            }
            HTMLUtil.toText(followingHtml)
                .lineSequence()
                .map(::normalizeReaderHeading)
                .filter(String::isNotBlank)
                .take(8)
                .firstNotNullOfOrNull(::extractReaderTextChapterHeading)
        }
}

/**
 * 从居中、大字号或标题标签中提取短标题。
 *
 * 不能只看第一个匹配元素：Discuz 常用一个居中/大字号父容器包住整章，真正标题在其子元素中。
 */
internal fun extractReaderStructuralHeading(node: Element): String? {
    return node.select(readerStructuralHeadingSelector)
        .asSequence()
        .filterNot { element ->
            element.parents().any { it.tagName() in readerHeadingSkipAncestors }
        }
        .map { normalizeReaderHeading(it.text()) }
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                candidate.length <= 24 &&
                candidate.first().isLetterOrDigit() &&
                candidate.last() !in readerHeadingTrailingDeny
        }
}

/**
 * 提取楼层顶部的日期式章节标题。
 *
 * 只接受整行时间标题，例如“现在 - 2044 年 1 月”或“三年前 - 2041 年 5 月”；
 * 正文句子中即使包含年月日期，也不能被当成章节标题。
 */
internal fun extractReaderDateSubHeading(node: Element): String? {
    return node.select("p, div, center, h1, h2, h3, h4, strong, b, font")
        .firstNotNullOfOrNull { element ->
            if (element.parents().any { it.tagName() in readerHeadingSkipAncestors }) {
                return@firstNotNullOfOrNull null
            }
            element.text()
                .replace(' ', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { candidate ->
                    candidate.isNotBlank() &&
                        candidate.length <= 24 &&
                        readerDateHeadingRegex.matches(candidate)
                }
        }
}

/** 将楼主正文中独立的数字章节（如“1”或“2 一边这样想着……”）拆成独立段落。 */
internal fun splitReaderNumberedChapterSegments(text: String): List<ReaderNumberedChapterSegment> {
    val segments = mutableListOf<ReaderNumberedChapterSegment>()
    val currentLines = mutableListOf<String>()
    var currentTitle: String? = null

    fun appendCurrentSegment() {
        val segmentText = currentLines.joinToString("\n").trim()
        if (segmentText.isNotBlank()) {
            segments += ReaderNumberedChapterSegment(segmentText, currentTitle)
        }
        currentLines.clear()
    }

    text.lines().forEach { line ->
        val match = readerNumberedChapterLineRegex.matchEntire(line.trim())
        if (match != null) {
            appendCurrentSegment()
            currentTitle = match.groupValues[1]
        }
        currentLines += line
    }
    appendCurrentSegment()

    return segments.ifEmpty {
        listOf(ReaderNumberedChapterSegment(text.trim(), null))
    }
}

/**
 * 在楼层 HTML 中标记所有独立的章节标题元素。
 *
 * Discuz 帖子常在同一楼层连续发布多章，且标题由多层 strong/font 嵌套。这里只标记最外层
 * 的短标题元素，避免同一标题被重复拆分；标记会在正文统一简繁转换后再解析。
 */
internal fun markReaderEmbeddedChapterHeadings(
    node: Element,
    linkedDirectoryThreadId: String? = null
): Element {
    val clone = node.clone()
    if (
        linkedDirectoryThreadId != null &&
        isReaderLinkedChapterDirectory(clone, linkedDirectoryThreadId)
    ) {
        return clone
    }
    clone.select(readerEmbeddedHeadingSelector)
        .asSequence()
        .map { element -> element to normalizeReaderHeading(element.text()) }
        .filter { (_, candidate) -> isReaderEmbeddedChapterHeading(candidate) }
        .filterNot { (element, _) ->
            element.parents().any { parent ->
                parent.tagName() in readerHeadingSkipAncestors ||
                    (parent != clone &&
                        parent.`is`(readerEmbeddedHeadingSelector) &&
                        isReaderEmbeddedChapterHeading(normalizeReaderHeading(parent.text())))
            }
        }
        .toList()
        .forEach { (element, _) ->
            element.before(TextNode(readerEmbeddedHeadingStartMarker))
            element.after(TextNode(readerEmbeddedHeadingEndMarker))
        }
    return clone
}

/** 将带标记的单个楼层正文拆成多个章节，并继续兼容原有的独立阿拉伯数字章节。 */
internal fun splitReaderEmbeddedChapterSegments(text: String): List<ReaderNumberedChapterSegment> {
    if (!text.contains(readerEmbeddedHeadingStartMarker)) {
        return splitReaderNumberedChapterSegments(text)
    }

    val structuralSegments = mutableListOf<ReaderNumberedChapterSegment>()
    var cursor = 0
    while (cursor < text.length) {
        val headingStart = text.indexOf(readerEmbeddedHeadingStartMarker, cursor)
        if (headingStart < 0) {
            val tail = text.substring(cursor).trim()
            if (tail.isNotBlank()) {
                structuralSegments += ReaderNumberedChapterSegment(tail, null)
            }
            break
        }

        val preface = text.substring(cursor, headingStart).trim()
        if (preface.isNotBlank()) {
            structuralSegments += ReaderNumberedChapterSegment(preface, null)
        }

        val titleStart = headingStart + readerEmbeddedHeadingStartMarker.length
        val titleEnd = text.indexOf(readerEmbeddedHeadingEndMarker, titleStart)
        if (titleEnd < 0) {
            val unparsed = text.substring(headingStart).trim()
            if (unparsed.isNotBlank()) {
                structuralSegments += ReaderNumberedChapterSegment(unparsed, null)
            }
            break
        }

        val title = normalizeReaderHeading(text.substring(titleStart, titleEnd))
        val bodyStart = titleEnd + readerEmbeddedHeadingEndMarker.length
        val nextHeadingStart = text.indexOf(readerEmbeddedHeadingStartMarker, bodyStart)
            .takeIf { it >= 0 } ?: text.length
        val body = buildString {
            append(title)
            val remainder = text.substring(bodyStart, nextHeadingStart).trim()
            if (remainder.isNotBlank()) {
                append('\n')
                append(remainder)
            }
        }.trim()

        if (body.isNotBlank()) {
            structuralSegments += ReaderNumberedChapterSegment(body, title)
        }
        cursor = nextHeadingStart
    }

    return structuralSegments.flatMap { structural ->
        val numberedSegments = splitReaderNumberedChapterSegments(structural.text)
        numberedSegments.mapIndexed { index, numbered ->
            if (index == 0 && numbered.title == null && structural.title != null) {
                numbered.copy(title = structural.title)
            } else {
                numbered
            }
        }
    }.ifEmpty {
        listOf(ReaderNumberedChapterSegment(text.trim(), null))
    }
}

private fun isReaderEmbeddedChapterHeading(candidate: String): Boolean {
    if (candidate.isBlank() || candidate.length > 40) return false
    return extractReaderTextChapterHeading(candidate) != null ||
        readerEmbeddedChineseChapterHeadingRegex.matches(candidate)
}

package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedReaderThreadPage(
    val combinedHtml: String,
    val title: String?,
    val author: String?,
    val authorId: String?,
    val section: String?,
    val maxPage: Int,
    val accessDeniedReason: String?
)

data class ParsedMangaThreadPage(
    val imageUrls: List<String>,
    val title: String?,
    val authorId: String?,
    val compatibleHtml: String,
    val accessDeniedReason: String?
)

object ThreadHtmlParser {
    private const val SUBSTANTIVE_READER_TEXT_LENGTH = 240
    private val chapterHeadingRegex = Regex(
        """^(?:序章|楔子|引子|尾声|尾聲|后记|後記|番外|第[零〇一二三四五六七八九十百千万兩两\d]+(?:章|节|節|卷|篇|幕|话|話|回))(?=\s|$)"""
    )
    private val ignoredImageParts = listOf(
        "smiley",
        "avatar",
        "static/image",
        "template/",
        "/common/",
        "/block/"
    )

    fun parseReaderPage(
        html: String,
        baseUrl: String,
        expectedAuthorId: String? = null
    ): ParsedReaderThreadPage {
        val document = Jsoup.parse(html, baseUrl)
        val allMessages = messageElements(document)
        val authorId = expectedAuthorId
            ?: allMessages.firstNotNullOfOrNull(::extractMessageAuthorId)
            ?: extractAuthorId(document)
        val messages = filterAuthorMessages(allMessages, authorId)
        val readerMessages = selectReaderMessages(messages)

        val combinedHtml = readerMessages.joinToString("") { message ->
            val postId = message.id().removePrefix("postmessage_")
            """<div class="message" data-post-id="$postId">${message.html()}</div>"""
        }

        return ParsedReaderThreadPage(
            combinedHtml = combinedHtml,
            title = document.selectFirst("#thread_subject")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?: document.title().substringBefore(" - 百合会").trim().takeIf(String::isNotBlank),
            author = document.selectFirst(".authi a.xw1, .authi a[href*='space-uid-']")?.text()?.trim()
                ?.takeIf(String::isNotBlank),
            authorId = authorId,
            section = extractSection(document),
            maxPage = extractMaxPage(document),
            accessDeniedReason = extractAccessDeniedReason(document)
        )
    }

    fun parseMangaPage(
        html: String,
        baseUrl: String,
        expectedAuthorId: String? = null
    ): ParsedMangaThreadPage {
        val document = Jsoup.parse(html, baseUrl)
        val allMessages = messageElements(document)
        val authorId = expectedAuthorId
            ?: allMessages.firstNotNullOfOrNull(::extractMessageAuthorId)
            ?: extractAuthorId(document)
        val messages = filterAuthorMessages(allMessages, authorId)
        val seen = linkedSetOf<String>()

        messages.forEach { message ->
            message.select("img").forEach imageLoop@{ image ->
                val url = imageUrl(image) ?: return@imageLoop
                if (ignoredImageParts.none { url.contains(it, ignoreCase = true) }) {
                    seen += url
                }
            }
        }

        val compatibleHtml = messages.joinToString("") { source ->
            """<div class="message">${source.html()}</div>"""
        }

        return ParsedMangaThreadPage(
            imageUrls = seen.toList(),
            title = document.selectFirst("#thread_subject")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?: document.title().substringBefore(" - 百合会").trim().takeIf(String::isNotBlank),
            authorId = authorId,
            compatibleHtml = compatibleHtml,
            accessDeniedReason = extractAccessDeniedReason(document)
        )
    }

    private fun messageElements(document: Document): List<Element> {
        val desktopMessages = document.select("td.t_f[id^=postmessage_]")
        if (desktopMessages.isNotEmpty()) return desktopMessages

        val mobileMessages = document.select(".message")
        if (mobileMessages.isNotEmpty()) return mobileMessages

        return document.select(".postmessage")
    }

    private fun filterAuthorMessages(
        messages: List<Element>,
        authorId: String?
    ): List<Element> {
        if (authorId.isNullOrBlank()) return messages
        val filtered = messages.filter { message ->
            val messageAuthorId = extractMessageAuthorId(message)
            messageAuthorId == null || messageAuthorId == authorId
        }
        return filtered.ifEmpty { messages }
    }

    private fun selectReaderMessages(messages: List<Element>): List<Element> {
        if (messages.isEmpty()) return emptyList()

        val candidates = messages.map { source ->
            val message = source.clone()
            message.select(".locked-tip, .pstatus, .quote").remove()
            val normalizedText = message.wholeText()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            val lockedContentLength = message.select(".locked-content").text().trim().length
            ReaderMessageCandidate(
                message = message,
                textLength = normalizedText.length,
                hasChapterHeading = chapterHeadingRegex.containsMatchIn(normalizedText.take(80)),
                hasSubstantiveLockedContent = lockedContentLength >= 80,
                isThreadStarter = isThreadStarterMessage(source)
            )
        }

        val hasStructuredInstallments = candidates.any { candidate ->
            candidate.hasChapterHeading ||
                candidate.hasSubstantiveLockedContent ||
                candidate.textLength >= SUBSTANTIVE_READER_TEXT_LENGTH
        }
        if (!hasStructuredInstallments) return candidates.map(ReaderMessageCandidate::message)

        return candidates.filter { candidate ->
            candidate.hasChapterHeading ||
                candidate.hasSubstantiveLockedContent ||
                candidate.textLength >= SUBSTANTIVE_READER_TEXT_LENGTH ||
                (candidate.isThreadStarter && candidate.textLength >= 10)
        }.map(ReaderMessageCandidate::message)
            .ifEmpty { candidates.map(ReaderMessageCandidate::message) }
    }

    private fun isThreadStarterMessage(message: Element): Boolean {
        val postContainer = message.parents().firstOrNull { parent ->
            parent.id().startsWith("post_")
        } ?: return false
        return postContainer.selectFirst("a[id^=postnum] em")?.text()?.trim() == "1"
    }

    private fun extractMessageAuthorId(message: Element): String? {
        val postContainer = message.parents().firstOrNull { parent ->
            parent.id().startsWith("post_")
        } ?: message
        return extractAuthorId(postContainer)
    }

    private fun extractAuthorId(root: Element): String? {
        root.select("a[href*='authorid='], a[href*='space-uid-'], a[href*='uid=']")
            .forEach { link ->
                val href = link.attr("href")
                Regex("[?&]authorid=(\\d+)").find(href)?.groupValues?.getOrNull(1)?.let {
                    return it
                }
                Regex("space-uid-(\\d+)").find(href)?.groupValues?.getOrNull(1)?.let {
                    return it
                }
                Regex("[?&]uid=(\\d+)").find(href)?.groupValues?.getOrNull(1)?.let {
                    return it
                }
            }
        return null
    }

    private fun extractSection(document: Document): String? {
        val candidates = document.select(
            "#pt a, .header h2 a, .z a[href*='forum-'], a[href*='mod=forumdisplay']"
        )
        return candidates.asSequence()
            .map { it.text().trim() }
            .firstOrNull { name ->
                name.contains("文学") ||
                    name.contains("文學") ||
                    name.contains("小说") ||
                    name.contains("小說") ||
                    name.contains("漫画") ||
                    name.contains("漫畫")
            }
    }

    private fun extractMaxPage(document: Document): Int {
        val candidates = mutableListOf(1)

        document.select("select option, .pg a, .pg strong").forEach { element ->
            element.text().trim().toIntOrNull()?.let(candidates::add)
            Regex("[?&]page=(\\d+)").find(element.attr("value"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.let(candidates::add)
            Regex("[?&]page=(\\d+)").find(element.attr("href"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.let(candidates::add)
        }

        document.select(".pg label span, .pg label, .page").forEach { element ->
            Regex("(\\d+)\\s*页").findAll(element.text()).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let(candidates::add)
            }
        }

        return candidates.maxOrNull() ?: 1
    }

    private fun extractAccessDeniedReason(document: Document): String? {
        val phrases = listOf(
            "阅读权限",
            "無權訪問",
            "无权访问",
            "權限不足",
            "权限不足",
            "您所在的用户组",
            "您所在的用戶組",
            "抱歉，您没有权限",
            "抱歉，您沒有權限"
        )
        val candidateText = document.select(
            "#messagetext, .showmessage, .alert_error, .nfl .f_c"
        ).joinToString(" ") { it.text().trim() }
        if (candidateText.isBlank()) return null
        return candidateText.takeIf { content ->
            phrases.any { phrase -> content.contains(phrase, ignoreCase = true) }
        }?.take(160)
    }

    private fun imageUrl(image: Element): String? {
        val attribute = listOf("zoomfile", "file", "zsrc", "data-src", "src")
            .firstOrNull { image.hasAttr(it) && image.attr(it).isNotBlank() }
            ?: return null
        val raw = image.attr(attribute).trim()
        if (raw.startsWith("data:") || raw.startsWith("blob:")) return null
        return image.absUrl(attribute).ifBlank { raw }
    }

    private data class ReaderMessageCandidate(
        val message: Element,
        val textLength: Int,
        val hasChapterHeading: Boolean,
        val hasSubstantiveLockedContent: Boolean,
        val isThreadStarter: Boolean
    )
}

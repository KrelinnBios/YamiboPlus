package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.shirakawatyu.yamibo.novel.bean.space.BlogComment
import org.shirakawatyu.yamibo.novel.bean.space.BlogContentBlock
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail

/** 电脑版空间日志解析器。权限由页面实际输出的操作链接决定。 */
object SpaceDesktopParser {
    private const val ORIGIN = "https://bbs.yamibo.com"
    private val datePattern = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}")

    fun parseBlogDetail(html: String, url: String): BlogDetail {
        val document = Jsoup.parse(html, ORIGIN)
        val article = document.selectFirst("#blog_article")
            ?: throw IllegalStateException("日志内容为空")
        val authorLink = document.selectFirst("#pcd .hm a[href*='space-uid-']")
        val ownerUid = extractUid(authorLink?.attr("href").orEmpty())
            ?: Regex("blog-(\\d+)-\\d+")
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        val blogId = Regex("blog-\\d+-(\\d+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("[?&]id=(\\d+)")
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        val title = document.selectFirst(".vw .ph")?.text()?.trim().orEmpty()
        val category = document.selectFirst(".vw .h p.xg2 a")?.text()?.trim().orEmpty()
        val metaText = document.selectFirst(".vw .h p.xg2")?.text().orEmpty()
        val viewCount = Regex("已有\\s*(\\d+)\\s*次阅读")
            .find(metaText)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val time = datePattern.find(metaText)?.value.orEmpty()
        val comments = parseComments(document)
        val actionLinks = document.select(".vw .o a")
        val commentForm = document.selectFirst("form[id^=quickcommentform]")
        val commentReferer = commentForm
            ?.selectFirst("input[name=referer]")
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::absoluteUrl)
            ?: url

        return BlogDetail(
            blogId = blogId,
            ownerUid = ownerUid,
            category = category,
            title = title,
            authorName = authorLink?.text()?.trim().orEmpty(),
            authorAvatarUrl = document.selectFirst("#pcd .hm img")?.let { avatarUrl(it) },
            time = time,
            viewCount = viewCount,
            commentCount = document.selectFirst("#comment_replynum")?.text()?.trim().orEmpty(),
            blocks = parseBlogBlocks(article),
            comments = comments,
            favoriteUrl = actionLinks.firstOrNull { it.id() == "a_favorite" }?.absUrl("href").orEmpty(),
            shareUrl = actionLinks.firstOrNull { it.id() == "a_share" }?.absUrl("href").orEmpty(),
            inviteUrl = actionLinks.firstOrNull { it.id() == "a_invite" }?.absUrl("href").orEmpty(),
            editUrl = actionLinks.firstOrNull { it.text().trim() == "编辑" }?.absUrl("href").orEmpty(),
            deleteUrl = actionLinks.firstOrNull { it.id().contains("delete") }?.absUrl("href").orEmpty(),
            reportUrl = actionLinks.firstOrNull { it.text().trim() == "举报" }
                ?.let { actionUrl(it) }
                .orEmpty(),
            commentFormUrl = commentForm?.absUrl("action").orEmpty(),
            commentFormHash = commentForm
                ?.selectFirst("input[name=formhash]")
                ?.attr("value")
                ?.trim()
                .orEmpty(),
            commentReferer = commentReferer
        )
    }

    private fun parseComments(document: Document): List<BlogComment> =
        document.select("#comment_ul dl").mapNotNull { item ->
            val id = Regex("comment_(\\d+)_li")
                .find(item.id())
                ?.groupValues
                ?.getOrNull(1)
                ?: return@mapNotNull null
            val authorLink = item.selectFirst("dt a[id^=author_]")
            val authorUid = extractUid(authorLink?.attr("href").orEmpty()).orEmpty()
            val contentElement = item.children().firstOrNull { child ->
                child.tagName() == "dd" && child.id().startsWith("comment_")
            }
            val content = contentElement
                ?.clone()
                ?.apply { select(".quote").remove() }
                ?.text()
                ?.trim()
                .orEmpty()
            val actions = item.select("dt span.y a")
            BlogComment(
                id = id,
                authorName = authorLink?.text()?.trim().orEmpty(),
                authorUid = authorUid,
                avatarUrl = item.selectFirst("dd.avt img")?.let { avatarUrl(it) },
                time = item.selectFirst("dt span.xg1")?.text()?.trim().orEmpty(),
                content = content,
                replyUrl = actions.firstOrNull { it.text().trim() == "回复" }
                    ?.absUrl("href")
                    .orEmpty(),
                editUrl = actions.firstOrNull { it.text().trim() == "编辑" }
                    ?.absUrl("href")
                    .orEmpty(),
                deleteUrl = actions.firstOrNull { it.text().trim() == "删除" }
                    ?.absUrl("href")
                    .orEmpty()
            )
        }

    private fun parseBlogBlocks(message: Element): List<BlogContentBlock> {
        val blocks = mutableListOf<BlogContentBlock>()
        message.children().forEach { child ->
            when (child.tagName().lowercase()) {
                "img" -> child.attr("abs:src").takeIf(String::isNotBlank)?.let {
                    blocks += BlogContentBlock.Image(it)
                }
                "blockquote" -> child.text().trim().takeIf(String::isNotBlank)?.let {
                    blocks += BlogContentBlock.Quote(it)
                }
                else -> {
                    child.select("img").forEach { image ->
                        image.attr("abs:src").takeIf(String::isNotBlank)?.let {
                            blocks += BlogContentBlock.Image(it)
                        }
                    }
                    child.text().trim().takeIf(String::isNotBlank)?.let {
                        blocks += BlogContentBlock.Text(it)
                    }
                }
            }
        }
        return blocks
    }

    private fun extractUid(href: String): String? =
        Regex("space-uid-(\\d+)")
            .find(href)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("[?&]uid=(\\d+)")
                .find(href)
                ?.groupValues
                ?.getOrNull(1)

    private fun avatarUrl(image: Element): String? =
        image.absUrl("src").takeIf(String::isNotBlank)
            ?: image.absUrl("zsrc").takeIf(String::isNotBlank)

    private fun absoluteUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "$ORIGIN/${trimmed.trimStart('/')}"
    }

    private fun actionUrl(link: Element): String {
        val href = link.absUrl("href")
        if (href.isNotBlank() && !href.startsWith("javascript:", ignoreCase = true)) return href
        val onclick = link.attr("onclick")
        val match = Regex("['\"]((?:misc|home)\\.php\\?[^'\"]+)['\"]")
            .find(onclick)
            ?.groupValues
            ?.getOrNull(1)
        return match?.let(::absoluteUrl).orEmpty()
    }
}

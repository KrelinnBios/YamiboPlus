package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.shirakawatyu.yamibo.novel.bean.space.BlogComment
import org.shirakawatyu.yamibo.novel.bean.space.BlogContentBlock
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.bean.space.DoingComment
import org.shirakawatyu.yamibo.novel.bean.space.SpaceCategory
import org.shirakawatyu.yamibo.novel.bean.space.SpaceFriendFilter
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListPage
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.util.AppErrorLog

/** 电脑版空间日志解析器。权限由页面实际输出的操作链接决定。 */
object SpaceDesktopParser {
    private const val ORIGIN = "https://bbs.yamibo.com"
    private val datePattern = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}")

    fun parseListPage(kind: SpacePageKind, html: String): SpaceListPage {
        val document = Jsoup.parse(html, ORIGIN)
        val items = when (kind) {
            SpacePageKind.BLOG -> parseBlogList(document)
            SpacePageKind.DOING -> parseDoingList(document)
            SpacePageKind.FRIEND -> parseFriendList(document)
            SpacePageKind.USER_THREAD -> parseThreadList(document)
            SpacePageKind.PRIVATE_MESSAGE -> parseMessageList(document)
            SpacePageKind.NOTICE -> parseNoticeList(document)
        }
        return SpaceListPage(
            items = items,
            previousUrl = pageUrl(document, true),
            nextUrl = pageUrl(document, false),
            categories = document.select("a[href*='classid=']").mapNotNull { link ->
                Regex("[?&]classid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                    ?.let { SpaceCategory(it, link.text().trim()) }
            }.filter { it.name.isNotBlank() }.distinctBy { it.id },
            friendFilters = document.select("select[name=fuidsel] option[value]").mapNotNull { option ->
                val uid = option.attr("value")
                if (uid.matches(Regex("[1-9]\\d*"))) SpaceFriendFilter(uid, option.text().trim()) else null
            }.filter { it.name.isNotBlank() }.distinctBy { it.uid }
        )
    }

    private fun parseBlogList(document: Document): List<SpaceListItem> =
        document.select(".xld > dl.bbda").mapNotNull { item ->
            val link = item.selectFirst("dt a[href*='blog-']") ?: return@mapNotNull null
            val id = Regex("blog-\\d+-(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            SpaceListItem.Blog(
                blogId = id,
                title = link.text().trim(),
                category = "",
                time = item.selectFirst("dd .xg1")?.text()?.trim().orEmpty(),
                summary = item.selectFirst("dd[id^=blog_article_]")?.text()?.trim().orEmpty(),
                authorName = item.selectFirst("dd a[href*='space-uid-']")?.text()?.trim().orEmpty(),
                url = link.absUrl("href")
            )
        }

    private fun parseDoingList(document: Document): List<SpaceListItem> =
        document.select(".doing_list_box li.doing_list_li, .xld dl").mapNotNull { item ->
            val user = item.selectFirst("a[href*='space-uid-'], a[href*='uid=']") ?: return@mapNotNull null
            val uid = extractUid(user.attr("href")) ?: return@mapNotNull null
            SpaceListItem.Doing(
                uid = uid,
                name = user.text().trim(),
                avatarUrl = item.selectFirst("img")?.let { avatarUrl(it) },
                time = item.selectFirst(".xg1, .mtime")?.text()?.trim().orEmpty(),
                content = item.selectFirst(".do_comment, dd.cl, .message")?.text()?.trim().orEmpty(),
                comments = emptyList(),
                spaceUrl = user.absUrl("href")
            )
        }

    private fun parseMessageList(document: Document): List<SpaceListItem> =
        document.select("#pmlist li, .xld dl, .imglist li").mapNotNull { item ->
            val link = item.selectFirst("a[href*='subop=view'], a[href*='touid=']") ?: return@mapNotNull null
            val uid = Regex("[?&]touid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            SpaceListItem.PrivateMessage(
                touid = uid,
                name = item.selectFirst(".xw1, .mtit, dt a")?.text()?.trim().orEmpty(),
                time = item.selectFirst(".xg1, .mtime")?.text()?.trim().orEmpty(),
                summary = item.selectFirst(".xg1 + *, .mtxt, dd")?.text()?.trim().orEmpty(),
                avatarUrl = item.selectFirst("img")?.let { avatarUrl(it) },
                url = link.absUrl("href")
            )
        }

    private fun parseNoticeList(document: Document): List<SpaceListItem> =
        document.select(".nts dl, .xld dl, .imglist li").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            SpaceListItem.Notice(
                title = item.selectFirst(".xw1, .mtit, dt a")?.text()?.trim().orEmpty(),
                time = item.selectFirst(".xg1, .mtime")?.text()?.trim().orEmpty(),
                summary = item.selectFirst(".ntc_body, .mtxt, dd")?.text()?.trim().orEmpty(),
                avatarUrl = item.selectFirst("img")?.let { avatarUrl(it) },
                url = link.absUrl("href")
            )
        }

    private fun parseFriendList(document: Document): List<SpaceListItem> =
        document.select("#friend_ul li, .buddy li, .xld dl, .imglist li").mapNotNull { item ->
            val link = item.select("a[href*='space-uid-'], a[href*='uid=']").lastOrNull()
                ?: return@mapNotNull null
            val uid = extractUid(link.attr("href")) ?: return@mapNotNull null
            SpaceListItem.Friend(
                uid = uid,
                name = link.text().trim(),
                avatarUrl = item.selectFirst("img")?.let { avatarUrl(it) },
                statusText = item.selectFirst(".xg1, .mtxt, dd")?.text()?.trim().orEmpty(),
                spaceUrl = link.absUrl("href"),
                pmUrl = item.selectFirst("a[href*='subop=view']")?.absUrl("href").orEmpty()
            )
        }

    private fun parseThreadList(document: Document): List<SpaceListItem> =
        document.select("#threadlisttableid tr, .tl tbody tr").mapNotNull { row ->
            val link = row.selectFirst("a[href*='tid='], a[href*='mod=viewthread']") ?: return@mapNotNull null
            val tid = Regex("[?&]tid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            SpaceListItem.UserThread(
                tid = tid,
                title = link.text().trim(),
                time = row.selectFirst(".by em, .xg1")?.text()?.trim().orEmpty(),
                forumName = row.selectFirst(".by a")?.text()?.trim().orEmpty(),
                viewCount = row.select(".num em").firstOrNull()?.text().orEmpty(),
                replyCount = row.select(".num em").getOrNull(1)?.text().orEmpty(),
                isClosed = row.selectFirst(".lock, img[src*='lock']") != null,
                url = link.absUrl("href")
            )
        }

    private fun pageUrl(document: Document, previous: Boolean): String? {
        val labels = if (previous) setOf("上一页", "上一頁", "prev") else setOf("下一页", "下一頁", "next")
        return document.select(".pg a[href], .pgs a[href]").firstOrNull { link ->
            labels.any { link.text().trim().contains(it, ignoreCase = true) } &&
                !link.attr("href").startsWith("javascript:", ignoreCase = true)
        }?.absUrl("href")?.takeIf { it.startsWith("http") }
    }

    private fun extractUid(href: String): String? =
        Regex("space-uid-(\\d+)").find(href)?.groupValues?.get(1)
            ?: Regex("[?&]uid=(\\d+)").find(href)?.groupValues?.get(1)

    private fun avatarUrl(image: Element): String? =
        image.absUrl("src").takeIf(String::isNotBlank)
            ?: image.absUrl("zsrc").takeIf(String::isNotBlank)

    fun parseBlogDetail(html: String, url: String): BlogDetail {
        val document = Jsoup.parse(html, ORIGIN)
        val article = document.selectFirst("#blog_article")
            ?: throw IllegalStateException("日志内容为空")
        val authorLink = document.selectFirst("#pcd .hm a[href*='space-uid-']")
            ?: document.selectFirst("#pcd .hm a[href*='mod=space'][href*='uid=']")
            ?: document.selectFirst("#pcd .hm a.xw1")
            ?: document.selectFirst(".vw .hm a[href*='space-uid-']")
            ?: document.selectFirst(".vw .hm a[href*='mod=space'][href*='uid=']")
            ?: document.selectFirst(".vw .hm a.xw1")
            ?: document.selectFirst("#pcd a[href*='space-uid-']")
            ?: document.selectFirst(".vw a[href*='space-uid-']")
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
        val authorName = authorLink?.text()?.trim().orEmpty().ifBlank {
            document.select("a[href*='space-uid-']")
                .map { it.text().trim() }
                .firstOrNull(String::isNotBlank)
                .orEmpty()
        }
        if (authorName.isBlank()) {
            val authorLinkCount = document.select("a[href*='uid='], a[href*='space-uid-']").size
            AppErrorLog.record(
                "桌面版日志作者名为空：作者链接数=$authorLinkCount，pcd作者区=${document.selectFirst("#pcd .hm") != null}，vw作者区=${document.selectFirst(".vw .hm") != null}"
            )
        }
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
            authorName = authorName,
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

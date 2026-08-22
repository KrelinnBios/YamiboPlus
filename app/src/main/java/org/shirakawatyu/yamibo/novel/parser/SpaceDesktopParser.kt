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
            val authorLinks = item.select(
                "dd a[href*='space-uid-'], " +
                    "dd a[href*='mod=space'][href*='uid=']:not([href*='do=blog'])"
            )
            // 电脑版好友日志的第一个空间链接位于 dd.m 头像内，没有文本；
            // 作者名要取后面正文 dd 中的非空链接，uid 则允许从头像链接兜底。
            val authorLink = authorLinks.firstOrNull { it.text().trim().isNotBlank() }
            val authorUid = extractUid(authorLink?.attr("href").orEmpty())
                ?: authorLinks.firstNotNullOfOrNull { extractUid(it.attr("href")) }
                ?: ""
            val metadataTexts = item.select("dd .xg1, dt .xg1")
                .map { it.text().trim() }
            SpaceListItem.Blog(
                blogId = id,
                title = link.text().trim(),
                category = item.selectFirst("a[href*='classid=']")?.text()?.trim().orEmpty(),
                time = metadataTexts.firstOrNull { datePattern.containsMatchIn(it) }.orEmpty(),
                summary = item.selectFirst("dd[id^=blog_article_]")?.text()?.trim().orEmpty(),
                authorName = authorLink?.text()?.trim().orEmpty(),
                url = link.absUrl("href"),
                authorUid = authorUid,
                authorAvatarUrl = item.selectFirst(
                    "a[href*='space-uid-'] img, " +
                        "a[href*='mod=space'][href*='uid=']:not([href*='do=blog']) img, " +
                        "img[src*='avatar'], img[zsrc*='avatar']"
                )?.let(::avatarUrl)
                    ?: authorUid.takeIf(String::isNotBlank)
                        ?.let { "$ORIGIN/uc_server/avatar.php?uid=$it&size=small" },
                visibilityText = extractBlogVisibilityText(metadataTexts)
            )
        }

    private fun parseDoingList(document: Document): List<SpaceListItem> =
        document.select(".xld.xlda > dl, .doing_list_box li.doing_list_li").mapNotNull { item ->
            // 电脑版第一条空间链接位于头像中，本身没有文本；作者名必须从正文 dd 中取。
            val contentCell = item.selectFirst("dd.ptm.xs2, .do_comment, .message")
                ?: return@mapNotNull null
            val user = contentCell.children().firstOrNull { child ->
                child.tagName() == "a" && extractUid(child.attr("href")) != null
            } ?: return@mapNotNull null
            val uid = extractUid(user.attr("href")) ?: return@mapNotNull null
            val content = contentCell.children().firstOrNull { it.tagName() == "span" }
                ?.let(::plainTextWithImages)
                .orEmpty()
                .ifBlank {
                    plainTextWithImages(contentCell.clone().apply {
                        children().firstOrNull { child ->
                            child.tagName() == "a" && extractUid(child.attr("href")) != null
                        }?.remove()
                    })
                        .removePrefix(":")
                        .removePrefix("：")
                        .trim()
                }
            val comments = item.select("dd.cmt > ul > li").mapNotNull(::parseDoingComment)
            val commentBox = item.selectFirst("dd.cmt[id]")
            val doId = commentBox?.id()?.substringAfterLast('_').orEmpty().ifBlank {
                Regex("dl(\\d+)$").find(item.id())?.groupValues?.getOrNull(1).orEmpty()
            }
            val replyLink = item.selectFirst("dd.ptn.xg1 a[onclick*='docomment_form']")
            SpaceListItem.Doing(
                doId = doId,
                uid = uid,
                name = user.text().trim(),
                avatarUrl = item.selectFirst("dd.m.avt img, .avatar img, .mimg img")
                    ?.let(::avatarUrl),
                // 评论也使用 .xg1；只取条目底部的发布时间，避免误取第一条评论时间。
                time = item.selectFirst("dd.ptn.xg1 > span.y, .mtime > span, .mtime")
                    ?.text()
                    ?.trim()
                    .orEmpty(),
                content = content,
                comments = comments,
                spaceUrl = user.absUrl("href"),
                replyUrl = doingReplyUrl(replyLink),
                deleteUrl = item.selectFirst(
                    "dd.ptn.xg1 a[href*='ac=doing'][href*='op=delete']"
                )?.absUrl("href").orEmpty()
            )
        }

    private fun parseDoingComment(item: Element): DoingComment? {
        val author = item.selectFirst(
            "a.lit[href*='space-uid-'], a.lit[href*='uid='], " +
                "a[href*='space-uid-'], a[href*='mod=space'][href*='uid=']"
        ) ?: return null
        val content = plainTextWithImages(item.clone().apply {
            select("a.lit, .xg1, a[onclick*='docomment_form'], " +
                "a[href*='ac=doing'][href*='op=delete'], div[id*='_form_']")
                .remove()
        }).removePrefix(":").removePrefix("：").trim()
        val replyLink = item.selectFirst("a[onclick*='docomment_form']")
        return DoingComment(
            id = doingReplyArguments(replyLink)?.second.orEmpty(),
            authorName = author.text().trim(),
            time = item.selectFirst(".xg1")?.text()?.trim()?.removeSurrounding("(", ")").orEmpty(),
            content = content,
            replyUrl = doingReplyUrl(replyLink),
            deleteUrl = item.selectFirst(
                "a[href*='ac=doing'][href*='op=delete']"
            )?.absUrl("href").orEmpty()
        )
    }

    private fun doingReplyUrl(link: Element?): String {
        val (doId, commentId, key) = doingReplyArguments(link) ?: return ""
        return "$ORIGIN/home.php?mod=spacecp&ac=doing&op=docomment" +
            "&handlekey=msg_$commentId&doid=$doId&id=$commentId&key=$key&mobile=no"
    }

    private fun doingReplyArguments(link: Element?): Triple<String, String, String>? {
        val match = Regex(
            "docomment_form\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*['\"]([^'\"]+)['\"]"
        ).find(link?.attr("onclick").orEmpty()) ?: return null
        return Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }

    /** 论坛表情是无 alt 的图片；原生纯文本列表至少保留一个可见占位，避免语义被静默吞掉。 */
    private fun plainTextWithImages(element: Element): String {
        val copy = element.clone()
        copy.select("img").forEach { image ->
            val label = image.attr("alt").trim()
                .ifBlank { image.attr("title").trim() }
                .ifBlank { "[表情]" }
            image.after(" $label ")
            image.remove()
        }
        return copy.text().trim()
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

    private fun parseThreadList(document: Document): List<SpaceListItem> {
        val entryType = when {
            document.selectFirst("a.a[href*='type=postcomment']") != null -> "点评"
            document.selectFirst("a.a[href*='type=reply']") != null -> "回复"
            else -> ""
        }
        return document.select("#threadlisttableid tr, .tl tbody tr").mapNotNull { row ->
            val link = row.selectFirst("th > a[href]") ?: return@mapNotNull null
            val tid = extractUserThreadId(link.attr("href")) ?: return@mapNotNull null
            val detailCell = row.nextElementSibling()?.selectFirst("td[colspan].xg1")
            val detailLink = detailCell
                ?.selectFirst("a[href*='goto=findpost'][href*='pid=']")
            val destinationUrl = detailLink?.absUrl("href")?.takeIf(String::isNotBlank)
                ?: link.absUrl("href")
            SpaceListItem.UserThread(
                tid = tid,
                title = link.text().trim(),
                time = row.selectFirst("td.by em")?.text()?.trim().orEmpty(),
                forumName = row.selectFirst("th + td a")?.text()?.trim().orEmpty(),
                viewCount = row.selectFirst("td.num em")?.text()?.trim().orEmpty(),
                replyCount = row.selectFirst("td.num a")?.text()?.trim().orEmpty(),
                isClosed = row.selectFirst(".fico-lock, .lock, img[src*='lock']") != null ||
                    row.selectFirst("th .xg1")?.text()?.contains("已关闭") == true,
                isPoll = row.selectFirst(
                    ".fico-vote, [alt='投票'], [title='投票'], img[src*='poll']"
                ) != null,
                url = destinationUrl,
                replyExcerpt = detailLink?.text()?.trim()
                    ?: detailCell?.text()?.trim().orEmpty(),
                entryType = entryType,
                postId = detailLink?.attr("href")
                    ?.let { Regex("[?&]pid=(\\d+)").find(it)?.groupValues?.getOrNull(1) }
                    .orEmpty()
            )
        }
    }

    private fun extractUserThreadId(url: String): String? =
        Regex("[?&](?:tid|ptid)=(\\d+)").find(url)?.groupValues?.getOrNull(1)
            ?: Regex("(?:^|/)thread-(\\d+)").find(url)?.groupValues?.getOrNull(1)

    private fun pageUrl(document: Document, previous: Boolean): String? {
        val labels = if (previous) setOf("上一页", "上一頁", "prev") else setOf("下一页", "下一頁", "next")
        val pageLinks = document.select(
            ".pg a[href], .pgs a[href], a.prev[href], a.nxt[href], " +
                ".prev a[href], .nxt a[href], a[rel=prev][href], a[rel=next][href]"
        )
        val direct = pageLinks.firstOrNull { link ->
            val classes = link.classNames().map(String::lowercase)
            val parentClasses = link.parent()?.classNames()?.map(String::lowercase).orEmpty()
            val classMatches = if (previous) {
                "prev" in classes || "prev" in parentClasses
            } else {
                "nxt" in classes || "next" in classes ||
                    "nxt" in parentClasses || "next" in parentClasses
            }
            val semanticText = listOf(
                link.text(),
                link.attr("title"),
                link.attr("aria-label"),
                link.attr("rel")
            ).joinToString(" ")
            val textMatches = labels.any { semanticText.trim().contains(it, ignoreCase = true) }
            (classMatches || textMatches) &&
                !link.attr("href").startsWith("javascript:", ignoreCase = true)
        }?.absUrl("href")?.takeIf { it.startsWith("http") }
        if (direct != null) return direct

        return numericPageUrl(pageLinks, previous)
    }

    private fun numericPageUrl(pageLinks: List<Element>, previous: Boolean): String? {
        val current = pageLinks
            .flatMap { it.parent()?.select("strong, .a")?.map { node -> node.text().trim() }.orEmpty() }
            .firstNotNullOfOrNull { it.toIntOrNull() }
            ?: return null
        val candidates = pageLinks.mapNotNull { link ->
            val page = Regex("[?&]page=(\\d+)").find(link.attr("href"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: link.text().trim().toIntOrNull()
                ?: return@mapNotNull null
            page to link
        }
        val target = if (previous) {
            candidates.filter { it.first < current }.maxByOrNull { it.first }
        } else {
            candidates.filter { it.first > current }.minByOrNull { it.first }
        }
        return target?.second?.absUrl("href")?.takeIf { it.startsWith("http") }
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
        val category = document.selectFirst(".vw .h p.xg2 a[href*='classid=']")
            ?.text()
            ?.trim()
            .orEmpty()
        val tags = document.select(".vw .h p.xg2 .ptg a")
            .map { it.text().trim() }
            .filter(String::isNotBlank)
        val metaText = document.selectFirst(".vw .h p.xg2")?.text().orEmpty()
        val visibilityText = extractBlogVisibilityText(
            document.select(".vw .h p.xg2 .y, .vw .h p.xg2 .xg1")
                .map { it.text().trim() }
        )
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
        val managementLinks = document.select(".vw a")
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
            stickUrl = managementLinks.firstOrNull {
                it.text().trim() == "置顶" || it.attr("href").contains("op=stick")
            }?.absUrl("href").orEmpty(),
            editUrl = managementLinks.firstOrNull {
                it.text().trim() == "编辑" || it.attr("href").contains("op=edit")
            }?.absUrl("href").orEmpty(),
            deleteUrl = managementLinks.firstOrNull {
                it.id().contains("delete") || it.attr("href").contains("op=delete")
            }?.absUrl("href").orEmpty(),
            reportUrl = actionLinks.firstOrNull { it.text().trim() == "举报" }
                ?.let { actionUrl(it) }
                .orEmpty(),
            tags = tags,
            visibilityText = visibilityText,
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
            val quoteElement = contentElement?.selectFirst(".quote blockquote")
            val quotedAuthor = quoteElement?.selectFirst("b")?.text()?.trim().orEmpty()
            val quotedContent = quoteElement
                ?.clone()
                ?.apply { select("b").remove() }
                ?.let(::formattedTextWithImages)
                ?.removePrefix(":")
                ?.removePrefix("：")
                ?.trim()
                .orEmpty()
            val content = contentElement
                ?.clone()
                ?.apply { select(".quote").remove() }
                ?.let(::formattedTextWithImages)
                .orEmpty()
            val actions = item.select("dt span.y a")
            BlogComment(
                id = id,
                authorName = authorLink?.text()?.trim().orEmpty(),
                authorUid = authorUid,
                avatarUrl = item.selectFirst("dd.avt img")?.let { avatarUrl(it) },
                time = item.selectFirst("dt span.xg1")?.text()?.trim().orEmpty(),
                content = content,
                quotedAuthor = quotedAuthor,
                quotedContent = quotedContent,
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

    /** 保留评论中的 br/段落边界，同时给无 alt 的论坛表情留下可见占位。 */
    private fun formattedTextWithImages(element: Element): String {
        val copy = element.clone()
        copy.select("img").forEach { image ->
            val label = image.attr("alt").trim()
                .ifBlank { image.attr("title").trim() }
                .ifBlank { "[表情]" }
            image.after(" $label ")
            image.remove()
        }
        copy.select("br").forEach { lineBreak ->
            lineBreak.after("\n")
            lineBreak.remove()
        }
        copy.select("p, li").forEach { block ->
            block.before("\n")
            block.after("\n")
        }
        return copy.wholeText()
            .replace("\r\n", "\n")
            .replace(Regex("[\\t\\u000B\\f ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
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

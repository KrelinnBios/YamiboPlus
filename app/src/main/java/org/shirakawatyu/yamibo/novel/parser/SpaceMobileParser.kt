package org.shirakawatyu.yamibo.novel.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.shirakawatyu.yamibo.novel.bean.space.DoingComment
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListPage
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.SpaceCategory
import org.shirakawatyu.yamibo.novel.bean.space.SpaceFriendFilter
import org.shirakawatyu.yamibo.novel.bean.space.BlogComment
import org.shirakawatyu.yamibo.novel.bean.space.BlogContentBlock
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageBubble
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageConversation
import org.shirakawatyu.yamibo.novel.util.AppErrorLog

/**
 * 手机版空间页 HTML 解析器。
 * 覆盖：私信列表(do=pm)、提醒(do=notice)、好友(do=friend)、记录(do=doing)、
 * 日志(do=blog)、我的主题/回复(do=thread)。
 */
object SpaceMobileParser {
    private const val ORIGIN = "https://bbs.yamibo.com"

    fun parse(kind: SpacePageKind, html: String): List<SpaceListItem> {
        return parsePage(kind, html).items
    }

    fun parsePage(kind: SpacePageKind, html: String): SpaceListPage {
        val document = Jsoup.parse(html, ORIGIN)
        val items = when (kind) {
            SpacePageKind.PRIVATE_MESSAGE -> parsePmList(document)
            SpacePageKind.NOTICE -> parseNoticeList(document)
            SpacePageKind.FRIEND -> parseFriendList(document)
            SpacePageKind.DOING -> parseDoingList(document)
            SpacePageKind.BLOG -> parseBlogList(document)
            SpacePageKind.USER_THREAD -> parseUserThreadList(document)
        }
        return SpaceListPage(
            items = items,
            previousUrl = pageUrl(document, previous = true),
            nextUrl = pageUrl(document, previous = false),
            categories = if (kind == SpacePageKind.BLOG) parseCategories(document) else emptyList(),
            friendFilters = if (kind == SpacePageKind.BLOG) parseFriendFilters(document) else emptyList()
        )
    }

    fun isLoginRequired(html: String): Boolean =
        html.contains("mod=logging") && html.contains("formhash")

    /**
     * 解析私信对话页（do=pm&subop=view）：
     * 对方消息 .friend_msg，自己消息 .self_msg，表单提供 pmid/formhash/touid。
     */
    fun parsePrivateMessageConversation(html: String, url: String): PrivateMessageConversation {
        val document = Jsoup.parse(html, ORIGIN)
        val messages = mutableListOf<PrivateMessageBubble>()
        document.select(".msgbox .friend_msg, .msgbox .self_msg").forEach { item ->
            val isSelf = item.hasClass("self_msg")
            val content = item.selectFirst(".dialog_c")?.text()?.trim().orEmpty()
            val time = item.selectFirst(".date")?.text()?.trim().orEmpty()
            val avatar = item.selectFirst(".avat img")?.let { avatarUrl(it, null) }
            messages += PrivateMessageBubble(
                isSelf = isSelf,
                authorName = "",
                avatarUrl = avatar,
                content = content,
                time = time
            )
        }
        val form = document.selectFirst("#pmform")
        val title = document.selectFirst(".header h2")?.text()?.trim().orEmpty()
        return PrivateMessageConversation(
            touid = form?.selectFirst("input[name=touid]")?.attr("value").orEmpty(),
            title = title,
            pmid = Regex("[?&]pmid=(\\d+)")
                .find(form?.attr("action").orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty(),
            formHash = form?.selectFirst("input[name=formhash]")?.attr("value").orEmpty(),
            messages = messages,
            previousUrl = pageUrl(document, previous = true),
            nextUrl = pageUrl(document, previous = false)
        )
    }

    fun parseBlogDetail(html: String, url: String): BlogDetail {
        val document = Jsoup.parse(html, ORIGIN)
        val titleElement = document.selectFirst(".viewthread .view_tit")
            ?: throw IllegalStateException("日志内容为空")
        val title = titleElement.ownText().trim()
        val category = titleElement.selectFirst("em")?.text()
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            .orEmpty()
        val authorLink = document.selectFirst(".viewthread .authi a[href*='uid=']")
            ?: document.selectFirst(".viewthread .authi a.xw1")
            ?: document.selectFirst(".viewthread .authi a[href*='mod=space']")
        val ownerUid = Regex("[?&]uid=(\\d+)")
            .find(authorLink?.attr("href").orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("[?&]uid=(\\d+)").find(url)?.groupValues?.getOrNull(1).orEmpty()
        val blogId = Regex("[?&]id=(\\d+)").find(url)?.groupValues?.getOrNull(1).orEmpty()
        val time = document.selectFirst(".viewthread .authi .mtime")?.ownText()?.trim().orEmpty()
        val statText = document.selectFirst(".viewthread .authi .mtime .y")?.text().orEmpty()
        val visibilityText = extractBlogVisibilityText(
            document.select(".viewthread .authi .y, .viewthread .authi .xg1")
                .map { it.text().trim() }
        )
        val stats = Regex("(\\d+)").findAll(statText).map { it.value }.toList()
        val authorName = authorLink?.text()?.trim().orEmpty().ifBlank {
            document.select("a[href*='uid=']")
                .map { it.text().trim() }
                .firstOrNull(String::isNotBlank)
                .orEmpty()
        }
        val message = document.selectFirst(".viewthread .message")
            ?: throw IllegalStateException("日志正文为空")
        val blocks = parseBlogBlocks(message)
        val comments = document.select(".doing_list_box li.doing_list_li").mapNotNull { li ->
            val id = Regex("comment_(\\d+)").find(li.id())?.groupValues?.getOrNull(1)
                ?: return@mapNotNull null
            val commentAuthor = li.selectFirst(".muser .mmc")
            val commentUid = Regex("[?&]uid=(\\d+)")
                .find(commentAuthor?.attr("href").orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            val content = li.selectFirst(".do_comment")?.clone()?.apply {
                select(".quote").remove()
            }?.text()?.trim().orEmpty()
            BlogComment(
                id = id,
                authorName = li.selectFirst(".mmc")?.text()?.trim().orEmpty(),
                authorUid = commentUid,
                avatarUrl = avatarUrl(li.selectFirst(".avatar img, .mimg img"), commentUid),
                time = li.selectFirst(".mtime > span")?.text()?.trim().orEmpty(),
                content = content,
                replyUrl = li.selectFirst("a[href*='op=reply']")?.attr("abs:href").orEmpty(),
                editUrl = li.selectFirst("a[href*='op=edit']")?.attr("abs:href").orEmpty(),
                deleteUrl = li.selectFirst("a[href*='op=delete']")?.attr("abs:href").orEmpty()
            )
        }
        val actionLinks = document.select(".viewthread .threadlist_foot a")
        val managementLinks = document.select(".viewthread a")
        return BlogDetail(
            blogId = blogId,
            ownerUid = ownerUid,
            category = category,
            title = title,
            authorName = authorName,
            authorAvatarUrl = avatarUrl(
                document.selectFirst(".viewthread .avatar img"),
                ownerUid
            ),
            time = time,
            viewCount = stats.getOrNull(0).orEmpty(),
            commentCount = stats.getOrNull(1).orEmpty(),
            blocks = blocks,
            comments = comments,
            favoriteUrl = actionLinks.firstOrNull { it.id() == "a_favorite" }?.attr("abs:href").orEmpty(),
            shareUrl = actionLinks.firstOrNull { it.id() == "a_share" }?.attr("abs:href").orEmpty(),
            inviteUrl = actionLinks.firstOrNull { it.id() == "a_invite" }?.attr("abs:href").orEmpty(),
            stickUrl = managementLinks.firstOrNull {
                it.text().trim() == "置顶" || it.attr("href").contains("op=stick")
            }?.attr("abs:href").orEmpty(),
            editUrl = managementLinks.firstOrNull {
                it.text().trim() == "编辑" || it.attr("href").contains("op=edit")
            }?.attr("abs:href").orEmpty(),
            deleteUrl = managementLinks.firstOrNull {
                it.id().contains("delete") || it.attr("href").contains("op=delete")
            }?.attr("abs:href").orEmpty(),
            visibilityText = visibilityText
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
        if (blocks.isEmpty()) {
            message.text().trim().takeIf(String::isNotBlank)?.let {
                blocks += BlogContentBlock.Text(it)
            }
        }
        return blocks
    }

    private fun parsePmList(doc: Document): List<SpaceListItem> =
        doc.select("#pmlist ul li, .imglist ul li").mapNotNull { li ->
            val link = li.selectFirst("a[href*='subop=view']") ?: li.selectFirst("a[href]")
            val touid = link?.attr("href")
                ?.let { Regex("[?&]touid=(\\d+)").find(it)?.groupValues?.get(1) }
                ?: return@mapNotNull null
            val mtit = li.selectFirst(".mtit")
            val time = mtit?.selectFirst(".mtime")?.text().orEmpty().trim()
            val title = mtit?.ownText().orEmpty().trim()
            val summary = li.selectFirst(".mtxt")?.text().orEmpty().trim()
            SpaceListItem.PrivateMessage(
                touid = touid,
                name = title.substringAfter("对 ").substringBefore(" 说")
                    .ifBlank { title },
                time = time,
                summary = summary,
                avatarUrl = avatarUrl(li.selectFirst("img"), touid),
                url = link.attr("abs:href")
            )
        }

    private fun parseNoticeList(doc: Document): List<SpaceListItem> {
        // 手机版提醒列表与私信列表同为 .imglist 结构；桌面版为 .nts dl。
        val mobileItems = doc.select(".imglist ul li").mapNotNull { li ->
            val link = li.selectFirst("a[href]") ?: return@mapNotNull null
            val title = li.selectFirst(".mtit")?.text().orEmpty().trim()
            val summary = li.selectFirst(".mtxt")?.text().orEmpty().trim()
            val time = li.selectFirst(".mtime")?.text().orEmpty().trim()
            SpaceListItem.Notice(
                title = title.ifBlank { summary.take(24) },
                time = time,
                summary = summary,
                avatarUrl = avatarUrl(li.selectFirst("img"), null),
                url = link.attr("abs:href")
            )
        }
        if (mobileItems.isNotEmpty()) return mobileItems
        return doc.select(".nts dl, #ct .nts dl").mapNotNull { dl ->
            val link = dl.selectFirst("a[href]") ?: return@mapNotNull null
            val title = dl.selectFirst(".n_from, dd:first-child")?.text().orEmpty().trim()
            val body = dl.selectFirst(".ntc_body")?.text().orEmpty().trim()
            val time = dl.selectFirst(".n_from .xg1, .ntc_body .xg1, em.xg1")?.text().orEmpty().trim()
            SpaceListItem.Notice(
                title = title.ifBlank { body.take(24) },
                time = time,
                summary = body,
                avatarUrl = null,
                url = link.attr("abs:href")
            )
        }
    }

    private fun parseFriendList(doc: Document): List<SpaceListItem> =
        doc.select("#friend_ul ul li, .imglist ul li").mapNotNull { li ->
            val userLink = li.select("a[href*='mod=space']").lastOrNull()
                ?: li.select("a[href*='uid=']").lastOrNull()
                ?: return@mapNotNull null
            val uid = Regex("[?&]uid=(\\d+)").find(userLink.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val name = userLink.selectFirst("span")?.text().orEmpty().trim()
                .ifBlank { userLink.text().trim() }
            val status = li.selectFirst(".mtxt")?.text().orEmpty().trim()
            val pmLink = li.select("a[href*='subop=view']").firstOrNull()
            SpaceListItem.Friend(
                uid = uid,
                name = name,
                avatarUrl = avatarUrl(li.selectFirst(".mimg img, img"), uid),
                statusText = status,
                spaceUrl = userLink.attr("abs:href"),
                pmUrl = pmLink?.attr("abs:href").orEmpty()
            )
        }

    private fun parseDoingList(doc: Document): List<SpaceListItem> =
        doc.select(".doing_list_box ul > li.doing_list_li, .doing_list ul > li").mapNotNull { li ->
            val userLink = li.selectFirst("a[href*='mod=space'], a[href*='uid=']") ?: return@mapNotNull null
            val uid = Regex("[?&]uid=(\\d+)").find(userLink.attr("href"))?.groupValues?.get(1).orEmpty()
            val name = li.selectFirst(".mmc")?.text().orEmpty().trim()
            val time = li.selectFirst(".mtime > span, .mtime")?.ownText().orEmpty().trim()
                .ifBlank { li.selectFirst(".mtime span")?.text().orEmpty().trim() }
            val content = li.selectFirst(".do_comment")?.ownText().orEmpty().trim()
            val comments = li.select(".do_comment .quote ul > li").mapNotNull { commentLi ->
                val author = commentLi.selectFirst("a.lit, a[href*='uid=']")?.text().orEmpty().trim()
                val commentTime = commentLi.selectFirst(".xg1")?.text().orEmpty().trim()
                val commentText = commentLi.ownText().trim()
                DoingComment(
                    authorName = author,
                    time = commentTime,
                    content = commentText
                )
            }
            SpaceListItem.Doing(
                uid = uid,
                name = name,
                avatarUrl = avatarUrl(li.selectFirst(".avatar img, .mimg img, img"), uid),
                time = time,
                content = content,
                comments = comments,
                spaceUrl = userLink.attr("abs:href")
            )
        }

    private fun parseBlogList(doc: Document): List<SpaceListItem> {
        val mobileItems = doc.select(".threadlist_box .threadlist ul > li.list, .threadlist ul > li")
            .mapNotNull { li ->
            val link = li.selectFirst("a[href*='do=blog']") ?: return@mapNotNull null
            val blogId = Regex("[?&]id=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val category = li.selectFirst(".threadlist_tit span")?.text().orEmpty()
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
            val title = li.selectFirst(".threadlist_tit")?.ownText().orEmpty().trim()
            val time = li.selectFirst(".mtime span")?.text().orEmpty().trim()
            val summary = li.selectFirst(".threadlist_mes")?.text().orEmpty().trim()
            val author = li.selectFirst(".mmc")?.text().orEmpty().trim()
            val authorLink = li.selectFirst(
                "a.mmc[href], .muser a[href*='uid='], " +
                    "a[href*='mod=space'][href*='uid=']:not([href*='do=blog']), " +
                    "a[href*='space-uid-']"
            )
            val authorUid = extractUid(authorLink?.attr("href").orEmpty()).orEmpty()
            val metadataTexts = li.select(".xg1, .mtime")
                .map { it.text().trim() }
            val tags = li.select("a[href*='mod=tag']")
                .map { it.text().trim() }
                .filter(String::isNotBlank)
                .distinct()
            SpaceListItem.Blog(
                blogId = blogId,
                category = category,
                title = title.ifBlank { category },
                time = time,
                summary = summary,
                authorName = author,
                url = link.attr("abs:href"),
                authorUid = authorUid,
                authorAvatarUrl = avatarUrl(
                    li.selectFirst(".mimg img, .avatar img, img[src*='avatar']"),
                    authorUid
                ),
                visibilityText = extractBlogVisibilityText(metadataTexts),
                editUrl = li.selectFirst("a[href*='op=edit']")?.attr("abs:href").orEmpty(),
                deleteUrl = li.selectFirst("a[href*='op=delete']")?.attr("abs:href").orEmpty(),
                stickUrl = li.selectFirst("a[href*='op=stick']")?.attr("abs:href").orEmpty(),
                tags = tags
            )
        }
        if (mobileItems.isNotEmpty()) return mobileItems

        return doc.select(".xld > dl.bbda").mapNotNull { item ->
            val link = item.selectFirst("dt a[href*='blog-']") ?: return@mapNotNull null
            val blogId = Regex("blog-\\d+-(\\d+)").find(link.attr("href"))
                ?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val authorLink = item.selectFirst(
                "dd a[href*='space-uid-'], " +
                    "dd a[href*='mod=space'][href*='uid=']:not([href*='do=blog'])"
            )
            val authorUid = extractUid(authorLink?.attr("href").orEmpty()).orEmpty()
            val metadataTexts = item.select("dd .xg1, dt .xg1")
                .map { it.text().trim() }
            SpaceListItem.Blog(
                blogId = blogId,
                category = item.selectFirst("a[href*='classid=']")?.text()?.trim().orEmpty(),
                title = link.text().trim(),
                time = metadataTexts.firstOrNull {
                    it.length >= 8 && it.any(Char::isDigit) && (it.contains("-") || it.contains("/"))
                }.orEmpty(),
                summary = item.selectFirst("dd[id^=blog_article_]")?.text()?.trim().orEmpty(),
                authorName = authorLink?.text()?.trim().orEmpty(),
                url = link.absUrl("href"),
                authorUid = authorUid,
                authorAvatarUrl = avatarUrl(
                    item.selectFirst(
                        "a[href*='space-uid-'] img, " +
                            "a[href*='mod=space'][href*='uid=']:not([href*='do=blog']) img, " +
                            "img[src*='avatar'], img[zsrc*='avatar']"
                    ),
                    authorUid
                ),
                visibilityText = extractBlogVisibilityText(metadataTexts)
            )
        }
    }

    private fun parseUserThreadList(doc: Document): List<SpaceListItem> {
        val entryType = when {
            doc.selectFirst("a.a[href*='type=postcomment']") != null -> "点评"
            doc.selectFirst("a.a[href*='type=reply']") != null -> "回复"
            else -> ""
        }
        return doc.select(".threadlist ul > li").mapNotNull { li ->
            val link = li.selectFirst("a[href*='mod=viewthread']") ?: return@mapNotNull null
            val tid = Regex("[?&]tid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            // 取标题前先剔掉 .micon（如「已关闭」图标），避免站点模板里
            // !closed_thread!: 这类未替换的 lang 文本混进标题。
            val titleElement = li.selectFirst(".threadlist_tit")
            val title = titleElement?.clone()?.apply { select(".micon").remove() }
                ?.text().orEmpty().trim()
            val time = li.selectFirst(".mtime")?.text().orEmpty().trim()
            val summary = li.selectFirst(".threadlist_mes")?.text().orEmpty().trim()
            val forumName = li.selectFirst(".threadlist_foot li.mr a")?.text().orEmpty().trim()
                .removePrefix("#")
            val viewCount = li.select(".threadlist_foot li")
                .firstOrNull { it.selectFirst(".dm-eye-fill, i[class*='eye']") != null }
                ?.text().orEmpty().trim()
            val replyCount = li.select(".threadlist_foot li")
                .firstOrNull { it.selectFirst(".dm-chat-s-fill, i[class*='chat']") != null }
                ?.text().orEmpty().trim()
            val isClosed = li.selectFirst(".micon.lock") != null
            SpaceListItem.UserThread(
                tid = tid,
                title = title.ifBlank { summary.take(24) },
                time = time,
                forumName = forumName,
                viewCount = viewCount,
                replyCount = replyCount,
                isClosed = isClosed,
                url = link.attr("abs:href"),
                replyExcerpt = summary,
                entryType = entryType
            )
        }
    }

    private fun avatarUrl(img: Element?, uid: String?): String? {
        val src = img?.let { element ->
            element.attr("zsrc").takeIf(String::isNotBlank)
                ?: element.attr("src").takeIf(String::isNotBlank)
        } ?: return uid?.let {
            "$ORIGIN/uc_server/avatar.php?uid=$it&size=small"
        }
        return if (src.startsWith("http")) src else "$ORIGIN$src"
    }

    private fun pageUrl(doc: Document, previous: Boolean): String? {
        val labels = if (previous) {
            setOf("上一页", "上一頁", "prev")
        } else {
            setOf("下一页", "下一頁", "next")
        }
        val pageLinks = doc.select(
            ".page a[href], .pgs .page a[href], .pgs .pg a[href], .pg a[href], " +
                "a.prev[href], a.nxt[href], .prev a[href], .nxt a[href], " +
                "a[rel=prev][href], a[rel=next][href]"
        )
        val direct = pageLinks
            .firstOrNull { link ->
                val text = link.text().trim().lowercase()
                val classes = link.classNames().map(String::lowercase)
                val parentClasses = link.parent()?.classNames()?.map(String::lowercase).orEmpty()
                val classMatches = if (previous) {
                    "prev" in classes || "prev" in parentClasses
                } else {
                    "nxt" in classes || "next" in classes ||
                        "nxt" in parentClasses || "next" in parentClasses
                }
                val semanticText = listOf(
                    text,
                    link.attr("title"),
                    link.attr("aria-label"),
                    link.attr("rel")
                ).joinToString(" ").lowercase()
                (classMatches || labels.any { semanticText.contains(it.lowercase()) }) &&
                    !link.attr("href").trim().startsWith("javascript:", ignoreCase = true)
            }
            ?.attr("abs:href")
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (direct != null) return direct

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
        return target?.second?.attr("abs:href")?.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    private fun extractUid(href: String): String? =
        Regex("space-uid-(\\d+)").find(href)?.groupValues?.getOrNull(1)
            ?: Regex("[?&]uid=(\\d+)").find(href)?.groupValues?.getOrNull(1)

    private fun parseCategories(doc: Document): List<SpaceCategory> =
        doc.select("#dhnavs_li a[href*='classid=']")
            .mapNotNull { link ->
                val id = Regex("[?&]classid=(\\d+)")
                    .find(link.attr("href"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@mapNotNull null
                SpaceCategory(id = id, name = link.text().trim())
            }
            .distinctBy { it.id }

    private fun parseFriendFilters(doc: Document): List<SpaceFriendFilter> =
        doc.select("select[name=fuidsel] option[value]")
            .mapNotNull { option ->
                val uid = option.attr("value").trim()
                val name = option.text().trim()
                if (uid.matches(Regex("[1-9]\\d*")) && name.isNotBlank()) {
                    SpaceFriendFilter(uid, name)
                } else null
            }
            .distinctBy { it.uid }
}

internal fun extractBlogVisibilityText(texts: Iterable<String>): String {
    val markers = listOf(
        "仅好友可见",
        "僅好友可見",
        "好友可见",
        "好友可見",
        "仅自己可见",
        "僅自己可見",
        "自己可见",
        "自己可見"
    )
    return texts.asSequence()
        .map(String::trim)
        .firstOrNull { text -> markers.any(text::contains) }
        .orEmpty()
}

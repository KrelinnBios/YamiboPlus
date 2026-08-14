package org.shirakawatyu.yamibo.novel.parser

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAuthor
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostTextPart
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import kotlin.math.ceil

object ForumApiParser {
    private const val FORUM_ORIGIN = "https://bbs.yamibo.com"
    private val encodedHtmlTag = Regex(
        """</?([a-z][a-z0-9]*)(?:\s+[^<>]*?)?\s*/?>""",
        RegexOption.IGNORE_CASE
    )
    private val inlineFormattingTags = setOf(
        "a", "b", "big", "del", "em", "font", "i", "ins", "mark", "s", "small",
        "span", "strike", "strong", "sub", "sup", "u"
    )
    private val blockFormattingTags = setOf("br", "div", "p")

    fun parseForumIndex(rawJson: String): ForumIndex {
        val variables = variables(rawJson)
        val forumArray = variables.getJSONArray("forumlist")
            ?: throw IllegalStateException("论坛未返回板块列表")
        val categoryArray = variables.getJSONArray("catlist")
            ?: throw IllegalStateException("论坛未返回板块分类")

        val forums = forumArray.objects().map(::parseBoard)
        val forumsById = forums.associateBy(ForumBoard::id)
        val categories = categoryArray.objects().mapNotNull { category ->
            val id = category.stringValue("fid") ?: return@mapNotNull null
            val forumIds = category.getJSONArray("forums")
                ?.mapNotNull { value -> value?.toString()?.takeIf(String::isNotBlank) }
                .orEmpty()
            ForumCategory(
                id = id,
                name = cleanText(category.getString("name")),
                forums = forumIds.mapNotNull(forumsById::get)
            )
        }.filter { it.forums.isNotEmpty() }

        return ForumIndex(categories = categories)
    }

    fun parseForumBanners(rawHtml: String): List<ForumBanner> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        // 论坛首页轮播在不同模板/UA 下可能有多种容器类名
        val slideSelectors = listOf(
            "#forum .index-top-wrapper .yami-swiper .swiper-slide",
            ".index-top-wrapper .yami-swiper .swiper-slide",
            ".slidebox .swiper-slide",
            "#slide .swiper-slide",
            ".img_slide .swiper-slide",
            ".scrool_img .swiper-slide",
            ".scroll_img .swiper-slide",
            ".slide .swiper-slide",
            ".yami-swiper .swiper-slide",
            ".swiper-wrapper .swiper-slide",
            ".swiper-slide"
        )
        val slides = slideSelectors.asSequence()
            .map { document.select(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: return emptyList()
        return slides.mapNotNull { slide ->
            val image = slide.selectFirst("img[src]") ?: return@mapNotNull null
            val imageUrl = image.absUrl("src").ifBlank {
                absoluteUrl(image.attr("src")).orEmpty()
            }
            if (imageUrl.isBlank()) return@mapNotNull null
            val link = slide.selectFirst("a[href]")?.attr("href").orEmpty()
            ForumBanner(
                imageUrl = imageUrl,
                threadId = extractThreadId(link)
            )
        }.distinctBy(ForumBanner::imageUrl)
    }

    fun parseForumHeadImage(rawHtml: String): String? {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val image = document.selectFirst(
            "#forum > div.forum-headimg img[src], .forum-headimg img[src]"
        ) ?: return null
        return image.absUrl("src").ifBlank {
            absoluteUrl(image.attr("src")).orEmpty()
        }.takeIf(String::isNotBlank)
    }

    fun parseThreadPage(rawJson: String): ForumThreadPage {
        val variables = variables(rawJson)
        val forumObject = variables.getJSONObject("forum")
            ?: throw IllegalStateException("论坛未返回板块信息")
        val threadArray = variables.getJSONArray("forum_threadlist")
            ?: throw IllegalStateException("论坛未返回主题列表")
        val typeNames = variables.getJSONObject("threadtypes")
            ?.getJSONObject("types")
            ?.entries
            ?.associate { (id, name) -> id to cleanText(name?.toString()) }
            .orEmpty()

        val page = variables.intValue("page", 1).coerceAtLeast(1)
        val pageSize = variables.intValue("tpp", 20).coerceAtLeast(1)
        val forum = parseBoard(forumObject)
        val threads = threadArray.objects().mapNotNull { value ->
            val id = value.stringValue("tid") ?: return@mapNotNull null
            val typeId = value.stringValue("typeid")?.takeUnless { it == "0" }
            ForumThread(
                id = id,
                subject = cleanText(value.getString("subject")),
                authorId = value.stringValue("authorid")?.takeUnless { it == "0" },
                authorName = cleanText(value.getString("author")),
                createdAt = cleanText(value.getString("dateline")),
                lastPostAt = cleanText(value.getString("lastpost")),
                lastPoster = cleanText(value.getString("lastposter")),
                replyCount = value.intValue("replies"),
                viewCount = value.intValue("views"),
                displayOrder = value.intValue("displayorder"),
                typeId = typeId,
                typeName = typeId?.let(typeNames::get)?.takeIf(String::isNotBlank)
            )
        }
        val totalThreads = forumObject.intValue("threadcount")
        val hasMore = if (totalThreads > 0) {
            page * pageSize < totalThreads
        } else {
            threads.count { !it.isSticky } >= pageSize
        }
        val totalPages = if (totalThreads > 0) {
            ceil(totalThreads.toDouble() / pageSize).toInt().coerceAtLeast(1)
        } else if (hasMore) {
            page + 1
        } else {
            page
        }



        return ForumThreadPage(
            forum = forum,
            threads = threads,
            page = page,
            totalPages = totalPages,
            hasMore = hasMore,
            availableTypes = typeNames
        )
    }

    fun parsePostPage(rawJson: String, requestedPage: Int): ForumPostPage =
        buildPostPage(variables(rawJson), requestedPage)

    fun parseForumPostRatingSummaries(rawHtml: String): Map<String, ForumPostRatingSummary> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        return document.select("[id]")
            .filter { it.id().startsWith("ratelog_") }
            .mapNotNull { rateLog ->
                val postId = rateLog.id().removePrefix("ratelog_")
                    .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                    ?: return@mapNotNull null
                parseForumPostRatingSummary(rateLog)?.let { postId to it }
            }
            .toMap()
    }

    private fun variables(rawJson: String): JSONObject {
        val root = runCatching { JSON.parseObject(rawJson) }
            .getOrElse { throw IllegalStateException("论坛返回了无法解析的数据", it) }
        val message = root.getJSONObject("Message")
        val messageCode = message?.getString("messageval").orEmpty()
        if (messageCode.isNotBlank()) {
            val detail = message?.getString("messagestr")
                ?.let(::cleanText)
                ?.takeIf(String::isNotBlank)
            throw IllegalStateException(detail ?: "论坛暂时无法访问（$messageCode）")
        }
        return root.getJSONObject("Variables")
            ?: throw IllegalStateException("论坛返回数据不完整")
    }

    private fun parseBoard(value: JSONObject): ForumBoard {
        val id = value.stringValue("fid")
            ?: throw IllegalStateException("板块数据缺少 ID")
        val subforums = value.getJSONArray("sublist")
            ?.objects()
            ?.map(::parseBoard)
            .orEmpty()
        return ForumBoard(
            id = id,
            name = cleanText(value.getString("name")),
            description = cleanText(value.getString("description")),
            iconUrl = absoluteUrl(value.getString("icon")),
            parentId = value.stringValue("fup")?.takeUnless { it == "0" },
            threadCount = value.intValue("threads", value.intValue("threadcount")),
            postCount = value.intValue("posts"),
            todayPostCount = value.intValue("todayposts"),
            rank = value.getString("rank")?.toIntOrNull(),
            subforums = subforums
        )
    }

    private fun buildPostPage(variables: JSONObject, requestedPage: Int): ForumPostPage {
        val threadObject = variables.getJSONObject("thread")
            ?: throw IllegalStateException("论坛未返回主题详情")
        val postArray = variables.getJSONArray("postlist")
            ?: throw IllegalStateException("论坛未返回楼层数据")
        val thread = parseThreadDetail(threadObject, variables.getJSONObject("forum")?.stringValue("name"))
        val pageSize = variables.intValue("ppp", 10).coerceAtLeast(1)
        val totalPages = ceil((thread.replyCount + 1).toDouble() / pageSize).toInt().coerceAtLeast(1)
        return postPage(thread, postArray, requestedPage, totalPages)
    }

    private fun parseThreadDetail(value: JSONObject, fallbackForumName: String? = null): ForumThreadDetail {
        val id = value.stringValue("tid") ?: throw IllegalStateException("主题数据缺少 ID")
        val authorId = value.stringValue("authorid")?.takeUnless { it == "0" }
        return ForumThreadDetail(
            id = id,
            forumId = value.stringValue("fid").orEmpty(),
            forumName = cleanText(
                value.getString("forumname")
                    ?: value.getString("fname")
                    ?: fallbackForumName
            ),
            lastPoster = cleanText(
                value.getString("lastposter")
                    ?: value.getString("lastpostername")
            ),
            subject = cleanText(value.getString("subject")),
            author = ForumPostAuthor(
                id = authorId,
                name = cleanText(value.getString("author")),
                avatarUrl = avatarUrl(authorId)
            ),
            replyCount = value.intValue("replies"),
            viewCount = value.intValue("views"),
            isClosed = value.intValue("closed") != 0
        )
    }

    private fun postPage(
        thread: ForumThreadDetail,
        postArray: JSONArray,
        requestedPage: Int,
        totalPages: Int
    ): ForumPostPage {
        val posts = postArray.objects().map { parsePost(it, thread.id) }
        return ForumPostPage(
            thread = thread,
            posts = posts,
            page = requestedPage.coerceAtLeast(1),
            totalPages = totalPages,
            hasMore = requestedPage < totalPages
        )
    }

    private fun parsePost(value: JSONObject, threadId: String): ForumPost {
        val id = value.stringValue("pid") ?: throw IllegalStateException("楼层数据缺少 ID")
        val authorId = value.stringValue("authorid")?.takeUnless { it == "0" }
        val anonymous = value.intValue("anonymous") != 0
        val position = value.intValue("position", value.intValue("number")).coerceAtLeast(1)
        return ForumPost(
            id = id,
            threadId = value.stringValue("tid") ?: threadId,
            author = ForumPostAuthor(
                id = authorId.takeUnless { anonymous },
                name = cleanText(value.getString("author")).ifBlank { "匿名" },
                avatarUrl = avatarUrl(authorId).takeUnless { anonymous },
                isAnonymous = anonymous
            ),
            createdAt = cleanText(value.getString("dateline")),
            floor = value.intValue("number", position).coerceAtLeast(position),
            isOriginalPost = value.intValue("first") != 0 || position == 1,
            blocks = parsePostBlocks(value.getString("message")),
            attachments = parseAttachments(value["attachments"] ?: value["attachlist"])
        )
    }

    private fun parseForumPostRatingSummary(rateLog: Element): ForumPostRatingSummary? {
        val allAnchors = rateLog.select("a[href*='action=viewratings'][href*='pid=']")
        val participantText = cleanText(allAnchors.firstOrNull()?.text())
        val viewAllAnchor = allAnchors.lastOrNull()
        var scoreText = rateLog.select(".ratl th")
            .getOrNull(1)
            ?.let { cleanText(it.text()) }
            .orEmpty()
        val ratings = mutableListOf<ForumPostRating>()
        val viewAllText = cleanText(viewAllAnchor?.text())
        val mobileRows = rateLog.select("li.flex-box.mli.p0")

        if (mobileRows.isNotEmpty()) {
            mobileRows.forEach { row ->
                val rowText = cleanText(row.text())
                val cells = row.select("div")
                if (rowText.contains("参与人数") && rowText.contains("积分")) {
                    if (cells.size > 1) scoreText = cleanText(cells[1].text())
                    return@forEach
                }
                if (viewAllText.isNotBlank() && rowText == viewAllText) return@forEach
                parseForumPostRatingRow(row, "div")?.let(ratings::add)
            }
        } else {
            rateLog.select(".ratl_l tr[id^=rate_]").forEach { row ->
                parseForumPostRatingRow(row, "td")?.let(ratings::add)
            }
        }

        if (participantText.isBlank() && scoreText.isBlank() && ratings.isEmpty()) return null
        return ForumPostRatingSummary(
            participantText = participantText,
            scoreText = scoreText,
            ratings = ratings,
            viewAllUrl = viewAllAnchor?.attr("href")
                ?.let { absoluteUrl(it) }
        )
    }

    private fun parseForumPostRatingRow(row: Element, cellSelector: String): ForumPostRating? {
        val userAnchor = row.selectFirst("a[href]") ?: return null
        val userName = cleanText(userAnchor.text())
        if (userName.isBlank()) return null
        val cells = row.select(cellSelector)
        val userCellIndex = cells.indexOfFirst { it.selectFirst("a[href]") != null }
            .takeIf { it >= 0 }
            ?: -1
        val scoreIndex = if (userCellIndex >= 0) userCellIndex + 1 else 0
        return ForumPostRating(
            userName = userName,
            score = cells.getOrNull(scoreIndex)?.let { cleanText(it.text()) }.orEmpty(),
            reason = cells.getOrNull(scoreIndex + 1)?.let { cleanText(it.text()) }.orEmpty(),
            createdAt = cells.getOrNull(scoreIndex + 2)
                ?.let { cleanText(it.text()) }
                ?.takeIf(String::isNotBlank)
        )
    }

    private fun avatarUrl(userId: String?): String? =
        userId?.let { "$FORUM_ORIGIN/uc_server/avatar.php?uid=$it&size=small" }

    private fun extractThreadId(url: String): String? =
        Regex("(?:[?&]tid=|thread-)(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)

    private fun parseAttachments(raw: Any?): List<ForumPostAttachment> =
        jsonObjects(raw).map(::parseAttachment)

    private fun jsonObjects(raw: Any?): List<JSONObject> = when (raw) {
        is JSONArray -> raw.objects()
        is JSONObject -> raw.values.mapNotNull { it as? JSONObject }
        else -> emptyList()
    }

    private fun parseAttachment(value: JSONObject): ForumPostAttachment {
        val id = value.stringValue("aid") ?: throw IllegalStateException("附件数据缺少 ID")
        val attachment = value.getString("attachment").orEmpty().trim()
        val baseUrl = value.getString("url").orEmpty().trim()
        val path = when {
            attachment.startsWith("http") || attachment.startsWith("//") -> attachment
            baseUrl.isNotBlank() && attachment.isNotBlank() ->
                baseUrl.trimEnd('/') + "/" + attachment.trimStart('/')
            baseUrl.isNotBlank() -> baseUrl
            attachment.isNotBlank() -> "/data/attachment/forum/" + attachment.trimStart('/')
            else -> "/forum.php?mod=attachment&aid=$id"
        }
        val url = absoluteUrl(path) ?: throw IllegalStateException("附件地址无效")
        val filename = cleanText(value.getString("filename"))
            .ifBlank { attachment.substringAfterLast('/').ifBlank { "附件 $id" } }
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        val isImage = value.intValue("isimage") != 0 ||
            extension in setOf("avif", "bmp", "gif", "jpeg", "jpg", "png", "webp")
        return ForumPostAttachment(id, filename, url, isImage)
    }

    private fun parsePostBlocks(rawHtml: String?): List<ForumPostBlock> {
        if (rawHtml.isNullOrBlank()) return emptyList()
        val document = Jsoup.parseBodyFragment(rawHtml, FORUM_ORIGIN)
        document.select("script,style,.jammer,[style*=display:none]").remove()
        val blocks = mutableListOf<ForumPostBlock>()
        val textParts = mutableListOf<ForumPostTextPart>()

        fun appendText(value: String, url: String?) {
            val normalized = value
                .replace(160.toChar(), ' ')
                .replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return
            val last = textParts.lastOrNull()
            if (last != null && last.url == url) {
                textParts[textParts.lastIndex] = last.copy(text = last.text + normalized)
            } else {
                textParts += ForumPostTextPart(normalized, url)
            }
        }

        fun newline() {
            val current = textParts.lastOrNull() ?: return
            if (!current.text.endsWith("\n")) {
                textParts[textParts.lastIndex] = current.copy(text = current.text + "\n")
            }
        }

        fun flushText() {
            if (textParts.isEmpty()) return
            val mutable = textParts.toMutableList()
            mutable[0] = mutable.first().copy(text = mutable.first().text.trimStart())
            mutable[mutable.lastIndex] = mutable.last().copy(text = mutable.last().text.trimEnd())
            val cleaned = mutable.filter { it.text.isNotEmpty() }
            if (cleaned.isNotEmpty()) blocks += ForumPostBlock.Text(cleaned)
            textParts.clear()
        }

        fun walk(node: Node, inheritedUrl: String?) {
            when (node) {
                is TextNode -> appendText(node.text(), inheritedUrl)
                is Element -> {
                    val tag = node.normalName()
                    val link = if (tag == "a") {
                        node.absUrl("href").ifBlank { absoluteUrl(node.attr("href")).orEmpty() }
                            .takeIf(String::isNotBlank)
                    } else {
                        inheritedUrl
                    }
                    when (tag) {
                        "br" -> newline()
                        "img" -> {
                            flushText()
                            val source = sequenceOf("file", "zoomfile", "src")
                                .map(node::attr)
                                .firstOrNull(String::isNotBlank)
                            absoluteUrl(source)?.let {
                                blocks += ForumPostBlock.Image(it, node.attr("alt").trim())
                            }
                        }
                        else -> {
                            val isBlock = tag in setOf(
                                "blockquote", "div", "h1", "h2", "h3", "h4",
                                "h5", "h6", "li", "p", "pre", "table", "tr"
                            )
                            if (isBlock) newline()
                            node.childNodes().forEach { child -> walk(child, link) }
                            if (isBlock) newline()
                        }
                    }
                }
                else -> node.childNodes().forEach { child -> walk(child, inheritedUrl) }
            }
        }

        document.body().childNodes().forEach { walk(it, null) }
        flushText()
        return blocks
    }
    private fun cleanText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decodedText = Jsoup.parseBodyFragment(value).text().trim()
        return LanguageModeUtil.displayText(encodedHtmlTag.replace(decodedText) { match ->
            when (match.groupValues[1].lowercase()) {
                in inlineFormattingTags -> ""
                in blockFormattingTags -> " "
                else -> match.value
            }
        }.replace(Regex("\\s+"), " ").trim())
    }

    private fun absoluteUrl(value: String?): String? {
        val url = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return when {
            url.startsWith("https://") || url.startsWith("http://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$FORUM_ORIGIN$url"
            else -> "$FORUM_ORIGIN/$url"
        }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until size).mapNotNull(::getJSONObject)

    private fun JSONObject.stringValue(key: String): String? =
        get(key)?.toString()?.trim()?.takeIf(String::isNotBlank)

    private fun JSONObject.intValue(key: String, fallback: Int = 0): Int =
        get(key)?.toString()?.toIntOrNull() ?: fallback
}

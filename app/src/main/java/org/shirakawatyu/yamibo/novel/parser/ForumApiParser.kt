package org.shirakawatyu.yamibo.novel.parser

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAuthor
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostTextPart
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
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

        return ForumThreadPage(
            forum = forum,
            threads = threads,
            page = page,
            hasMore = hasMore
        )
    }

    fun parsePostPage(rawJson: String, requestedPage: Int): ForumPostPage =
        buildPostPage(variables(rawJson), requestedPage)

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
            subforums = subforums
        )
    }

    private fun buildPostPage(variables: JSONObject, requestedPage: Int): ForumPostPage {
        val threadObject = variables.getJSONObject("thread")
            ?: throw IllegalStateException("论坛未返回主题详情")
        val postArray = variables.getJSONArray("postlist")
            ?: throw IllegalStateException("论坛未返回楼层数据")
        val thread = parseThreadDetail(threadObject)
        val pageSize = variables.intValue("ppp", 10).coerceAtLeast(1)
        val totalPages = ceil((thread.replyCount + 1).toDouble() / pageSize).toInt().coerceAtLeast(1)
        return postPage(thread, postArray, requestedPage, totalPages)
    }

    private fun parseThreadDetail(value: JSONObject): ForumThreadDetail {
        val id = value.stringValue("tid") ?: throw IllegalStateException("主题数据缺少 ID")
        val authorId = value.stringValue("authorid")?.takeUnless { it == "0" }
        return ForumThreadDetail(
            id = id,
            forumId = value.stringValue("fid").orEmpty(),
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

    private fun avatarUrl(userId: String?): String? =
        userId?.let { "$FORUM_ORIGIN/uc_server/avatar.php?uid=$it&size=small" }

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
        return encodedHtmlTag.replace(decodedText) { match ->
            when (match.groupValues[1].lowercase()) {
                in inlineFormattingTags -> ""
                in blockFormattingTags -> " "
                else -> match.value
            }
        }.replace(Regex("\\s+"), " ").trim()
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

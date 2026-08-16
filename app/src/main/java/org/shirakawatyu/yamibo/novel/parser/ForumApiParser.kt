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
import org.shirakawatyu.yamibo.novel.bean.forum.ForumComment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostActionForm
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAttachment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAuthor
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPollOption
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostTextPart
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRateOption
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRatePopout
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import kotlin.math.ceil

data class ForumPageMetadata(
    val headImageUrl: String? = null,
    val todayPostCount: Int? = null,
    val threadCount: Int? = null,
    val rank: Int? = null
)

sealed class ForumReplyResult {
    object Posted : ForumReplyResult()
    data class PendingModeration(val message: String) : ForumReplyResult()
    data class Failed(val message: String) : ForumReplyResult()
}

object ForumApiParser {
    private const val FORUM_ORIGIN = "https://bbs.yamibo.com"
    private val postEditTimeRegex =
        Regex("""([0-9]{4}-[0-9]{1,2}-[0-9]{1,2}[ ]*[0-9]{1,2}:[0-9]{2})""")
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

    fun parseFavoriteForums(rawJson: String): List<ForumBoard> {
        val favoriteArray = variables(rawJson).getJSONArray("list") ?: return emptyList()
        return favoriteArray.objects().mapNotNull { favorite ->
            val id = favorite.stringValue("id")
                ?: favorite.stringValue("fid")
                ?: return@mapNotNull null
            val name = cleanText(favorite.getString("title") ?: favorite.getString("name"))
            if (name.isBlank()) return@mapNotNull null
            ForumBoard(
                id = id,
                name = name,
                description = cleanText(favorite.getString("description")),
                threadCount = favorite.intValue("threads"),
                postCount = favorite.intValue("posts"),
                todayPostCount = favorite.intValue("todayposts")
            )
        }.distinctBy(ForumBoard::id)
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

    fun parseForumPageMetadata(rawHtml: String): ForumPageMetadata {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val image = document.selectFirst(
            "#forum > div.forum-headimg img[src], .forum-headimg img[src]"
        )
        val headImageUrl = image?.let {
            it.absUrl("src").ifBlank {
                absoluteUrl(it.attr("src")).orEmpty()
            }.takeIf(String::isNotBlank)
        }
        val statistics = document.selectFirst(".forumdisplay-top p")
            ?.select("span")
            .orEmpty()
            .map { it.text().trim().replace(",", "").toIntOrNull() }
        return ForumPageMetadata(
            headImageUrl = headImageUrl,
            todayPostCount = statistics.getOrNull(0),
            threadCount = statistics.getOrNull(1),
            rank = statistics.getOrNull(2)
        )
    }

    fun parseForumHeadImage(rawHtml: String): String? =
        parseForumPageMetadata(rawHtml).headImageUrl

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

    /** 从主题网页中提取当前登录会话的 CSRF formhash。 */
    fun parseFormHash(rawHtml: String): String? =
        Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
            .selectFirst("input[name=formhash][value]")
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotBlank)

    /**
     * 解析 WebView 提取的帖子页 JSON。结构与 [parsePostPage] 一致，但额外字段
     * `extrasHtml` 含有 ratelog/poll，而 `formsHtml` 含有 rate/comment 表单。
     */
    fun parseWebPostPage(rawJson: String, requestedPage: Int): ForumPostPage {
        val root = runCatching { JSON.parseObject(rawJson) }
            .getOrElse { throw IllegalStateException("论坛网页提取结果无法解析", it) }
        val variables = root.getJSONObject("Variables")
            ?: throw IllegalStateException("论坛网页提取结果不完整")
        val parsed = buildPostPage(variables, requestedPage)
        val totalPages = root.getString("totalPages")
            ?.toIntOrNull()
            ?.coerceAtLeast(parsed.page)
            ?: parsed.totalPages
        val extrasHtml = root.getString("extrasHtml").orEmpty()
        val formsHtml = root.getString("formsHtml").orEmpty()
        val ratingSummaries = if (extrasHtml.isBlank()) emptyMap()
        else parseForumPostRatingSummaries(extrasHtml)
        val poll = if (extrasHtml.isBlank()) null else parseForumPoll(extrasHtml)
        val formsSource = listOf(extrasHtml, formsHtml).joinToString(separator = "\n")
        return parsed.copy(
            totalPages = totalPages,
            hasMore = parsed.page < totalPages,
            posts = parsed.posts.map { post ->
                val (rateForm, commentForm) = if (formsSource.isBlank()) {
                    null to null
                } else {
                    parsePostActionForms(formsSource, post.id)
                }
                val comments = if (extrasHtml.isBlank()) emptyList()
                else parseComments(extrasHtml, post.id)
                post.copy(
                    ratingSummary = ratingSummaries[post.id] ?: post.ratingSummary,
                    poll = poll.takeIf { post.isOriginalPost } ?: post.poll,
                    rateForm = rateForm ?: post.rateForm,
                    commentForm = commentForm ?: post.commentForm,
                    comments = if (comments.isNotEmpty()) comments else post.comments
                )
            }
        )
    }

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

    /**
     * 解析“全部评分”弹窗（`viewratings`）。优先按桌面版 `table.list` 解析；
     * 若返回 CDATA，则回退到手机版 `li.flex-box.mli`。
     */
    fun parseViewRatings(rawHtml: String): List<Pair<String, String>> {
        val body = if (rawHtml.contains("<![CDATA[")) {
            rawHtml.substringAfter("<![CDATA[").substringBeforeLast("]]>")
        } else rawHtml
        val document = Jsoup.parse(body, "$FORUM_ORIGIN/")
        val results = mutableListOf<Pair<String, String>>()
        document.select("table.list tr").forEach { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@forEach
            val userName = cleanText(cells.getOrNull(1)?.selectFirst("a[href]")?.text())
            val time = cleanText(cells.getOrNull(2)?.text())
            if (userName.isNotBlank() && time.isNotBlank()) {
                results += userName to time
            }
        }
        if (results.isNotEmpty()) return results
        document.select("li.flex-box.mli").mapNotNullTo(results) { row ->
            val userName = row.select(".flex-2.xs1.xg1 .z")
                .getOrNull(1)
                ?.let { cleanText(it.text()) }
                ?.ifBlank { null }
                ?: row.select("a[href*='uid='], a[href*='space-uid-']")
                    .lastOrNull()
                    ?.let { cleanText(it.text()) }
                    ?.ifBlank { null }
                ?: return@mapNotNullTo null
            val time = postEditTimeRegex.find(cleanText(row.text()))
                ?.value
                ?.replace(Regex("\\s+"), " ")
                ?: return@mapNotNullTo null
            userName to time
        }
        return results
    }

    /**
     * 解析“查看全部评分”的完整列表（`viewratings` 弹窗）。
     * 优先按桌面版 `table.list`（积分/用户名/时间/理由四列）解析；
     * 回退到手机版 `li.flex-box.mli`（积分/用户名/时间）。
     */
    fun parseAllRatings(rawHtml: String): List<ForumPostRating> {
        val body = if (rawHtml.contains("<![CDATA[")) {
            rawHtml.substringAfter("<![CDATA[").substringBeforeLast("]]>")
        } else rawHtml
        val document = Jsoup.parse(body, "$FORUM_ORIGIN/")

        document.selectFirst("table.list")?.let { table ->
            val rows = table.select("tr")
            var headerRowIndex = -1
            var scoreIndex = -1
            var userIndex = -1
            var timeIndex = -1
            var reasonIndex = -1
            for ((index, row) in rows.withIndex()) {
                val cells = row.select("th, td").map { cleanText(it.text()) }
                val score = cells.indexOfFirst { it == "积分" || it == "評分" }
                val user = cells.indexOfFirst { it.contains("用户名") || it.contains("用戶") }
                val time = cells.indexOfFirst { it.contains("时间") || it.contains("時間") }
                if (user >= 0) {
                    headerRowIndex = index
                    scoreIndex = score
                    userIndex = user
                    timeIndex = time
                    reasonIndex = cells.indexOfFirst { it.contains("理由") }
                    break
                }
            }
            if (headerRowIndex >= 0) {
                val ratings = rows.drop(headerRowIndex + 1).mapNotNull { row ->
                    val cells = row.select("td")
                    if (cells.size <= userIndex) return@mapNotNull null
                    // 用户名单元格可能包含头像链接（无文本），优先取带文本的链接。
                    val userName = cells[userIndex].select("a[href]")
                        .firstOrNull { cleanText(it.text()).isNotBlank() }
                        ?.let { cleanText(it.text()) }
                        ?.takeIf(String::isNotBlank)
                        ?: cleanText(cells[userIndex].text())
                    if (userName.isBlank()) return@mapNotNull null
                    val scoreText = cells.getOrNull(scoreIndex)
                        ?.let { cleanText(it.text()) }
                        .orEmpty()
                    // Discuz 某些模板会在表头后再插入一行“积分/用户名/—”摘要，
                    // 不能把它当作真实评分记录。
                    if (userName == "用户名" || userName == "用戶名" ||
                        scoreText == "积分" || scoreText == "評分"
                    ) return@mapNotNull null
                    val reason = when {
                        reasonIndex >= 0 -> cells.getOrNull(reasonIndex)?.let { cleanText(it.text()) }.orEmpty()
                        timeIndex >= 0 && cells.size > timeIndex + 1 ->
                            cleanText(cells[timeIndex + 1].text())
                        else -> row.selectFirst(".reason, .ratereason, [class*=reason]")
                            ?.let { cleanText(it.text()) }
                            .orEmpty()
                    }
                    ForumPostRating(
                        userName = userName,
                        score = normalizeRatingScore(scoreText),
                        reason = reason,
                        createdAt = cells.getOrNull(timeIndex)
                            ?.let { cleanText(it.text()) }
                            ?.takeIf(String::isNotBlank)
                    )
                }
                if (ratings.isNotEmpty()) return ratings
            }
        }

        // 手机版弹窗结构：表头行（积分/用户名/时间）→ 数据行（积分/用户名/时间）→
        // 理由独占一行（div.flex，无理由的评分没有这一行）。按顺序扫描，跳过表头，
        // 把理由行附加到最近一条评分上。
        val mobileRows = document.select("li.flex-box.mli")
        if (mobileRows.isNotEmpty()) {
            val result = mutableListOf<ForumPostRating>()
            for (row in mobileRows) {
                val rowText = cleanText(row.text())
                val zSpans = row.select(".flex-2.xs1.xg1 .z")
                val secondSpan = zSpans.getOrNull(1)
                    ?.let { cleanText(it.text()) }
                    ?.ifBlank { null }
                if (secondSpan != null) {
                    // 表头行：第二个 span 是“用户名”。
                    if (secondSpan == "用户名" || secondSpan == "用戶名") continue
                    val createdAt = postEditTimeRegex.find(rowText)
                        ?.value
                        ?.replace(Regex("\\s+"), " ")
                    result += ForumPostRating(
                        userName = secondSpan,
                        score = normalizeRatingScore(
                            zSpans.firstOrNull()?.let { cleanText(it.text()) }.orEmpty()
                        ),
                        reason = row.select(".reason, .ratereason, [class*=reason]")
                            .firstOrNull()
                            ?.let { cleanText(it.text()) }
                            .orEmpty(),
                        createdAt = createdAt
                    )
                } else {
                    // 无用户名列 → 可能是理由行（div.flex 单独一行）或“查看全部评分”行。
                    val reason = row.selectFirst("div.flex")
                        ?.let { cleanText(it.text()) }
                        ?.takeIf(String::isNotBlank)
                        ?: continue
                    if (reason == "查看全部评分" ||
                        reason.contains("参与人数") ||
                        postEditTimeRegex.containsMatchIn(rowText)
                    ) continue
                    val last = result.lastOrNull() ?: continue
                    if (last.reason.isBlank()) {
                        result[result.lastIndex] = last.copy(reason = reason)
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun normalizeRatingScore(value: String): String =
        Regex("[+-]?\\s*\\d+").find(value)?.value?.replace(" ", "") ?: value

    fun parseForumPoll(rawHtml: String): ForumPoll? {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val pollForm = document.selectFirst("form#poll") ?: return null
        val infoLines = pollForm.select(".poll_txt")
            .map { cleanText(it.text()) }
            .filter(String::isNotBlank)
        val typeLine = infoLines.firstOrNull().orEmpty()
        val participantCount = Regex("""共有[ ]*([0-9]+)[ ]*人""")
            .find(typeLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val resultRegex = Regex(
            """([0-9]+(?:[.][0-9]+)?)%[ ]*[(]([0-9]+)票[)]"""
        )
        val statusText = pollForm.selectFirst(".poll_box .xi1")
            ?.let { cleanText(it.text()) }
            ?.takeIf(String::isNotBlank)
        val options = pollForm.select(".poll_box p").mapNotNull { option ->
            val text = cleanText(option.selectFirst("label")?.text())
            if (text.isBlank()) return@mapNotNull null
            val result = resultRegex.find(cleanText(option.selectFirst("em")?.text()))
            ForumPollOption(
                text = text,
                percent = result?.groupValues?.getOrNull(1)?.toFloatOrNull(),
                voteCount = result?.groupValues?.getOrNull(2)?.toIntOrNull(),
                id = option.selectFirst("input[name='pollanswers[]']")
                    ?.attr("value")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            )
        }
        if (options.isEmpty()) return null
        return ForumPoll(
            typeText = typeLine.substringBefore(',').trim().ifBlank { "投票" },
            participantCount = participantCount,
            remainingText = infoLines.drop(1).firstOrNull(),
            options = options,
            statusText = statusText,
            formHash = pollForm.selectFirst("input[name=formhash]")
                ?.attr("value")
                ?.trim()
                ?.takeIf(String::isNotBlank),
            actionUrl = pollForm.absUrl("action").takeIf(String::isNotBlank),
            isMultipleChoice = typeLine.contains("多选"),
            hasVoted = statusText?.contains("投过票") == true
        )
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
        val rawMessage = value.getString("message")
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
            editedAt = parsePostEditedAt(rawMessage),
            floor = value.intValue("number", position).coerceAtLeast(position),
            isOriginalPost = value.intValue("first") != 0 || position == 1,
            blocks = parsePostBlocks(rawMessage),
            attachments = parseAttachments(value["attachments"] ?: value["attachlist"])
        )
    }

    private fun parsePostEditedAt(rawHtml: String?): String? {
        if (rawHtml.isNullOrBlank()) return null
        val statusText = Jsoup.parseBodyFragment(rawHtml, FORUM_ORIGIN)
            .select(".pstatus")
            .text()
        return postEditTimeRegex.find(statusText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex(" +"), " ")
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
        // 桌面版行内第一个 <a> 可能是头像链接（内部只有 <img>，没有文本），
        // 取第一个带文本的链接作为用户名。
        val userAnchor = row.select("a[href]")
            .firstOrNull { cleanText(it.text()).isNotBlank() }
            ?: return null
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
        document.select("script,style,.jammer,.pstatus,[style*=display:none]").remove()
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

    /**
     * 解析评分表单弹窗（GET ...action=rate）。返回的 HTML 包含评分下拉、理由下拉与表单 hash。
     * CDATA 与 HTML 双格式都兼容。
     */
    fun parseRatePopout(rawHtml: String): ForumRatePopout? {
        val body = if (rawHtml.contains("<![CDATA[")) {
            rawHtml.substringAfter("<![CDATA[").substringBeforeLast("]]>")
        } else rawHtml
        val document = Jsoup.parse(body, "$FORUM_ORIGIN/")
        val availableScores = document.select("select#rate1 option").mapNotNull { option ->
            val raw = option.attr("value").ifEmpty { option.text() }.trim()
            val score = raw.toIntOrNull() ?: return@mapNotNull null
            ForumRateOption(score = score, label = option.text().trim().ifBlank { raw })
        }
        val defaultReasons = document.select("select#reason option")
            .map { it.attr("value").ifEmpty { it.text() }.trim() }
            .filter { it.isNotEmpty() }
        val formHash = document.selectFirst("input[name=formhash]")
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (availableScores.isEmpty() && defaultReasons.isEmpty() && formHash == null) {
            return null
        }
        return ForumRatePopout(
            availableScores = availableScores,
            defaultReasons = defaultReasons,
            formHash = formHash
        )
    }

    /**
     * 解析 Discuz 操作反馈（POST 接口返回的 CDATA/XML）。成功时通常包含
     * `succeedhandle` 或 `reload="1"`，并把 `#messagetext p` 中的中文提示提取出来。
     */
    fun parsePostActionResponse(body: String): String? {
        val html = if (body.contains("<![CDATA[")) {
            body.substringAfter("<![CDATA[").substringBeforeLast("]]>")
        } else body
        val doc = Jsoup.parse(html, "$FORUM_ORIGIN/")
        val message = doc.selectFirst("#messagetext p")
            ?.let { element ->
                element.select("script").remove()
                element.text().trim()
            }
            ?: doc.selectFirst("#messagetext")
                ?.let { element ->
                    element.select("script").remove()
                    cleanText(element.text())
                }
        if (!message.isNullOrBlank()) return message
        return when {
            body.contains("succeedhandle") -> "操作成功"
            body.contains("errorhandle") -> "操作失败"
            body.contains("reload=\"1\"") -> "操作成功"
            else -> null
        }
    }

    /**
     * 解析移动 API `module=sendreply` 的 JSON 返回。
     * 区分「直接发布成功」「需要审核」「失败」三种结果。
     */
    fun parseSendReplyResponse(rawJson: String): ForumReplyResult {
        val root = runCatching { JSON.parseObject(rawJson) }
            .getOrElse { throw IllegalStateException("回复返回内容异常，请稍后重试", it) }
        val message = root.getJSONObject("Message")
        val messageVal = message?.getString("messageval").orEmpty()
        val messageStr = cleanText(message?.getString("messagestr"))
        val pid = root.getJSONObject("Variables")?.getString("pid")
        return when {
            messageVal == "post_reply_succeed" -> ForumReplyResult.Posted
            messageVal == "post_reply_mod_succeed" ->
                ForumReplyResult.PendingModeration(messageStr.ifBlank { "回复已提交，等待审核" })
            pid != null && pid != "0" && messageVal.isBlank() -> ForumReplyResult.Posted
            else -> ForumReplyResult.Failed(messageStr.takeIf { it.isNotBlank() } ?: "回复失败，请稍后重试")
        }
    }

    /**
     * 为引用回复构造 Discuz 标准引用 BBCode。移动 API `sendreply` 不支持
     * `repquote` 参数，引用楼层时需把引用块拼进正文提交。
     */
    fun buildReplyMessageWithQuote(quotePost: ForumPost, message: String): String {
        val quotedText = quotePost.blocks.mapNotNull { block ->
            (block as? ForumPostBlock.Text)?.parts?.joinToString("") { it.text }
        }.joinToString("\n").trim()
        val truncated = if (quotedText.length > QUOTE_MAX_LENGTH) {
            quotedText.take(QUOTE_MAX_LENGTH) + "…"
        } else {
            quotedText
        }
        val header = "[quote][size=2][color=#999999]" +
            "[url=forum.php?mod=redirect&goto=findpost&pid=${quotePost.id}]" +
            "${quotePost.author.name} 发表于 ${quotePost.createdAt}[/url]" +
            "[/color][/size]\n"
        return buildString {
            append(header)
            append(truncated)
            append("\n[/quote]\n")
            append(message)
        }
    }

    private const val QUOTE_MAX_LENGTH = 300

    /**
     * 解析帖子下方的点评列表。HTML 中点评在 `#comment_<postId>` 容器下，
     * 同时兼容移动版 `commentdetail_` 与桌面版 `.pstl` 两种结构。
     */
    fun parseComments(rawHtml: String, postId: String): List<ForumComment> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val container = document.selectFirst("#comment_$postId") ?: return emptyList()
        return parseCommentContainer(container, postId)
    }

    /**
     * 解析整页 HTML 中全部楼层的点评，返回 postId -> 点评列表。
     */
    fun parseForumPostComments(rawHtml: String): Map<String, List<ForumComment>> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        return document.select("[id^=comment_]")
            .filter { element ->
                val suffix = element.id().removePrefix("comment_")
                suffix.isNotBlank() && suffix.all(Char::isDigit)
            }
            .mapNotNull { container ->
                val postId = container.id().removePrefix("comment_")
                val comments = parseCommentContainer(container, postId)
                if (comments.isEmpty()) null else postId to comments
            }
            .toMap()
    }

    /** 从网页主题页补充各楼层的最后编辑时间，兼容桌面/手机版 postmessage 容器。 */
    fun parseForumPostEditedTimes(rawHtml: String): Map<String, String> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        return document.select("[id^=postmessage_]").mapNotNull { message ->
            val postId = message.id().removePrefix("postmessage_")
                .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?: return@mapNotNull null
            val status = message.selectFirst(".pstatus")?.text().orEmpty()
            val editedAt = postEditTimeRegex.find(cleanText(status))
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(Regex(" +"), " ")
                ?: return@mapNotNull null
            postId to editedAt
        }.toMap()
    }

    private fun parseCommentContainer(container: Element, postId: String): List<ForumComment> {
        val mobileComments = container.select("[id^=commentdetail_]").mapNotNull { element ->
            val authorAnchor = element.selectFirst(".authi .z a")
            val authorName = cleanText(authorAnchor?.text())
            val message = cleanText(element.selectFirst(".mtxt")?.text())
            if (authorName.isBlank() || message.isBlank()) return@mapNotNull null
            val authorUid = authorAnchor?.attr("href")?.let(::extractUidFromSpaceUrl)
            val avatar = element.selectFirst(".avatar img, .user_avatar")
                ?.attr("src")
                ?.takeIf(String::isNotBlank)
            val createdAt = cleanText(element.selectFirst(".mtime")?.text())
            ForumComment(
                id = element.id().removePrefix("commentdetail_"),
                authorName = authorName,
                authorUid = authorUid,
                authorAvatarUrl = avatar,
                createdAt = createdAt,
                message = message
            )
        }
        if (mobileComments.isNotEmpty()) return mobileComments
        // 桌面版结构：.pstl > .psta（头像 + 作者）与 .psti（正文 + 发表时间）
        return container.select(".pstl").mapIndexedNotNull { index, element ->
            val authorAnchor = element.selectFirst(".psta a.xw1")
                ?: element.selectFirst(".psta a[href*=space-uid]")
                ?: element.selectFirst(".psta a[href*=uid=]")
            val authorName = cleanText(authorAnchor?.text())
            val content = element.selectFirst(".psti") ?: return@mapIndexedNotNull null
            val createdAt = cleanText(content.selectFirst(".xg1")?.text())
                .removePrefix("发表于")
                .trim()
            val contentClone = content.clone()
            contentClone.select(".xg1").remove()
            val message = cleanText(contentClone.text())
            if (authorName.isBlank() || message.isBlank()) return@mapIndexedNotNull null
            val authorUid = authorAnchor?.attr("href")?.let(::extractUidFromSpaceUrl)
            val avatar = element.selectFirst(".psta img")
                ?.attr("src")
                ?.takeIf(String::isNotBlank)
            ForumComment(
                id = "${postId}_$index",
                authorName = authorName,
                authorUid = authorUid,
                authorAvatarUrl = avatar,
                createdAt = createdAt,
                message = message
            )
        }
    }

    /**
     * 在帖子 HTML 中解析评分/点评表单（用于后续原生提交）。每个表单
     * 包含 `action` URL 与 `formhash`。找不到则返回空表单。
     */
    fun parsePostActionForms(rawHtml: String, postId: String): Pair<ForumPostActionForm?, ForumPostActionForm?> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val rateForm = document.selectFirst("form#rateform_$postId")
            ?: document.selectFirst("form[id^=rateform_]")
                ?.takeIf { form -> form.selectFirst("input[name=pid]")?.attr("value") == postId }
        val commentForm = document.selectFirst("form#commentform_$postId")
            ?: document.selectFirst("form[id^=commentform_]")
                ?.takeIf { form -> form.selectFirst("input[name=pid]")?.attr("value") == postId }
        return parseActionForm(rateForm) to parseActionForm(commentForm)
    }

    /**
     * 一次性解析整页 HTML 中全部评分/点评表单，返回 postId -> (评分表单, 点评表单)。
     * 供主链路合并时使用，避免对同一份 HTML 反复解析。
     */
    fun parseAllPostActionForms(rawHtml: String): Map<String, Pair<ForumPostActionForm?, ForumPostActionForm?>> {
        val document = Jsoup.parse(rawHtml, "$FORUM_ORIGIN/")
        val rateForms = mutableMapOf<String, ForumPostActionForm?>()
        val commentForms = mutableMapOf<String, ForumPostActionForm?>()
        document.select("form[id^=rateform_]").forEach { form ->
            val postId = form.selectFirst("input[name=pid]")?.attr("value")?.trim()
                ?.takeIf(String::isNotBlank)
                ?: form.id().removePrefix("rateform_").takeIf(String::isNotBlank)
                ?: return@forEach
            rateForms[postId] = parseActionForm(form)
        }
        document.select("form[id^=commentform_]").forEach { form ->
            val postId = form.selectFirst("input[name=pid]")?.attr("value")?.trim()
                ?.takeIf(String::isNotBlank)
                ?: form.id().removePrefix("commentform_").takeIf(String::isNotBlank)
                ?: return@forEach
            commentForms[postId] = parseActionForm(form)
        }
        return (rateForms.keys + commentForms.keys)
            .associateWith { postId -> rateForms[postId] to commentForms[postId] }
    }

    private fun parseActionForm(form: Element?): ForumPostActionForm? {
        if (form == null) return null
        val type = when {
            form.attr("id").startsWith("rateform_") -> ForumPostActionForm.Type.RATE
            form.attr("id").startsWith("commentform_") -> ForumPostActionForm.Type.COMMENT
            else -> return null
        }
        val actionUrl = form.absUrl("action").takeIf(String::isNotBlank)
            ?: form.attr("action").takeIf(String::isNotBlank)?.let { action ->
                runCatching {
                    Jsoup.parseBodyFragment("<a href=\"$action\">x</a>", "$FORUM_ORIGIN/")
                        .selectFirst("a")?.absUrl("href")
                }.getOrNull()
            }
        val formHash = form.selectFirst("input[name=formhash]")?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (actionUrl.isNullOrBlank() && formHash == null) return null
        return ForumPostActionForm(type = type, actionUrl = actionUrl.orEmpty(), formHash = formHash)
    }

    private fun extractUidFromSpaceUrl(url: String): String? =
        Regex("(?:[?&]uid=|space-uid-|uid-)(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
}

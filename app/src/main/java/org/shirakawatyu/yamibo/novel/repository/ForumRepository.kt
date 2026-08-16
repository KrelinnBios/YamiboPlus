package org.shirakawatyu.yamibo.novel.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumComment
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostActionForm
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRateOption
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRatePopout
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.network.ForumApi
import org.shirakawatyu.yamibo.novel.network.ProfileApi
import org.shirakawatyu.yamibo.novel.parser.ForumApiParser
import org.shirakawatyu.yamibo.novel.parser.ForumPageMetadata
import org.shirakawatyu.yamibo.novel.parser.ForumReplyResult
import org.shirakawatyu.yamibo.novel.parser.ProfileApiParser
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import org.shirakawatyu.yamibo.novel.util.browser.ForumVerificationRequiredException
import okhttp3.Request

private data class ForumThreadHtmlExtras(
    val ratingSummaries: Map<String, ForumPostRatingSummary> = emptyMap(),
    val comments: Map<String, List<ForumComment>> = emptyMap(),
    val actionForms: Map<String, Pair<ForumPostActionForm?, ForumPostActionForm?>> = emptyMap(),
    val editedTimes: Map<String, String> = emptyMap(),
    val poll: ForumPoll? = null,
    val html: String = ""
)

class ForumRepository(
    private val api: ForumApi = YamiboRetrofit.getInstance().create(ForumApi::class.java),
    private val profileApi: ProfileApi = YamiboRetrofit.getInstance().create(ProfileApi::class.java),
    private val webPageDataSource: WebForumDataSource? = null
) {
    private var cachedBanners: List<ForumBanner> = emptyList()
    private var bannerFetchTimeMillis: Long = 0L
    private val cachedHeadImages = mutableMapOf<String, String?>()

    private val browserDataSource: WebForumDataSource? by lazy {
        GlobalData.applicationContext?.let { WebForumDataSource(it) }
    }

    /** 判断拉取到的 HTML 是否是真正的主题页（而非 WAF 挑战页或空响应）。 */
    private fun looksLikeThreadPage(html: String): Boolean =
        html.contains("postmessage_") || html.contains("ratelog_") ||
            html.contains("postlist") || html.contains("mod=viewthread")

    suspend fun getForumIndex(): ForumIndex = coroutineScope {
        val indexDeferred = async {
            ForumApiParser.parseForumIndex(api.getForumIndex().string())
        }
        val favoritesDeferred = async {
            runCatching {
                ForumApiParser.parseFavoriteForums(api.getFavoriteForums().string())
            }.getOrDefault(emptyList())
        }
        val index = indexDeferred.await()
        val favorites = favoritesDeferred.await()
        if (favorites.isEmpty()) return@coroutineScope index

        val boardsById = buildMap<String, ForumBoard> {
            fun addBoard(board: ForumBoard) {
                put(board.id, board)
                board.subforums.forEach(::addBoard)
            }
            index.categories.flatMap(ForumCategory::forums).forEach(::addBoard)
        }
        val resolvedFavorites = favorites.map { favorite ->
            boardsById[favorite.id] ?: favorite
        }
        index.copy(
            categories = listOf(
                ForumCategory(
                    id = "favorite-forums",
                    name = "我收藏的版块",
                    forums = resolvedFavorites
                )
            ) + index.categories
        )
    }

    suspend fun getForumBanners(): List<ForumBanner> {
        val now = System.currentTimeMillis()
        if (bannerFetchTimeMillis > 0L && now - bannerFetchTimeMillis < BANNER_CACHE_MILLIS) {
            return cachedBanners
        }
        return ForumApiParser.parseForumBanners(api.getForumHome().string()).also {
            // 解析失败/未命中时返回空列表，避免把空结果缓存 30 分钟
            if (it.isNotEmpty()) {
                cachedBanners = it
                bannerFetchTimeMillis = now
            }
        }
    }

    suspend fun getThreads(
        forumId: String,
        page: Int,
        orderBy: String? = null,
        filter: String? = null,
        typeId: String? = null
    ): ForumThreadPage = coroutineScope {
        val threadPageDeferred = async {
            ForumApiParser.parseThreadPage(
                api.getForumThreads(
                    forumId,
                    page,
                    orderBy = orderBy,
                    filter = filter,
                    typeId = typeId
                ).string()
            )
        }
        val metadataDeferred = if (page == 1) {
            async { getForumPageMetadata(forumId) }
        } else {
            null
        }
        val threadPage = threadPageDeferred.await()
        val metadata = metadataDeferred?.await()
        if (metadata == null) {
            threadPage
        } else {
            threadPage.copy(
                forum = threadPage.forum.copy(
                    headImageUrl = metadata.headImageUrl ?: threadPage.forum.headImageUrl,
                    todayPostCount = metadata.todayPostCount ?: threadPage.forum.todayPostCount,
                    threadCount = metadata.threadCount ?: threadPage.forum.threadCount,
                    rank = metadata.rank ?: threadPage.forum.rank
                )
            )
        }
    }

    private suspend fun getForumPageMetadata(forumId: String): ForumPageMetadata? {
        val metadata = runCatching {
            ForumApiParser.parseForumPageMetadata(
                api.getForumDisplayPage(forumId = forumId).string()
            )
        }.getOrNull()
        metadata?.headImageUrl?.let { imageUrl ->
            synchronized(cachedHeadImages) {
                cachedHeadImages[forumId] = imageUrl
            }
        }
        if (metadata != null) return metadata
        val cachedHeadImage = synchronized(cachedHeadImages) { cachedHeadImages[forumId] }
        return cachedHeadImage?.let { ForumPageMetadata(headImageUrl = it) }
    }

    suspend fun getPosts(threadId: String, page: Int, authorId: String? = null): ForumPostPage {
        val apiResult = try {
            getPostsFromApi(threadId, page, authorId)
        } catch (error: CancellationException) {
            throw error
        } catch (apiError: Exception) {
            AppErrorLog.record("帖子 API 数据源失败，回退浏览器：${apiError.message}")
            null
        }

        // API 与网页补充数据（评分/点评/投票/编辑时间）都拉取成功时直接返回；
        // 网页补充缺失（如被 WAF 拦截导致 HTML 拉取失败）时也走浏览器数据源补齐。
        if (apiResult != null && looksLikeThreadPage(apiResult.html)) return apiResult

        val dataSource = webPageDataSource ?: browserDataSource
        if (dataSource == null) {
            apiResult?.let { return it }
            throw IllegalStateException("论坛数据源不可用，请稍后重试")
        }
        return try {
            dataSource.getPosts(threadId, page, authorId)
        } catch (error: CancellationException) {
            throw error
        } catch (browserError: Exception) {
            if (browserError is ForumVerificationRequiredException) throw browserError
            // 浏览器兜底也失败：API 已拿到正文时退回仅有正文的结果，避免整页报错。
            apiResult?.let { return it }
            throw browserError
        }
    }

    private suspend fun getPostsFromApi(
        threadId: String,
        page: Int,
        authorId: String?
    ): ForumPostPage = coroutineScope {
            val postPageDeferred = async {
                ForumApiParser.parsePostPage(
                    api.getThreadPosts(threadId, page, authorId).string(),
                    page
                )
            }
            val htmlExtrasDeferred = async {
                withTimeoutOrNull(THREAD_HTML_FETCH_TIMEOUT_MILLIS) {
                    runCatching {
                        val threadHtml = api.getThreadPage(
                            threadId = threadId,
                            page = page
                        ).string()
                        ForumThreadHtmlExtras(
                            ratingSummaries = ForumApiParser.parseForumPostRatingSummaries(threadHtml),
                            comments = ForumApiParser.parseForumPostComments(threadHtml),
                            actionForms = ForumApiParser.parseAllPostActionForms(threadHtml),
                            editedTimes = ForumApiParser.parseForumPostEditedTimes(threadHtml),
                            poll = ForumApiParser.parseForumPoll(threadHtml),
                            html = threadHtml
                        )
                    }.getOrDefault(ForumThreadHtmlExtras())
                } ?: ForumThreadHtmlExtras()
            }
            val postPage = postPageDeferred.await()
            val htmlExtras = htmlExtrasDeferred.await()
            val hasHtmlExtras = htmlExtras.ratingSummaries.isNotEmpty() ||
                htmlExtras.comments.isNotEmpty() ||
                htmlExtras.actionForms.isNotEmpty() ||
                htmlExtras.editedTimes.isNotEmpty() ||
                htmlExtras.poll != null
            val mergedPage = if (!hasHtmlExtras) {
                postPage.copy(html = htmlExtras.html)
            } else {
                postPage.copy(
                    posts = postPage.posts.map { post ->
                        val (rateForm, commentForm) = htmlExtras.actionForms[post.id]
                            ?: (null to null)
                        post.copy(
                            ratingSummary = htmlExtras.ratingSummaries[post.id]
                                ?: post.ratingSummary,
                            poll = htmlExtras.poll.takeIf { post.isOriginalPost }
                                ?: post.poll,
                            comments = htmlExtras.comments[post.id]
                                ?.takeIf(List<ForumComment>::isNotEmpty)
                                ?: post.comments,
                            rateForm = rateForm ?: post.rateForm,
                            commentForm = commentForm ?: post.commentForm,
                            editedAt = post.editedAt ?: htmlExtras.editedTimes[post.id]
                        )
                    },
                    html = htmlExtras.html
                )
            }
            // 评分完整列表在用户打开“查看全部评分”时按需加载，避免首屏为每个楼层
            // 额外请求 viewratings，尤其是长主题会明显拖慢评分/点评入口。
            mergedPage
        }

    /**
     * 拉取楼层完整评分列表（`viewratings` 弹窗），用于“查看全部评分”。
     */
    suspend fun getAllRatings(threadId: String, postId: String): List<ForumPostRating> {
        return ForumApiParser.parseAllRatings(
            api.getAllRatings(threadId = threadId, postId = postId).string()
        )
    }

suspend fun votePoll(poll: ForumPoll, optionIds: List<String>) {
        val formHash = poll.formHash?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("投票校验已失效，请刷新页面")
        if (optionIds.isEmpty()) throw IllegalArgumentException("请选择投票选项")

        val response = api.votePoll(
            forumId = poll.actionUrl?.let { url ->
                Regex("(?<=[?&])fid=(\\d+)").find(url)?.groupValues?.getOrNull(1)
            } ?: throw IllegalStateException("投票链接已失效，请刷新页面"),
            threadId = poll.actionUrl?.let { url ->
                Regex("(?<=[?&])tid=(\\d+)").find(url)?.groupValues?.getOrNull(1)
            } ?: throw IllegalStateException("投票链接已失效，请刷新页面"),
            formHash = formHash,
            optionIds = optionIds
        ).string()
        // Discuz 的 votepoll 在非 inajax 提交下，成功时可能返回提示页，也可能直接 302
        // 落回帖子页——后者响应里没有任何成功标记，之前被误判为失败（实际投票已生效）。
        // 这里补充「落回帖子页且无错误标记」也视为成功，保证投票后能正常刷新状态。
        val looksLikeThreadPage = response.contains("postlist") ||
            response.contains("mod=viewthread") ||
            response.contains("formhash")
        if (!response.contains("succeedhandle") &&
            !response.contains("投票成功") &&
            !response.contains("reload=\"1\"") &&
            !(looksLikeThreadPage && !response.contains("errorhandle"))
        ) {
            throw IllegalStateException("投票失败，请刷新页面后重试")
        }
    }

    /**
     * 提交帖子点评（comment）。若帖子 HTML 中没有携带点评 form 详情，则降级使用登录态
     * 全局 formhash（来自 profile 接口），并把 threadId 放进提交路径里。
     */
    suspend fun commentPost(
        threadId: String,
        postId: String,
        message: String,
        form: ForumPostActionForm? = null
    ) {
        if (message.isBlank()) throw IllegalArgumentException("请输入点评内容")
        val formHash = form?.formHash?.takeIf(String::isNotBlank)
            ?: GlobalData.currentFormHash.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("点评校验已失效，请刷新页面或重新登录")
        val response = api.submitComment(
            threadId = threadId,
            postId = postId,
            formHash = formHash,
            message = message
        ).string()
        if (!response.contains("succeedhandle") &&
            !response.contains("刷新") &&
            !response.contains("reload=\"1\"") &&
            !response.contains("点评发表成功")
        ) {
            throw IllegalStateException("点评失败，请稍后重试")
        }
    }

    /**
     * 获取评分弹窗信息（可选分值、常用理由、formhash）。拉取失败时降级为
     * 帖子自带 form 或登录态全局 formhash，并给出默认可选分值。
     */
    suspend fun getRatePopout(
        threadId: String,
        postId: String,
        fallbackFormHash: String? = null
    ): ForumRatePopout {
        val fetched = withTimeoutOrNull(RATE_POPOUT_TIMEOUT_MILLIS) {
            runCatching {
                ForumApiParser.parseRatePopout(
                    api.getRatePopout(threadId = threadId, postId = postId).string()
                )
            }.getOrNull()
        }
        if (fetched != null && (!fetched.availableScores.isEmpty() || fetched.formHash != null)) {
            return fetched
        }
        val fallbackHash = fetched?.formHash
            ?: fallbackFormHash?.takeIf(String::isNotBlank)
            ?: GlobalData.currentFormHash.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("评分校验已失效，请刷新页面或重新登录")
        // Discuz 普通用户默认可评 ±3 分；作为弹窗拉取失败时的兜底选项
        val fallbackScores = (-3..3).map { ForumRateOption(score = it, label = if (it > 0) "+$it" else "$it") }
        return ForumRatePopout(availableScores = fallbackScores, formHash = fallbackHash)
    }

    /**
     * 提交帖子评分（rate）。优先使用评分弹窗返回的 formhash。
     */
    suspend fun ratePost(
        threadId: String,
        postId: String,
        score: Int,
        reason: String,
        formHash: String? = null
    ) {
        val hash = formHash?.takeIf(String::isNotBlank)
            ?: GlobalData.currentFormHash.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("评分校验已失效，请刷新页面或重新登录")
        val response = api.submitRate(
            threadId = threadId,
            postId = postId,
            formHash = hash,
            score = score.toString(),
            reason = reason
        ).string()
        val message = ForumApiParser.parsePostActionResponse(response)
        if (!response.contains("succeedhandle") && !response.contains("reload=\"1\"")) {
            throw IllegalStateException(message ?: "评分失败，请稍后重试")
        }
    }

    /**
     * 回复主题。quotePost 非空时以引用该楼的形式回复。
     * 优先走移动 API `module=sendreply`（返回 JSON，成功/失败判定可靠）；
     * 引用楼层的 `repquote` 仅网页表单支持，因此引用时先把标准引用 BBCode
     * 拼进正文再用移动 API 提交。移动 API 失败时回退网页表单（带 repquote）。
     * `forumId`（fid）是两端提交的必填字段，缺失会导致服务端误报成功但未写入楼层。
     */
    suspend fun replyThread(
        threadId: String,
        forumId: String,
        message: String,
        quotePost: ForumPost? = null
    ): String {
        if (message.isBlank()) throw IllegalArgumentException("请输入回复内容")
        val fid = forumId.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("回复校验已失效，请刷新页面")
        val formHash = resolveFormHash(threadId)

        // 1) 移动 API 优先
        try {
            val quotedMessage = if (quotePost == null) message
            else ForumApiParser.buildReplyMessageWithQuote(quotePost, message)
            val response = api.sendReplyMobile(
                forumId = fid,
                threadId = threadId,
                formHash = formHash,
                message = quotedMessage,
                referer = threadReferer(threadId)
            ).string()
            return when (val result = ForumApiParser.parseSendReplyResponse(response)) {
                is ForumReplyResult.Posted -> {
                    AppErrorLog.record("回复移动API结果：已发表")
                    "回复已发表"
                }
                is ForumReplyResult.PendingModeration -> {
                    AppErrorLog.record("回复移动API结果：等待审核：${result.message}")
                    result.message
                }
                is ForumReplyResult.Failed -> {
                    AppErrorLog.record("回复移动API结果：失败：${result.message}")
                    throw IllegalStateException(result.message)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 内容过短是明确的服务端规则，直接给出友好提示，回退网页表单也会被同样拒绝。
            if (error.message?.contains("post_message_tooshort") == true ||
                error.message?.contains("小于") == true ||
                error.message?.contains("太短") == true
            ) {
                throw IllegalStateException(minLengthError(error.message, message))
            }
            AppErrorLog.record("回复移动 API 失败，回退网页表单：${error.message}")
        }

        // 2) 网页表单兜底（服务端 repquote）
        val response = api.submitReply(
            threadId = threadId,
            forumIdQuery = fid,
            forumId = fid,
            repquote = quotePost?.id,
            formHash = formHash,
            message = message,
            referer = threadReferer(threadId)
        ).string()
        val result = ForumApiParser.parsePostActionResponse(response)
        if (!response.contains("succeedhandle") && !response.contains("reload=\"1\"")) {
            if (response.contains("小于") || response.contains("minpostsize")) {
                val detail = minLengthError(response, message)
                AppErrorLog.record("回复网页表单结果：失败：$detail")
                throw IllegalStateException(detail)
            }
            val detail = result ?: "回复失败，请稍后重试"
            AppErrorLog.record("回复网页表单结果：失败：$detail")
            throw IllegalStateException(detail)
        }
        AppErrorLog.record("回复网页表单结果：已发表")
        return if (result == null) "回复已发表" else result
    }

    /** 把 Discuz 最短字数限制翻译成带当前字数的友好提示。 */
    private fun minLengthError(raw: String?, typed: String): String {
        val min = Regex("minpostsize'?:\\s*'(\\d+)'")
            .find(raw.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 21
        return "回复内容至少需要 $min 个字符（当前 ${typed.length} 个）"
    }

    private fun threadReferer(threadId: String): String =
        "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$threadId&mobile=2"

    /**
     * 解析当前登录会话的 CSRF formhash，供原生提交（回复/点评/评分）使用。
     * 优先级：内存缓存 → 移动 profile 接口 → 主题网页。全部失败则抛错提示重登。
     * 这样即便用户从未进入“我的”页，也能在提交时按需补取，避免“校验已失效”。
     */
    private suspend fun resolveFormHash(threadId: String): String {
        GlobalData.currentFormHash.takeIf { it.isNotBlank() }?.let { return it }

        val fromProfile = try {
            ProfileApiParser.parseProfile(profileApi.getUserProfile().string()).formhash
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppErrorLog.record("回复 formhash profile 获取失败：${error.message}")
            null
        }?.takeIf(String::isNotBlank)
        if (fromProfile != null) {
            GlobalData.currentFormHash = fromProfile
            return fromProfile
        }

        val fromThread = try {
            ForumApiParser.parseFormHash(api.getThreadPage(threadId = threadId).string())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppErrorLog.record("回复 formhash 主题页获取失败：${error.message}")
            null
        }?.takeIf(String::isNotBlank)
        if (fromThread != null) {
            GlobalData.currentFormHash = fromThread
            return fromThread
        }

        throw IllegalStateException("回复校验已失效，请刷新页面或重新登录")
    }

    suspend fun resolveThreadId(url: String): String? {
        val normalized = YamiboPostLinkUtil.normalizePostUrl(url) ?: return null
        YamiboPostLinkUtil.extractThreadId(normalized)?.let { return it }
        val request = Request.Builder().url(normalized).get().build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            YamiboPostLinkUtil.extractThreadId(response.request.url.toString())
                ?: response.header("Location")?.let(YamiboPostLinkUtil::extractThreadId)
        }
    }

    private companion object {
        const val BANNER_CACHE_MILLIS = 30L * 60L * 1000L
        const val THREAD_HTML_FETCH_TIMEOUT_MILLIS = 8_000L
        const val RATE_POPOUT_TIMEOUT_MILLIS = 2_500L
    }
}

package org.shirakawatyu.yamibo.novel.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostActionForm
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRateOption
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRatePopout
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.network.ForumApi
import org.shirakawatyu.yamibo.novel.network.ProfileApi
import org.shirakawatyu.yamibo.novel.parser.ForumApiParser
import org.shirakawatyu.yamibo.novel.parser.ForumPageMetadata
import org.shirakawatyu.yamibo.novel.parser.ProfileApiParser
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import okhttp3.Request

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
    ): ForumThreadPage {
        val html = api.getForumDisplayPage(
            forumId = forumId,
            page = page,
            pageSize = 20,
            orderBy = orderBy,
            filter = filter,
            typeId = typeId
        ).string()
        val threadPage = ForumApiParser.parseDesktopThreadPage(html, forumId, page)
        val metadata = ForumApiParser.parseForumPageMetadata(html)
        metadata.headImageUrl?.let { imageUrl ->
            synchronized(cachedHeadImages) { cachedHeadImages[forumId] = imageUrl }
        }
        return threadPage.copy(
            forum = threadPage.forum.copy(
                headImageUrl = metadata.headImageUrl ?: threadPage.forum.headImageUrl,
                todayPostCount = metadata.todayPostCount ?: threadPage.forum.todayPostCount,
                threadCount = metadata.threadCount ?: threadPage.forum.threadCount,
                rank = metadata.rank ?: threadPage.forum.rank
            )
        )
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
        val dataSource = webPageDataSource ?: browserDataSource
            ?: throw IllegalStateException("电脑版帖子解析器不可用，请稍后重试")
        return dataSource.getPosts(threadId, page, authorId)
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

    /** 回复主题；只提交电脑版网页表单，引用回复直接使用 repquote。 */
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
                AppErrorLog.record("回复电脑版表单结果：失败：$detail")
                throw IllegalStateException(detail)
            }
            val detail = result ?: "回复失败，请稍后重试"
            AppErrorLog.record("回复电脑版表单结果：失败：$detail")
            throw IllegalStateException(detail)
        }
        AppErrorLog.record("回复电脑版表单结果：已发表")
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
        "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$threadId&mobile=no"

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

    /** 通过电脑版 findpost 重定向解析目标楼层所在页，不展示任何 WebView。 */
    suspend fun resolvePostPage(threadId: String, postId: String): Int {
        if (threadId.isBlank() || postId.isBlank()) return 1
        val url = "https://bbs.yamibo.com/forum.php?mod=redirect&goto=findpost" +
            "&ptid=$threadId&pid=$postId&mobile=no"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Cookie", YamiboSession.desktopCookie(YamiboSession.cookieFor(url)))
            .get()
            .build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            extractPostPage(response.request.url.toString())
                ?: response.header("Location")?.let(::extractPostPage)
                ?: 1
        }
    }

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        const val BANNER_CACHE_MILLIS = 30L * 60L * 1000L
        const val RATE_POPOUT_TIMEOUT_MILLIS = 2_500L
    }
}

internal fun extractPostPage(url: String): Int? =
    Regex("[?&]page=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("(?:^|/)thread-\\d+-(\\d+)(?:-|\\.html)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

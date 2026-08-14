package org.shirakawatyu.yamibo.novel.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRatingSummary
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.ForumApi
import org.shirakawatyu.yamibo.novel.parser.ForumApiParser
import org.shirakawatyu.yamibo.novel.parser.ForumPageMetadata
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import okhttp3.Request

private data class ForumThreadHtmlExtras(
    val ratingSummaries: Map<String, ForumPostRatingSummary> = emptyMap(),
    val poll: ForumPoll? = null
)

class ForumRepository(
    private val api: ForumApi = YamiboRetrofit.getInstance().create(ForumApi::class.java)
) {
    private var cachedBanners: List<ForumBanner> = emptyList()
    private var bannerFetchTimeMillis: Long = 0L
    private val cachedHeadImages = mutableMapOf<String, String?>()

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

    suspend fun getPosts(threadId: String, page: Int, authorId: String? = null): ForumPostPage =
        coroutineScope {
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
                            poll = ForumApiParser.parseForumPoll(threadHtml)
                        )
                    }.getOrDefault(ForumThreadHtmlExtras())
                } ?: ForumThreadHtmlExtras()
            }
            val postPage = postPageDeferred.await()
            val htmlExtras = htmlExtrasDeferred.await()
            if (htmlExtras.ratingSummaries.isEmpty() && htmlExtras.poll == null) {
                postPage
            } else {
                postPage.copy(
                    posts = postPage.posts.map { post ->
                        post.copy(
                            ratingSummary = htmlExtras.ratingSummaries[post.id]
                                ?: post.ratingSummary,
                            poll = htmlExtras.poll.takeIf { post.isOriginalPost }
                                ?: post.poll
                        )
                    }
                )
            }
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
    }
}

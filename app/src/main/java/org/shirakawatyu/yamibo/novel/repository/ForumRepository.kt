package org.shirakawatyu.yamibo.novel.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBanner
import org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.ForumApi
import org.shirakawatyu.yamibo.novel.parser.ForumApiParser
import org.shirakawatyu.yamibo.novel.util.YamiboPostLinkUtil
import okhttp3.Request

class ForumRepository(
    private val api: ForumApi = YamiboRetrofit.getInstance().create(ForumApi::class.java)
) {
    private var cachedBanners: List<ForumBanner> = emptyList()
    private var bannerFetchTimeMillis: Long = 0L
    private val cachedHeadImages = mutableMapOf<String, String?>()

    suspend fun getForumIndex(): ForumIndex =
        ForumApiParser.parseForumIndex(api.getForumIndex().string())

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
        val headImageDeferred = if (page == 1) {
            async { getForumHeadImage(forumId) }
        } else {
            null
        }
        val threadPage = threadPageDeferred.await()
        val headImage = headImageDeferred?.await()
        if (headImage == null) {
            threadPage
        } else {
            threadPage.copy(forum = threadPage.forum.copy(headImageUrl = headImage))
        }
    }

    private suspend fun getForumHeadImage(forumId: String): String? {
        synchronized(cachedHeadImages) {
            if (cachedHeadImages.containsKey(forumId)) return cachedHeadImages[forumId]
        }
        val imageUrl = runCatching {
            ForumApiParser.parseForumHeadImage(
                api.getForumDisplayPage(forumId = forumId).string()
            )
        }.getOrNull()
        synchronized(cachedHeadImages) {
            cachedHeadImages[forumId] = imageUrl
        }
        return imageUrl
    }

    suspend fun getPosts(threadId: String, page: Int, authorId: String? = null): ForumPostPage =
        coroutineScope {
            val postPageDeferred = async {
                ForumApiParser.parsePostPage(
                    api.getThreadPosts(threadId, page, authorId).string(),
                    page
                )
            }
            val ratingSummariesDeferred = async {
                withTimeoutOrNull(RATING_FETCH_TIMEOUT_MILLIS) {
                    runCatching {
                        ForumApiParser.parseForumPostRatingSummaries(
                            api.getThreadPage(threadId = threadId, page = page).string()
                        )
                    }.getOrDefault(emptyMap())
                }.orEmpty()
            }
            val postPage = postPageDeferred.await()
            val ratingSummaries = ratingSummariesDeferred.await()
            if (ratingSummaries.isEmpty()) {
                postPage
            } else {
                postPage.copy(
                    posts = postPage.posts.map { post ->
                        ratingSummaries[post.id]?.let { summary ->
                            post.copy(ratingSummary = summary)
                        } ?: post
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
        const val RATING_FETCH_TIMEOUT_MILLIS = 8_000L
    }
}

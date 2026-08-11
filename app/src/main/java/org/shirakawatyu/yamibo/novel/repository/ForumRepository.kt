package org.shirakawatyu.yamibo.novel.repository

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
    suspend fun getForumIndex(): ForumIndex =
        ForumApiParser.parseForumIndex(api.getForumIndex().string())

    suspend fun getThreads(forumId: String, page: Int): ForumThreadPage =
        ForumApiParser.parseThreadPage(api.getForumThreads(forumId, page).string())

    suspend fun getPosts(threadId: String, page: Int): ForumPostPage =
        ForumApiParser.parsePostPage(api.getThreadPosts(threadId, page).string(), page)

    suspend fun resolveThreadId(url: String): String? {
        val normalized = YamiboPostLinkUtil.normalizePostUrl(url) ?: return null
        YamiboPostLinkUtil.extractThreadId(normalized)?.let { return it }
        val request = Request.Builder().url(normalized).get().build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            YamiboPostLinkUtil.extractThreadId(response.request.url.toString())
                ?: response.header("Location")?.let(YamiboPostLinkUtil::extractThreadId)
        }
    }
}

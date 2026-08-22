package org.shirakawatyu.yamibo.novel.repository

import android.content.Context
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostPage
import org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadDetail
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.parser.ForumApiParser
import org.shirakawatyu.yamibo.novel.util.browser.ThreadPageExtractor
import org.shirakawatyu.yamibo.novel.util.browser.YamiboBrowserEngine
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 帖子页的浏览器驱动数据源；页面 DOM 只在 WebView 内转换为结构化 JSON。 */
class WebForumDataSource(
    context: Context
) {
    private val appContext = context.applicationContext
    private val contextRef = WeakReference(context)
    private val cachedThreads = ConcurrentHashMap<String, ForumThreadDetail>()

    suspend fun getPosts(threadId: String, page: Int, authorId: String? = null): ForumPostPage {
        val targetPage = page.coerceAtLeast(1)
        val url = buildThreadUrl(threadId, targetPage, authorId)
        val extraction = YamiboBrowserEngine.extract(
            context = contextRef.get() ?: appContext,
            url = url,
            extractorScript = ThreadPageExtractor.SCRIPT
        )
        val parsed = ForumApiParser.parseWebPostPage(extraction.payload, targetPage)
        val cached = cachedThreads[threadId]
        val resolvedThread = when {
            targetPage == 1 || cached == null -> parsed.thread
            else -> parsed.thread.copy(
                subject = cached.subject.ifBlank { parsed.thread.subject },
                author = cached.author,
                forumId = cached.forumId.ifBlank { parsed.thread.forumId },
                forumName = cached.forumName.ifBlank { parsed.thread.forumName },
                replyCount = parsed.thread.replyCount.takeIf { it > 0 } ?: cached.replyCount,
                viewCount = parsed.thread.viewCount.takeIf { it > 0 } ?: cached.viewCount,
                lastPoster = parsed.thread.lastPoster.ifBlank { cached.lastPoster }
            )
        }
        cachedThreads[threadId] = resolvedThread
        return parsed.copy(thread = resolvedThread)
    }

    private fun buildThreadUrl(threadId: String, page: Int, authorId: String?): String =
        "${RequestConfig.BASE_URL}/forum.php".toHttpUrl().newBuilder()
            .addQueryParameter("mod", "viewthread")
            .addQueryParameter("tid", threadId)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("ppp", "20")
            .apply {
                authorId?.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("authorid", it)
                }
            }
            .addQueryParameter("mobile", "no")
            .build()
            .toString()
}

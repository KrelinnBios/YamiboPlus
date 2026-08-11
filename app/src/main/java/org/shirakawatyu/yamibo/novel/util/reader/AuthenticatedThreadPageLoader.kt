package org.shirakawatyu.yamibo.novel.util.reader

import android.content.Context
import com.alibaba.fastjson2.JSON
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.NovelApi
import org.shirakawatyu.yamibo.novel.parser.ThreadHtmlParser
import org.shirakawatyu.yamibo.novel.util.YamiboSession

data class LoadedReaderThreadPage(
    val html: String,
    val maxPage: Int,
    val title: String?,
    val author: String?,
    val authorId: String,
    val section: String?
)

data class AuthenticatedHtmlPage(
    val html: String,
    val url: String,
    val cookie: String
)

object AuthenticatedThreadPageLoader {
    const val CONTENT_VERSION = 4

    suspend fun loadReaderPage(
        tid: String,
        page: Int,
        authorIdHint: String? = null,
        context: Context? = null
    ): LoadedReaderThreadPage {
        val apiSnapshot = runCatching {
            loadApiPage(tid, page, authorIdHint)
        }.getOrNull()
        val authorId = authorIdHint
            ?.takeIf(String::isNotBlank)
            ?: apiSnapshot?.authorId

        val htmlPage = runCatching {
            fetchPage(tid = tid, page = page, authorId = authorId)
        }.getOrNull()
        var parsedHtml = htmlPage?.let {
            ThreadHtmlParser.parseReaderPage(
                html = it.html,
                baseUrl = it.url,
                expectedAuthorId = authorId
            )
        }
        if (
            (parsedHtml?.combinedHtml.isNullOrBlank() || parsedHtml?.accessDeniedReason != null) &&
            context != null
        ) {
            val webViewPage = runCatching {
                AuthenticatedWebViewPageLoader.fetch(
                    context = context,
                    url = buildThreadUrl(tid, page, authorId)
                )
            }.getOrNull()
            if (webViewPage != null) {
                parsedHtml = ThreadHtmlParser.parseReaderPage(
                    html = webViewPage.html,
                    baseUrl = webViewPage.url,
                    expectedAuthorId = authorId
                )
            }
        }
        val resolvedAuthorId = authorId
            ?: parsedHtml?.authorId
            ?: throw IllegalStateException("无法获取作者ID")
        val accessDeniedReason = parsedHtml?.accessDeniedReason
        val content = parsedHtml?.combinedHtml
            ?.takeIf { it.isNotBlank() && accessDeniedReason == null }
            ?: apiSnapshot?.html
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException(
                accessDeniedReason ?: "帖子正文为空"
            )

        return LoadedReaderThreadPage(
            html = content,
            maxPage = maxOf(parsedHtml?.maxPage ?: 1, apiSnapshot?.maxPage ?: 1),
            title = parsedHtml?.title ?: apiSnapshot?.title,
            author = parsedHtml?.author ?: apiSnapshot?.author,
            authorId = resolvedAuthorId,
            section = parsedHtml?.section ?: apiSnapshot?.section
        )
    }

    fun fetchHtml(tid: String, page: Int = 1, authorId: String? = null): String {
        return fetchPage(tid = tid, page = page, authorId = authorId).html
    }

    fun fetchPage(tid: String, page: Int = 1, authorId: String? = null): AuthenticatedHtmlPage {
        val url = buildThreadUrl(tid, page, authorId)
        val cookie = YamiboSession.cookieFor(url)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", YamiboRetrofit.getPcUserAgent())
            .header("Accept", RequestConfig.ACCEPT)
            .header("Referer", "${RequestConfig.BASE_URL}/")
            .apply {
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .build()

        YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("帖子网页请求失败: HTTP ${response.code}")
            }
            var responseCursor: okhttp3.Response? = response
            while (responseCursor != null) {
                YamiboSession.storeSetCookies(
                    responseCursor.request.url.toString(),
                    responseCursor.headers("Set-Cookie")
                )
                responseCursor = responseCursor.priorResponse
            }
            val finalUrl = response.request.url.toString()
            return AuthenticatedHtmlPage(
                html = response.body?.string().orEmpty(),
                url = finalUrl,
                cookie = YamiboSession.cookieFor(finalUrl).ifBlank { cookie }
            )
        }
    }

    fun buildThreadUrl(tid: String, page: Int, authorId: String?): String {
        return "${RequestConfig.BASE_URL}/forum.php".toHttpUrl().newBuilder()
            .addQueryParameter("mod", "viewthread")
            .addQueryParameter("tid", tid)
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .apply {
                authorId?.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("authorid", it)
                }
            }
            .addQueryParameter("mobile", "no")
            .build()
            .toString()
    }

    private suspend fun loadApiPage(
        tid: String,
        page: Int,
        authorIdHint: String?
    ): ApiReaderPage {
        val api = YamiboRetrofit.getInstance().create(NovelApi::class.java)
        val resolvedAuthorId = authorIdHint?.takeIf(String::isNotBlank) ?: run {
            val metadata = JSON.parseObject(api.getThreadFirstPage(tid = tid, page = 1).string())
            metadata.getJSONObject("Variables")
                ?.getJSONObject("thread")
                ?.getString("authorid")
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("API 未返回作者ID")
        }
        val json = JSON.parseObject(
            api.getThreadPageByAuthor(
                tid = tid,
                page = page.coerceAtLeast(1),
                authorid = resolvedAuthorId
            ).string()
        )
        val variables = json.getJSONObject("Variables")
            ?: throw IllegalStateException("API 未返回 Variables")
        val thread = variables.getJSONObject("thread")
        val ppp = variables.getString("ppp")?.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val totalReplies = thread?.getString("replies")?.toIntOrNull() ?: 0
        val maxPage = ((totalReplies + 1 + ppp - 1) / ppp).coerceAtLeast(1)
        val postList = variables.getJSONArray("postlist")
            ?: throw IllegalStateException("API 未返回 postlist")
        val combinedHtml = (0 until postList.size).joinToString("") { index ->
            val message = postList.getJSONObject(index)?.getString("message").orEmpty()
            """<div class="message">$message</div>"""
        }

        return ApiReaderPage(
            html = combinedHtml,
            maxPage = maxPage,
            title = thread?.getString("subject"),
            author = thread?.getString("author"),
            authorId = resolvedAuthorId,
            section = variables.getJSONObject("forum")?.getString("name")
        )
    }

    private data class ApiReaderPage(
        val html: String,
        val maxPage: Int,
        val title: String?,
        val author: String?,
        val authorId: String,
        val section: String?
    )
}

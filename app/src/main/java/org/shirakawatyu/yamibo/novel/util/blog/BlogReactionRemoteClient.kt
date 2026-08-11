package org.shirakawatyu.yamibo.novel.util.blog

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.util.ForumRedirectCookieUtil
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import java.io.IOException

internal data class BlogReactionOption(
    val clickId: String,
    val label: String,
    val count: Int,
    val actionUrl: String
)

internal data class BlogReactionSnapshot(
    val options: List<BlogReactionOption>,
    val totalCount: Int
)

internal data class BlogReactionUpdate(
    val snapshot: BlogReactionSnapshot,
    val message: String
)

internal object BlogReactionRemoteClient {
    private const val FORUM_ROOT = "https://bbs.yamibo.com/"
    private val numericId = Regex("[1-9]\\d*")
    private val fallbackLabels = mapOf(
        "1" to "路过",
        "2" to "雷人",
        "3" to "握手",
        "4" to "鲜花",
        "5" to "鸡蛋"
    )

    fun fetchSnapshot(ownerUid: String, blogId: String): BlogReactionSnapshot {
        val blogUrl = desktopBlogUrl(ownerUid, blogId)
        return parseSnapshot(execute(Request.Builder().url(blogUrl).get()), blogUrl.toString())
            ?: throw IOException("手机版表态信息暂时无法加载")
    }

    fun addReaction(ownerUid: String, blogId: String, clickId: String): BlogReactionUpdate {
        val normalizedClickId = clickId.takeIf { it.matches(numericId) }
            ?: throw IOException("无效的表态类型")
        val blogUrl = desktopBlogUrl(ownerUid, blogId)
        val before = fetchSnapshot(ownerUid, blogId)
        val option = before.options.firstOrNull { it.clickId == normalizedClickId }
            ?: throw IOException("该表态选项已失效，请刷新后重试")
        val cookie = YamiboSession.cookieFor(FORUM_ROOT)
        if (!Regex("(?:^|;\\s*)[^=;]*_auth=").containsMatchIn(cookie)) {
            throw IOException("请先登录后再表态")
        }

        val actionUrl = validatedActionUrl(option.actionUrl, blogId, normalizedClickId)
        val ajaxUrl = actionUrl.newBuilder()
            .apply {
                if (actionUrl.queryParameter("inajax").isNullOrBlank()) {
                    addQueryParameter("inajax", "1")
                }
                if (actionUrl.queryParameter("ajaxtarget").isNullOrBlank()) {
                    addQueryParameter("ajaxtarget", "fwin_content_clickhandle")
                }
            }
            .build()
        val responseHtml = execute(
            Request.Builder()
                .url(ajaxUrl)
                .header("Referer", blogUrl.toString())
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
        )
        val after = fetchSnapshot(ownerUid, blogId)
        val changed = reactionState(before) != reactionState(after)
        val responseMessage = extractResponseMessage(responseHtml)
        val message = when {
            changed -> responseMessage.ifBlank { "表态成功" }
            responseMessage.isNotBlank() -> responseMessage
            else -> "表态未更新，可能已经表态过"
        }
        return BlogReactionUpdate(after, message)
    }

    private fun desktopBlogUrl(ownerUid: String, blogId: String): HttpUrl {
        val safeUid = ownerUid.takeIf { it.matches(numericId) }
            ?: throw IOException("无效的日志作者")
        val safeBlogId = blogId.takeIf { it.matches(numericId) }
            ?: throw IOException("无效的日志编号")
        return "$FORUM_ROOT/home.php".toHttpUrl().newBuilder()
            .addQueryParameter("mod", "space")
            .addQueryParameter("uid", safeUid)
            .addQueryParameter("do", "blog")
            .addQueryParameter("id", safeBlogId)
            .addQueryParameter("mobile", "no")
            .build()
    }

    private fun validatedActionUrl(
        rawUrl: String,
        blogId: String,
        clickId: String
    ): HttpUrl {
        val url = rawUrl.toHttpUrl()
        val valid = url.host.equals("bbs.yamibo.com", ignoreCase = true) &&
                url.encodedPath == "/home.php" &&
                url.queryParameter("mod") == "spacecp" &&
                url.queryParameter("ac") == "click" &&
                url.queryParameter("op") == "add" &&
                url.queryParameter("idtype") == "blogid" &&
                url.queryParameter("id") == blogId &&
                url.queryParameter("clickid") == clickId &&
                !url.queryParameter("hash").isNullOrBlank()
        if (!valid) throw IOException("表态链接校验失败，请刷新后重试")
        return url
    }

    private fun execute(builder: Request.Builder): String {
        val cookie = YamiboSession.cookieFor(FORUM_ROOT)
        var redirectCookie = YamiboSession.desktopCookie(cookie)
        var request = builder
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
            .apply {
                if (redirectCookie.isNotBlank()) {
                    header("Cookie", redirectCookie)
                }
            }
            .build()
        val client = YamiboRetrofit.okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var hops = 0
        while (true) {
            val response = client.newCall(
                request.newBuilder()
                    .apply {
                        if (redirectCookie.isNotBlank()) header("Cookie", redirectCookie)
                    }
                    .build()
            ).execute()
            redirectCookie = ForumRedirectCookieUtil.merge(
                redirectCookie,
                response.request.url,
                response.headers("Set-Cookie")
            )
            YamiboSession.storeSetCookies(
                response.request.url.toString(),
                response.headers("Set-Cookie").filterNot { header ->
                    isMobileCookieName(header.substringBefore('=').trim())
                }
            )
            if (response.isRedirect) {
                val location = response.header("Location")
                val resolved = location?.let { response.request.url.resolve(it) }
                response.close()
                if (resolved == null || hops >= 8) {
                    throw IOException("表态请求重定向失败")
                }
                request = request.newBuilder().url(resolved).get().build()
                hops++
                continue
            }
            if (!response.isSuccessful) {
                val responseCode = response.code
                response.close()
                throw IOException("表态请求失败：HTTP $responseCode")
            }
            return response.use { it.body?.string().orEmpty() }
        }
    }

    private fun isMobileCookieName(name: String): Boolean =
        name.equals("mobile", ignoreCase = true) ||
                name.endsWith("_mobile", ignoreCase = true)

    internal fun parseSnapshot(html: String, baseUrl: String): BlogReactionSnapshot? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html, baseUrl)
        val clickDiv = document.selectFirst("#click_div") ?: return null
        val options = clickDiv.select(".atd a[id^=click_]").mapNotNull { link ->
            val actionUrl = link.absUrl("href").ifBlank { link.attr("href") }
            val parsedAction = runCatching { actionUrl.toHttpUrl() }.getOrNull()
                ?: return@mapNotNull null
            val clickId = parsedAction.queryParameter("clickid")
                ?.takeIf { it.matches(numericId) }
                ?: return@mapNotNull null
            val label = link.ownText().trim().ifBlank {
                fallbackLabels[clickId].orEmpty()
            }
            if (label.isBlank()) return@mapNotNull null
            BlogReactionOption(
                clickId = clickId,
                label = label,
                count = link.selectFirst(".atdc em")?.text()?.trim()?.toIntOrNull() ?: 0,
                actionUrl = actionUrl
            )
        }.distinctBy { it.clickId }
        if (options.isEmpty()) return null

        val totalCount = Regex("(\\d+)\\s*人")
            .find(clickDiv.selectFirst("h3")?.text().orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: options.sumOf { it.count }
        return BlogReactionSnapshot(options, totalCount)
    }

    internal fun extractResponseMessage(html: String): String {
        if (html.isBlank()) return ""
        val document = Jsoup.parse(html)
        val elementMessage = document.selectFirst(
            "#messagetext, .showmessage, .alert_error, .alert_info, .alert_right"
        )?.text()?.trim().orEmpty()
        if (elementMessage.isNotBlank()) return elementMessage

        val decoded = Parser.unescapeEntities(html, false)
        return Regex("""showDialog\(\s*['"]([^'"]+)['"]""")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\'", "'")
            ?.replace("\\\"", "\"")
            ?.trim()
            .orEmpty()
    }

    private fun reactionState(snapshot: BlogReactionSnapshot): List<Pair<String, Int>> =
        snapshot.options.map { it.clickId to it.count }
}

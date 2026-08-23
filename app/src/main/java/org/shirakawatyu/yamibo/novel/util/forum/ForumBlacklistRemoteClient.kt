package org.shirakawatyu.yamibo.novel.util.forum

import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import java.io.IOException

internal data class ForumBlacklistSnapshot(
    val users: List<ForumBlockedItem>,
    val formHash: String,
    val currentUid: String = ""
)

internal object ForumBlacklistRemoteClient {
    private const val FORUM_ROOT = "https://bbs.yamibo.com/"

    private val blacklistUrl = "${FORUM_ROOT}home.php"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("mod", "space")
        .addQueryParameter("do", "friend")
        .addQueryParameter("view", "blacklist")
        .addQueryParameter("quickforward", "1")
        .addQueryParameter("start", "")
        .addQueryParameter("mobile", "no")
        .build()

    private val blacklistActionUrl = "${FORUM_ROOT}home.php"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("mod", "spacecp")
        .addQueryParameter("ac", "friend")
        .addQueryParameter("op", "blacklist")
        .addQueryParameter("start", "")
        .addQueryParameter("mobile", "no")
        .build()

    fun fetchSnapshot(): ForumBlacklistSnapshot? {
        val html = execute(
            Request.Builder()
                .url(blacklistUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .get()
        )
        return parseSnapshot(html)
    }

    fun addUser(username: String): ForumBlacklistSnapshot? {
        val normalizedName = username.trim()
        if (normalizedName.isBlank()) return null

        val before = fetchSnapshot() ?: return null
        if (before.users.any { it.authorName.equals(normalizedName, ignoreCase = true) }) {
            return before
        }

        val body = FormBody.Builder()
            .add("username", normalizedName)
            .add("blacklistsubmit", "true")
            .add("blacklistsubmit_btn", "true")
            .add("formhash", before.formHash)
            .build()
        execute(
            Request.Builder()
                .url(blacklistActionUrl)
                .post(body)
        )
        return fetchSnapshot()
    }

    fun removeUser(uid: String): ForumBlacklistSnapshot? {
        val normalizedUid = uid.trim().takeIf { it.matches(Regex("[1-9]\\d*")) } ?: return null
        val url = blacklistActionUrl.newBuilder()
            .addQueryParameter("subop", "delete")
            .addQueryParameter("uid", normalizedUid)
            .build()
        execute(Request.Builder().url(url).get())
        return fetchSnapshot()
    }

    private fun execute(builder: Request.Builder): String {
        val cookie = YamiboSession.cookieFor(FORUM_ROOT)
        if (!cookie.contains("EeqY_2132_auth=")) {
            throw IOException("Forum account is not logged in")
        }

        val request = builder
            // 黑名单表单只存在于电脑版模板。App 的论坛会话通常带 mobile=2，
            // 必须仅在本次原生请求中改成 mobile=no，不能修改 WebView 的全局会话。
            .header("Cookie", YamiboSession.desktopCookie(cookie))
            .header("Referer", blacklistUrl.toString())
            .build()
        return YamiboRetrofit.okHttpClient.newCall(request).execute().use { response ->
            YamiboSession.storeSetCookies(
                response.request.url.toString(),
                response.headers("Set-Cookie").filterNot { header ->
                    isMobileCookieName(header.substringBefore('=').trim())
                }
            )
            if (!response.isSuccessful) {
                throw IOException("Forum blacklist request failed: HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun isMobileCookieName(name: String): Boolean =
        name.equals("mobile", ignoreCase = true) ||
                name.endsWith("_mobile", ignoreCase = true)

    internal fun parseSnapshot(html: String): ForumBlacklistSnapshot? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html, blacklistUrl.toString())
        val form = document.selectFirst("form[name=blackform]") ?: return null
        val formHash = form.selectFirst("input[name=formhash]")
            ?.attr("value")
            ?.trim()
            .orEmpty()
        if (formHash.isBlank()) return null

        val users = document.select("#friend_ul li").mapNotNull { item ->
            val profileLinks = item.select(
                "a[href*=\"space-uid-\"], a[href*=\"mod=space\"][href*=\"uid=\"]"
            ).filter { link -> isUserProfileHref(link.attr("href")) }
            val uid = profileLinks.asSequence()
                .mapNotNull { link -> extractUid(link.attr("href")) }
                .firstOrNull()
                ?: item.id()
                    .removePrefix("friend_")
                    .removeSuffix("_li")
                    .takeIf { it.matches(Regex("[1-9]\\d*")) }
                ?: return@mapNotNull null
            val username = profileLinks.asSequence()
                .map { link -> link.text().trim() }
                .firstOrNull { name -> name.isNotBlank() && !isActionLabel(name) }
                ?: profileLinks.asSequence()
                    .map { link -> link.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
                    .firstOrNull { name -> name.isNotBlank() && !isActionLabel(name) }
                ?: "UID $uid"
            ForumBlockedItem(
                type = ForumBlockedItem.TYPE_USER,
                id = uid,
                title = username,
                authorUid = uid,
                authorName = username
            )
        }.distinctBy { it.id }

        val currentUid = Regex("""discuz_uid\s*=\s*['"]([1-9]\d*)['"]""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        return ForumBlacklistSnapshot(
            users = users,
            formHash = formHash,
            currentUid = currentUid
        )
    }

    private fun isUserProfileHref(href: String): Boolean =
        Regex("space-uid-[1-9]\\d*", RegexOption.IGNORE_CASE).containsMatchIn(href) ||
            (
                Regex("[?&]mod=space(?:&|$)", RegexOption.IGNORE_CASE).containsMatchIn(href) &&
                    extractUid(href) != null
            )

    private fun isActionLabel(value: String): Boolean =
        value in setOf("黑名单除名", "移出黑名单", "解除黑名单", "删除")

    private fun extractUid(href: String): String? {
        return Regex("space-uid-([1-9]\\d*)", RegexOption.IGNORE_CASE)
            .find(href)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("[?&]uid=([1-9]\\d*)", RegexOption.IGNORE_CASE)
                .find(href)
                ?.groupValues
                ?.getOrNull(1)
    }
}

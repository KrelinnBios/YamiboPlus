package org.shirakawatyu.yamibo.novel.util.network

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

private const val WAF_COOKIE_NAME = "abymg_id"

/**
 * 只保存百度图片 WAF 下发的 abymg_id，不接管论坛登录 Cookie。
 *
 * WAF 首次请求可能返回同地址 302，并通过 Set-Cookie 下发令牌。网络拦截器会在 OkHttp
 * 自动跟随重定向前拿到该响应；下一跳再把令牌合并进已有 Cookie 请求头，避免服务器重复 302。
 */
internal class WafCookieHandshakeInterceptor(
    private val store: WafCookieStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val wafCookie = store.cookieHeaderFor(original.url)
        val request = if (wafCookie == null) {
            original
        } else {
            original.newBuilder()
                .header(
                    "Cookie",
                    WafCookieStore.mergeCookieHeader(original.header("Cookie"), wafCookie)
                )
                .build()
        }

        return chain.proceed(request).also { response ->
            store.capture(response.request.url, response.headers("Set-Cookie"))
        }
    }
}

/** 进程内、线程安全的单 Cookie 存储；有效期、域名、路径及 Secure 匹配均交给 OkHttp。 */
internal class WafCookieStore {
    @Volatile
    private var cookie: Cookie? = null

    fun capture(responseUrl: HttpUrl, setCookieHeaders: List<String>) {
        if (!isYamiboHost(responseUrl.host)) return

        setCookieHeaders.forEach { header ->
            val parsed = Cookie.parse(responseUrl, header) ?: return@forEach
            if (parsed.name != WAF_COOKIE_NAME) return@forEach
            cookie = parsed.takeIf { it.value.isNotEmpty() && it.expiresAt > System.currentTimeMillis() }
        }
    }

    fun cookieHeaderFor(requestUrl: HttpUrl, nowMillis: Long = System.currentTimeMillis()): String? {
        if (!isYamiboHost(requestUrl.host)) return null
        val current = cookie ?: return null
        if (current.expiresAt <= nowMillis) {
            cookie = null
            return null
        }
        if (!matches(current, requestUrl)) return null
        return "${current.name}=${current.value}"
    }

    private fun matches(cookie: Cookie, url: HttpUrl): Boolean {
        if (cookie.secure && url.scheme != "https") return false

        val domainMatches = if (cookie.hostOnly) {
            url.host == cookie.domain
        } else {
            url.host == cookie.domain || url.host.endsWith(".${cookie.domain}")
        }
        if (!domainMatches) return false

        val requestPath = url.encodedPath
        return requestPath == cookie.path ||
                (requestPath.startsWith(cookie.path) &&
                        (cookie.path.endsWith('/') || requestPath.getOrNull(cookie.path.length) == '/'))
    }

    companion object {
        internal fun mergeCookieHeader(existingHeader: String?, wafCookie: String): String {
            val existingCookies = existingHeader.orEmpty()
                .split(';')
                .map(String::trim)
                .filter { part ->
                    val separator = part.indexOf('=')
                    separator > 0 && part.substring(0, separator) != WAF_COOKIE_NAME
                }
            return (existingCookies + wafCookie).joinToString("; ")
        }

        private fun isYamiboHost(host: String): Boolean =
            host == "yamibo.com" || host.endsWith(".yamibo.com")
    }
}

package org.shirakawatyu.yamibo.novel.util.network

import okhttp3.HttpUrl
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie

class WafCookieStore {
    companion object {
        private const val WAF_COOKIE_NAME = "abymg_id"
        private val WAF_COOKIE_PREFIXES = listOf("abymg_", "nox_")

        fun mergeCookieHeader(existing: String, wafCookie: String): String {
            val existingCookies = existing.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && !isWafCookieName(it.substringBefore("=").trim()) }

            return (existingCookies + wafCookie).joinToString("; ")
        }

        internal fun isWafCookieName(name: String): Boolean {
            return name == WAF_COOKIE_NAME || WAF_COOKIE_PREFIXES.any { name.startsWith(it) }
        }
    }

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    fun capture(url: HttpUrl, setCookieHeaders: List<String>) {
        for (header in setCookieHeaders) {
            try {
                val cookies = HttpCookie.parse(header).filter { isWafCookieName(it.name) }
                for (cookie in cookies) {
                    cookieManager.cookieStore.add(url.toUri(), cookie)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 从 WebView CookieManager 同步所有 WAF Cookie。
     */
    fun captureFromCookieString(url: HttpUrl, cookieString: String) {
        if (cookieString.isBlank()) return
        val headers = cookieString.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() && isWafCookieName(it.substringBefore("=").trim()) }
            .map { "${it.substringBefore("=")}=${it.substringAfter("=", "")}; Domain=.yamibo.com; Path=/" }
        capture(url, headers)
    }

    fun cookieHeaderFor(url: HttpUrl): String? {
        val cookies = cookieManager.cookieStore.get(url.toUri())
            .filter { isWafCookieName(it.name) && !it.hasExpired() }

        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
}

package org.shirakawatyu.yamibo.novel.util

import android.webkit.CookieManager
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData

object YamiboSession {
    private const val FORUM_ROOT = "https://bbs.yamibo.com/"
    const val AUTHENTICATION_COOKIE_NAME = "EeqY_2132_auth"
    private val INVALID_AUTHENTICATION_COOKIE_VALUES =
        setOf("delete", "deleted", "expired", "nil", "none", "null")

    fun cookieFor(url: String): String {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        val cookieHeaders = buildList {
            if (cookieManager != null) {
                add(runCatching { cookieManager.getCookie(url) }.getOrNull().orEmpty())
                add(runCatching { cookieManager.getCookie(FORUM_ROOT) }.getOrNull().orEmpty())
                add(runCatching { cookieManager.getCookie(RequestConfig.BASE_URL) }.getOrNull().orEmpty())
            }
            add(GlobalData.currentCookie)
        }
        return mergeCookieHeaders(cookieHeaders)
    }

    /**
     * 论坛是否已登录只由有效的认证 Cookie 决定，不能用个人资料接口是否可解析来推断。
     */
    fun hasAuthenticationCookie(cookieHeader: String): Boolean =
        authenticationCookieValue(cookieHeader) != null

    fun authenticationCookieValue(cookieHeader: String): String? =
        cookieHeader.split(';').firstNotNullOfOrNull { rawCookie ->
            val separator = rawCookie.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            val name = rawCookie.substring(0, separator).trim()
            if (name != AUTHENTICATION_COOKIE_NAME) return@firstNotNullOfOrNull null
            val value = rawCookie.substring(separator + 1).trim()
                .removeSurrounding("\"")
                .trim()
            value.takeIf {
                it.isNotEmpty() && it.lowercase() !in INVALID_AUTHENTICATION_COOKIE_VALUES
            }
        }

    internal fun desktopCookie(cookie: String): String {
        var hasMobileCookie = false
        val parts = cookie.split(';').mapNotNull { rawPart ->
            val part = rawPart.trim()
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = part.substring(0, separator).trim()
            if (isMobileCookieName(name)) {
                hasMobileCookie = true
                "$name=no"
            } else {
                part
            }
        }.toMutableList()

        if (!hasMobileCookie) {
            val authCookieName = parts.asSequence()
                .map { it.substringBefore('=').trim() }
                .firstOrNull { it.endsWith("_auth", ignoreCase = true) }
            val mobileCookieName = authCookieName
                ?.removeSuffix("auth")
                ?.plus("mobile")
                ?: "mobile"
            parts += "$mobileCookieName=no"
        }
        return parts.joinToString("; ")
    }

    /** WAF 挑战后重放请求时，刷新 Cookie 的同时保留原请求的电脑版模板模式。 */
    internal fun cookieForRequestUserAgent(cookie: String, userAgent: String): String =
        if (isDesktopUserAgent(userAgent)) desktopCookie(cookie) else cookie

    fun syncToWebView(url: String, cookie: String = cookieFor(url)) {
        if (cookie.isBlank()) return
        runCatching {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setCookie(FORUM_ROOT, cookie)
                setCookie(url, cookie)
                flush()
            }
        }
    }

    /** 退出登录时清理本应用 WebView 的会话 Cookie，避免常驻页面把旧登录态重新带回请求。 */
    fun clearWebViewSession() {
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }

    fun storeSetCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        runCatching {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setCookieHeaders.forEach { header ->
                    setCookie(url, header)
                    setCookie(FORUM_ROOT, header)
                }
                flush()
            }
        }
    }

    internal fun mergeCookieHeaders(headers: List<String>): String {
        val cookies = linkedMapOf<String, String>()
        headers.forEach { header ->
            header.split(';').forEach cookieLoop@{ rawCookie ->
                val cookie = rawCookie.trim()
                val separator = cookie.indexOf('=')
                if (separator <= 0) return@cookieLoop
                val name = cookie.substring(0, separator).trim()
                if (name.isNotBlank() && name !in cookies) {
                    cookies[name] = cookie
                }
            }
        }
        return cookies.values.joinToString("; ")
    }

    private fun isMobileCookieName(name: String): Boolean =
        name.equals("mobile", ignoreCase = true) ||
                name.endsWith("_mobile", ignoreCase = true)

    private fun isDesktopUserAgent(userAgent: String): Boolean =
        userAgent.contains("Windows NT", ignoreCase = true) ||
                userAgent.contains("Macintosh", ignoreCase = true)
}

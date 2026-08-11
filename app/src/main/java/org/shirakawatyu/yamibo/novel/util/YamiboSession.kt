package org.shirakawatyu.yamibo.novel.util

import android.webkit.CookieManager
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData

object YamiboSession {
    private const val FORUM_ROOT = "https://bbs.yamibo.com/"

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
}

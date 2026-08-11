package org.shirakawatyu.yamibo.novel.util

import okhttp3.Cookie
import okhttp3.HttpUrl

internal object ForumRedirectCookieUtil {
    fun merge(
        currentHeader: String?,
        responseUrl: HttpUrl,
        setCookieHeaders: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val cookies = linkedMapOf<String, String>()
        currentHeader.orEmpty().split(';').forEach { rawCookie ->
            val cookie = rawCookie.trim()
            val separator = cookie.indexOf('=')
            if (separator <= 0) return@forEach
            val name = cookie.substring(0, separator).trim()
            if (name.isNotBlank()) cookies[name] = cookie
        }

        setCookieHeaders.forEach { header ->
            val cookie = Cookie.parse(responseUrl, header) ?: return@forEach
            if (cookie.value.isEmpty() || cookie.expiresAt <= nowMillis) {
                cookies.remove(cookie.name)
            } else {
                cookies[cookie.name] = "${cookie.name}=${cookie.value}"
            }
        }
        return cookies.values.joinToString("; ")
    }
}

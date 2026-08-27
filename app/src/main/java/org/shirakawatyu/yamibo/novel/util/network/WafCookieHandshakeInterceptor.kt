package org.shirakawatyu.yamibo.novel.util.network

import okhttp3.Interceptor
import okhttp3.Response

class WafCookieHandshakeInterceptor(
    private val wafCookieStore: WafCookieStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val isYamiboHost = url.host == "yamibo.com" || url.host.endsWith(".yamibo.com")

        val modifiedRequest = if (isYamiboHost) {
            val wafCookie = wafCookieStore.cookieHeaderFor(url)
            if (wafCookie != null) {
                val existingCookie = request.header("Cookie").orEmpty()
                val mergedCookie = WafCookieStore.mergeCookieHeader(existingCookie, wafCookie)
                request.newBuilder()
                    .header("Cookie", mergedCookie)
                    .build()
            } else {
                request
            }
        } else {
            request
        }

        val response = chain.proceed(modifiedRequest)
        if (isYamiboHost) {
            wafCookieStore.capture(url, response.headers("Set-Cookie"))
        }
        return response
    }
}

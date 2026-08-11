package org.shirakawatyu.yamibo.novel.util

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import java.util.concurrent.atomic.AtomicLong
import androidx.core.net.toUri

object StaticAssetProxy {

    val hitCount = AtomicLong()
    val successCount = AtomicLong()
    val mimeRejectCount = AtomicLong()


    private val SAFE_STATIC_EXTENSIONS = setOf(
        "js", "css", "woff", "woff2", "ttf", "eot", "svg", "ico"
    )

    // 头像是公开静态图片（/uc_server/data/avatar/.../xx_avatar_*.jpg|svg），无鉴权。
    private val SAFE_AVATAR_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "svg", "webp"
    )

    private val SAFE_STATIC_HOSTS = setOf(
        "bbs.yamibo.com",
        "m.yamibo.com",
        "www.yamibo.com",
        "yamibo.com"
    )

    private val DANGEROUS_STATIC_QUERY_KEYS = listOf(
        "auth",
        "token",
        "sid",
        "session",
        "formhash",
        "password",
        "passwd",
        "seccode"
    )

    fun shouldProxySafeDiscuzStaticAsset(request: WebResourceRequest?): Boolean {
        if (request == null) return false
        if (request.isForMainFrame) return false
        if (request.method != "GET") return false

        val uri = request.url ?: return false

        if (uri.scheme != "https") return false

        val host = uri.host.orEmpty().lowercase()
        if (host !in SAFE_STATIC_HOSTS) return false

        val path = uri.path.orEmpty().lowercase()
        if (path.isBlank()) return false

        val query = uri.encodedQuery.orEmpty()
        val queryLower = query.lowercase()

        // 头像：公开静态图片，原本被各 WebView 页排除而走 WebView 原生网络（无 DNS 优化/
        // 连接池），加载慢或加载不出。这里放行，改走优化过的 okHttpClient。
        if (path.startsWith("/uc_server/data/avatar/")) {
            val avatarExt = path.substringAfterLast('.', missingDelimiterValue = "")
            if (avatarExt !in SAFE_AVATAR_EXTENSIONS) return false
            if (DANGEROUS_STATIC_QUERY_KEYS.any { queryLower.contains(it) }) return false
            return true
        }

        val ext = path.substringAfterLast('.', missingDelimiterValue = "")
        if (ext !in SAFE_STATIC_EXTENSIONS) return false

        val isSafeStaticPath =
            path.startsWith("/static/") ||
                    path.startsWith("/template/") ||
                    path.startsWith("/data/cache/") ||
                    path.startsWith("/source/plugin/")
        if (!isSafeStaticPath) return false

        if (DANGEROUS_STATIC_QUERY_KEYS.any { queryLower.contains(it) }) {
            return false
        }

        if (ext == "js" || ext == "css") {
            if (query.isBlank()) {
                return false
            }
        }

        return true
    }

    fun isExpectedStaticMime(url: String, mimeType: String?): Boolean {
        val path = runCatching {
            url.toUri().path.orEmpty().lowercase()
        }.getOrDefault("")
        val mime = mimeType.orEmpty().lowercase().substringBefore(";").trim()

        return when {
            path.endsWith(".css") ->
                mime == "text/css"

            path.endsWith(".js") ->
                mime == "application/javascript" ||
                        mime == "text/javascript" ||
                        mime == "application/x-javascript" ||
                        mime == "text/plain" ||
                        mime == "application/octet-stream" ||
                        mime.contains("javascript") ||
                        mime.contains("ecmascript")

            path.endsWith(".woff") ->
                mime == "font/woff" ||
                        mime == "application/font-woff" ||
                        mime == "application/x-font-woff" ||
                        mime == "application/octet-stream"

            path.endsWith(".woff2") ->
                mime == "font/woff2" ||
                        mime == "application/font-woff2" ||
                        mime == "application/octet-stream"

            path.endsWith(".ttf") ->
                mime == "font/ttf" ||
                        mime == "application/x-font-ttf" ||
                        mime == "application/octet-stream"

            path.endsWith(".eot") ->
                mime == "application/vnd.ms-fontobject" ||
                        mime == "application/octet-stream"

            path.endsWith(".svg") ->
                mime == "image/svg+xml" ||
                        mime == "text/xml" ||
                        mime == "application/xml"

            path.endsWith(".ico") ->
                mime == "image/x-icon" ||
                        mime == "image/vnd.microsoft.icon" ||
                        mime == "image/png" ||
                        mime == "application/octet-stream"

            path.endsWith(".jpg") || path.endsWith(".jpeg") ->
                mime == "image/jpeg" || mime == "application/octet-stream"

            path.endsWith(".png") ->
                mime == "image/png" || mime == "application/octet-stream"

            path.endsWith(".gif") ->
                mime == "image/gif" || mime == "application/octet-stream"

            path.endsWith(".webp") ->
                mime == "image/webp" || mime == "application/octet-stream"

            else -> false
        }
    }

    fun closeWebResourceResponseQuietly(response: WebResourceResponse?) {
        try {
            response?.data?.close()
        } catch (_: Throwable) {
        }
    }

    fun tryProxySafeStaticAsset(request: WebResourceRequest?): WebResourceResponse? {
        if (!shouldProxySafeDiscuzStaticAsset(request)) return null

        hitCount.incrementAndGet()

        val safeRequest = request ?: return null
        val response = YamiboRetrofit.proxyWebViewResource(safeRequest) ?: return null

        val url = safeRequest.url.toString()
        if (!isExpectedStaticMime(url, response.mimeType)) {
            mimeRejectCount.incrementAndGet()
            closeWebResourceResponseQuietly(response)
            return null
        }

        successCount.incrementAndGet()
        return response
    }

}

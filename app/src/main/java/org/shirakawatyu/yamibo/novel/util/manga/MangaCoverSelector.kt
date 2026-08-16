package org.shirakawatyu.yamibo.novel.util.manga

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MangaCoverSelector {
    private const val BASE_URL = "https://bbs.yamibo.com/"
    private val imageAttributes = listOf(
        "zoomfile",
        "file",
        "zsrc",
        "data-src",
        "data-original",
        "data-lazy-src",
        "src"
    )
    private val imageExtensionRegex = Regex("\\.(?:jpg|jpeg|png|webp|gif|bmp)(?:[?#].*)?$", RegexOption.IGNORE_CASE)
    private val ignoredImageParts = listOf(
        "smiley",
        "smilie",
        "static/image",
        "template/",
        "/common/",
        "/block/",
        "avatar"
    )

    fun firstCoverUrl(
        messages: Iterable<String>,
        attachmentUrls: Iterable<String> = emptyList()
    ): String? {
        messages.forEach { message ->
            val document = Jsoup.parse(message, BASE_URL)
            document.select("img").forEach imageLoop@{ image ->
                val url = imageUrl(image) ?: return@imageLoop
                if (isCoverCandidate(url, image)) return url
            }
        }

        return attachmentUrls.firstOrNull { url ->
            isCoverCandidate(url, image = null)
        }
    }

    fun attachmentImageUrls(raw: Any?): List<String> {
        val attachments = when (raw) {
            is JSONArray -> (0 until raw.size).mapNotNull(raw::getJSONObject)
            is JSONObject -> raw.values.mapNotNull { it as? JSONObject }
            else -> emptyList()
        }
        return attachments.mapNotNull { attachment ->
            attachmentImageUrl(
                urlPrefix = attachment.getString("url"),
                attachmentPath = attachment.getString("attachment"),
                attachmentId = attachment.getString("aid"),
                fileName = attachment.getString("filename"),
                isImage = attachment.getIntValue("isimage") != 0
            )
        }
    }

    fun attachmentImageUrl(
        urlPrefix: String?,
        attachmentPath: String?,
        attachmentId: String? = null,
        fileName: String? = null,
        isImage: Boolean = false
    ): String? {
        val prefix = urlPrefix?.trim().orEmpty()
        val path = attachmentPath?.trim().orEmpty()
        val raw = when {
            path.startsWith("http://", ignoreCase = true) ||
                path.startsWith("https://", ignoreCase = true) ||
                path.startsWith("//") -> path
            prefix.isNotBlank() && path.isNotBlank() ->
                prefix.trimEnd('/') + "/" + path.trimStart('/')
            path.isNotBlank() -> path
            prefix.isNotBlank() -> prefix
            !attachmentId.isNullOrBlank() ->
                "forum.php?mod=attachment&aid=${attachmentId.trim()}"
            else -> return null
        }
        val url = normalizeImageUrl(raw) ?: return null
        val hasImageExtension = sequenceOf(url, fileName.orEmpty())
            .any { imageExtensionRegex.containsMatchIn(it.substringBefore('?')) }
        return url.takeIf { isImage || hasImageExtension }
    }

    private fun imageUrl(image: Element): String? {
        val attribute = imageAttributes
            .firstOrNull { image.hasAttr(it) && image.attr(it).isNotBlank() }
            ?: return null
        val raw = image.attr(attribute).trim()
        val resolved = image.absUrl(attribute).ifBlank { raw }
        return normalizeImageUrl(resolved)
    }

    private fun normalizeImageUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (value.startsWith("data:") || value.startsWith("blob:")) return null
        if (value.startsWith("//")) return "https:$value"

        val fixedDataHost = when {
            value.startsWith("http://data/", ignoreCase = true) ->
                BASE_URL.trimEnd('/') + "/data/" + value.replaceFirst(Regex("^https?://data/", RegexOption.IGNORE_CASE), "")
            value.startsWith("https://data/", ignoreCase = true) ->
                BASE_URL.trimEnd('/') + "/data/" + value.replaceFirst(Regex("^https?://data/", RegexOption.IGNORE_CASE), "")
            else -> value
        }

        return if (fixedDataHost.startsWith("http://") || fixedDataHost.startsWith("https://")) {
            fixedDataHost
        } else {
            BASE_URL.trimEnd('/') + "/" + fixedDataHost.trimStart('/')
        }
    }

    private fun isCoverCandidate(url: String, image: Element?): Boolean {
        if (image?.hasAttr("smilieid") == true) return false
        val normalizedUrl = url.replace('\\', '/')
        val markerText = "$normalizedUrl ${image?.className().orEmpty()} ${image?.id().orEmpty()}"
        return ignoredImageParts.none { markerText.contains(it, ignoreCase = true) }
    }
}

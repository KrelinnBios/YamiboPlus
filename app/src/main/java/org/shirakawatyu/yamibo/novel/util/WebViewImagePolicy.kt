package org.shirakawatyu.yamibo.novel.util

object WebViewImagePolicy {
    fun shouldProxyForumAttachment(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("/data/attachment/forum/") ||
                normalized.contains("attachment/forum/") ||
                Regex("[?&]mod=attachment(?:[&#]|$)").containsMatchIn(normalized)
    }
}

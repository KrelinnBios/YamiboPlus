package org.shirakawatyu.yamibo.novel.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object YamiboPostLinkUtil {
    private val candidateRegex = Regex(
        """(?i)(?:https?://)?(?:(?:bbs|m|www)\.)?yamibo\.com/[^\s<>"']+"""
    )
    private val threadPathRegex = Regex("""^/thread-\d+(?:-\d+){0,2}\.html$""", RegexOption.IGNORE_CASE)
    private val forumPathRegex = Regex("""^/forum-\d+(?:-\d+){0,2}\.html$""", RegexOption.IGNORE_CASE)
    private val imagePathRegex = Regex(
        """(?:^|/)(?:data/attachment/.*|\S+\.(?:jpg|jpeg|png|webp|gif|bmp))$""",
        RegexOption.IGNORE_CASE
    )
    private val validHosts = setOf(
        "bbs.yamibo.com",
        "m.yamibo.com",
        "www.yamibo.com",
        "yamibo.com"
    )
    private val trailingPunctuation = setOf(
        '.', ',', ';', ':', '!', '?',
        '。', '，', '；', '：', '！', '？',
        ')', ']', '}', '）', '】', '》', '"'
    )

    fun extractPostUrl(text: CharSequence?): String? {
        val rawText = text?.toString()?.replace("&amp;", "&").orEmpty()
        return candidateRegex.findAll(rawText)
            .mapNotNull { normalizeCandidate(it.value) }
            .firstOrNull()
    }

    fun extractThreadId(url: String?): String? {
        val normalized = normalizePostUrl(url) ?: return null
        val parsed = normalized.toHttpUrlOrNull() ?: return null
        Regex("""^/thread-([1-9]\d*)""", RegexOption.IGNORE_CASE)
            .find(parsed.encodedPath)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return parsed.queryParameter("tid")
            ?.takeIf { it.matches(Regex("""[1-9]\d*""")) }
            ?: parsed.queryParameter("ptid")
                ?.takeIf { it.matches(Regex("""[1-9]\d*""")) }
    }

    fun extractPostId(url: String?): String? {
        val normalized = normalizePostUrl(url) ?: return null
        val parsed = normalized.toHttpUrlOrNull() ?: return null
        return parsed.queryParameter("pid")
            ?.takeIf { it.matches(Regex("""[1-9]\d*""")) }
            ?: parsed.fragment
                ?.let { Regex("^pid([1-9]\\d*)$", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues
                ?.getOrNull(1)
    }
    fun normalizePostUrl(url: String?): String? {
        return extractPostUrl(url)
    }

    /**
     * 归一化个人空间主页链接（home.php?mod=space&uid=xxx），统一转为 bbs + mobile=2，
     * 供 BBSPage 直接加载。非个人空间主页链接返回 null。
     */
    fun normalizeUserSpaceUrl(url: String?): String? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in validHosts) return null
        if (!parsed.encodedPath.equals("/home.php", ignoreCase = true)) return null
        if (!parsed.queryParameter("mod").equals("space", ignoreCase = true)) return null
        if (parsed.queryParameter("uid").isNullOrBlank()) return null
        return parsed.newBuilder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .apply {
                if (parsed.queryParameter("mobile").isNullOrBlank()) {
                    addQueryParameter("mobile", "2")
                }
            }
            .build()
            .toString()
    }

    /**
     * 论坛页内点击帖子时沿用当前页面模板。页面脚本可直接根据 DOM 判断电脑版，
     * 避免无 mobile 参数的电脑版 SEO 链接先被直达流程补成 mobile=2。
     */
    fun normalizePostUrlForTemplate(url: String?, desktopTemplate: Boolean): String? {
        val normalized = normalizePostUrl(url) ?: return null
        val parsed = normalized.toHttpUrlOrNull() ?: return normalized
        val expectedMobile = if (desktopTemplate) "no" else "2"
        return parsed.newBuilder()
            .removeAllQueryParameters("mobile")
            .addQueryParameter("mobile", expectedMobile)
            .build()
            .toString()
    }

    private fun normalizeCandidate(candidate: String): String? {
        val trimmed = candidate.trimEnd { it in trailingPunctuation }
        val withScheme = if (trimmed.startsWith("http", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in validHosts) return null
        if (imagePathRegex.matches(parsed.encodedPath)) return null
        if (!isPostUrl(parsed)) return null

        return parsed.newBuilder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .removeAllQueryParameters("highlight")
            .apply {
                if (parsed.queryParameter("mobile").isNullOrBlank()) {
                    addQueryParameter("mobile", "2")
                }
            }
            .build()
            .toString()
    }

    /**
     * 判断 Cookie 串是否处于「电脑版」会话：用户点过论坛底部的电脑版切换后，
     * Discuz 会写入 mobile=no（含站点前缀如 EeqY_2132_mobile）cookie。
     */
    fun isDesktopSessionCookie(cookies: String?): Boolean {
        if (cookies.isNullOrBlank()) return false
        return cookies.split(';').any { pair ->
            val trimmed = pair.trim()
            val name = trimmed.substringBefore('=')
            val value = trimmed.substringAfter('=', "")
            value.equals("no", ignoreCase = true) &&
                    (name.equals("mobile", ignoreCase = true) ||
                            name.endsWith("_mobile", ignoreCase = true))
        }
    }

    /** 读取 WebView 会话 cookie 判断是否电脑版会话；单元测试等无 WebView 环境按手机版处理。 */
    private fun isDesktopWebSession(): Boolean = try {
        isDesktopSessionCookie(
            android.webkit.CookieManager.getInstance().getCookie("https://bbs.yamibo.com")
        )
    } catch (_: Throwable) {
        false
    }

    /**
     * 电脑版专属页（标签页 misc.php?mod=tag）在手机版会话下直接打开会变成
     * 「提示信息」并自动跳回论坛首页（且手机模板禁用缩放）。这里把这类链接
     * 强制加上 mobile=no，让服务器返回电脑版标签页。无需改写时返回 null。
     * 用户主动切到电脑版会话时不改写：电脑版本就能直接渲染标签页，且改写后的
     * mobile=no URL 会触发加载完成时的 cookie 恢复逻辑，把用户主动选择的
     * 电脑版会话误切回手机版。
     */
    fun normalizePcOnlyPageUrl(url: String?): String? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in validHosts) return null
        if (!parsed.encodedPath.equals("/misc.php", ignoreCase = true)) return null
        if (!parsed.queryParameter("mod").equals("tag", ignoreCase = true)) return null
        if (parsed.queryParameter("mobile") == "no") return null
        if (isDesktopWebSession()) return null
        return parsed.newBuilder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .removeAllQueryParameters("mobile")
            .addQueryParameter("mobile", "no")
            .build()
            .toString()
    }

    /**
     * App 默认保持用户当前选择的模板：手机版会话统一使用 mobile=2，电脑版会话统一使用
     * mobile=no。Discuz 的电脑版列表仍可能给普通帖子链接附带 mobile=2，不能因此把它
     * 当成用户主动切回手机版；只有明确的同页/论坛首页模板切换入口才改变用户选择。
     *
     * 标签页和明确进入的个人空间沿用现有电脑版流程；静态资源、附件、搜索等也不属于
     * 常规论坛页面。帖子 URL（含帖子内分页和 findpost 楼层中转）由网页原生导航或
     * 导航前桥接独占处理，这里不再二次 loadUrl。无需改写时返回 null，避免加载竞态。
     */
    fun normalizeForumPageTemplateUrl(
        url: String?,
        desktopSession: Boolean,
        currentUrl: String? = null
    ): String? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in validHosts) return null
        if (!isMobileForumPage(parsed)) return null
        if (explicitDesktopTemplateSelection(url, currentUrl) != null) return null
        if (isPostUrl(parsed)) return null
        val expectedMobile = if (desktopSession) "no" else "2"
        val mobileValues = parsed.queryParameterValues("mobile")
        if (mobileValues.size == 1 &&
            (mobileValues.single().equals(expectedMobile, ignoreCase = true) ||
                    !desktopSession && mobileValues.single().equals("yes", ignoreCase = true))
        ) {
            return null
        }
        return parsed.newBuilder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .removeAllQueryParameters("mobile")
            .addQueryParameter("mobile", expectedMobile)
            .build()
            .toString()
    }

    /**
     * 识别明确的模板选择。电脑版页脚的 showmobile=yes、论坛首页只有 mobile 参数，或目标
     * 与当前页面仅 mobile 参数不同，才是用户点击模板切换入口。其它页面单独携带
     * mobile=2/no 可能只是站点生成的普通链接，不能据此改变用户选择。
     */
    fun explicitDesktopTemplateSelection(url: String?, currentUrl: String? = null): Boolean? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in validHosts) return null
        val showMobileValues = parsed.queryParameterValues("showmobile")
        if (showMobileValues.size == 1 &&
            showMobileValues.single().equals("yes", ignoreCase = true)
        ) {
            return false
        }
        val values = parsed.queryParameterValues("mobile")
        if (values.size != 1) return null
        val selected = when (values.single()?.lowercase()) {
            "2", "yes" -> false
            "no" -> true
            else -> return null
        }
        val isForumHome =
            parsed.encodedPath.lowercase() in setOf("/", "/index.php", "/forum.php")
        val hasOnlyMobile =
            parsed.queryParameterNames.all { it.equals("mobile", ignoreCase = true) }
        return selected.takeIf {
            isForumHome && hasOnlyMobile ||
                    isSamePageTemplateSwitch(parsed, currentUrl?.toHttpUrlOrNull())
        }
    }

    private fun isSamePageTemplateSwitch(target: HttpUrl, current: HttpUrl?): Boolean {
        if (current == null || current.host.lowercase() !in validHosts) return false
        val currentMobile = current.queryParameterValues("mobile")
        if (currentMobile.size > 1) return false
        val targetMobile = target.queryParameter("mobile") ?: return false
        val currentValue = currentMobile.singleOrNull()
        if (currentValue != null && currentValue.equals(targetMobile, ignoreCase = true)) return false
        return withoutMobile(target) == withoutMobile(current)
    }

    private fun withoutMobile(url: HttpUrl): String =
        url.newBuilder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .removeAllQueryParameters("mobile")
            .fragment(null)
            .build()
            .toString()

    private fun isMobileForumPage(url: HttpUrl): Boolean {
        if (threadPathRegex.matches(url.encodedPath) || forumPathRegex.matches(url.encodedPath)) {
            return true
        }
        return when (url.encodedPath.lowercase()) {
            "/", "/index.php", "/forum.php", "/member.php" -> true
            "/home.php" -> {
                val isDesktopSpace =
                    url.queryParameter("mod").equals("space", ignoreCase = true) &&
                            url.queryParameter("mobile").equals("no", ignoreCase = true)
                !isDesktopSpace
            }
            "/misc.php" -> !url.queryParameter("mod").equals("tag", ignoreCase = true)
            else -> false
        }
    }

    private fun isPostUrl(url: HttpUrl): Boolean {
        if (threadPathRegex.matches(url.encodedPath)) return true
        if (!url.encodedPath.equals("/forum.php", ignoreCase = true)) return false

        val mod = url.queryParameter("mod").orEmpty()
        val tid = url.queryParameter("tid")
        val pid = url.queryParameter("pid")
        return mod.equals("viewthread", ignoreCase = true) && tid.isPositiveId() ||
                mod.equals("redirect", ignoreCase = true) && pid.isPositiveId()
    }

    private fun String?.isPositiveId(): Boolean {
        return this?.matches(Regex("""[1-9]\d*""")) == true
    }
}

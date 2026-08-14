package org.shirakawatyu.yamibo.novel.parser

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.forum.UserProfile
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

object ProfileApiParser {
    private const val FORUM_ORIGIN = "https://bbs.yamibo.com"

    fun parseProfile(rawJson: String): UserProfile {
        val variables = variables(rawJson)
        val uid = variables.stringValue("member_uid").orEmpty()
        val username = cleanText(variables.getString("member_username")).orEmpty()
        val formhash = variables.getString("formhash").orEmpty()
        val groupTitle = cleanText(variables.getString("group_title"))
        // Discuz 的 credits 是按积分公式计算后的总积分；extcredits2 是单项“积分”。
        val totalCredits = variables.intValue("credits")
        val credits = variables.intValue("extcredits2").takeIf { it > 0 }
            ?: totalCredits
        val partner = variables.intValue("extcredits3")
        val posts = variables.intValue("posts")
        val threads = variables.intValue("threads")
        val digestCount = variables.intValue("digests")
        val notice = variables.getJSONObject("notice")
        val hasNewPrivateMessage = notice?.intValue("newpm")?.let { it > 0 } ?: false
        val hasNewPrompt = notice?.intValue("newprompt")?.let { it > 0 } ?: false
        val hasNewMessage = hasNewPrivateMessage || hasNewPrompt
        val avatarUrl = uid.takeIf { it.isNotEmpty() }?.let {
            "$FORUM_ORIGIN/uc_server/avatar.php?uid=$it&size=small"
        }
        return UserProfile(
            uid = uid,
            username = username,
            avatarUrl = avatarUrl,
            groupTitle = groupTitle,
            credits = credits,
            totalCredits = totalCredits,
            partner = partner,
            posts = posts,
            threads = threads,
            digestCount = digestCount,
            formhash = formhash,
            hasNewMessage = hasNewMessage,
            hasNewPrompt = hasNewPrompt
        )
    }

    /**
     * 电脑版个人资料页会提供移动接口缺失的积分和用户组数据。
     * 选择器与论坛实际个人中心结构保持一致，供移动 JSON 接口缺字段时兜底。
     */
    fun parseProfileHtml(html: String): UserProfile {
        val document = Jsoup.parse(html, FORUM_ORIGIN)
        val username = cleanText(document.selectFirst(".avatar_bg .name")?.text())
        if (username.isNullOrBlank()) throw IllegalStateException("未读取到个人资料")

        var uid = ""
        var groupTitle: String? = null
        document.select(".myinfo_list li").forEach { item ->
            val label = item.ownText().trim()
            val value = cleanText(item.selectFirst("span")?.text())
            when {
                label == "UID" -> uid = value.orEmpty()
                label.contains("用户组") || label.contains("用戶組") -> {
                    groupTitle = cleanText(item.selectFirst("span font")?.text()).ifBlank { value.orEmpty() }
                }
            }
        }

        var credits = 0
        var totalCredits = 0
        var partner = 0
        document.select(".user_box li").forEach { item ->
            val text = item.text()
            val value = item.selectFirst("span")?.text()
                ?.replace("点", "")
                ?.replace("點", "")
                ?.trim()
                ?.toIntOrNull() ?: 0
            when {
                text.contains("总积分") || text.contains("總積分") -> totalCredits = value
                text.contains("对象") || text.contains("對象") -> partner = value
                text.contains("积分") || text.contains("積分") -> credits = value
            }
        }

        val hasNewMessage = document.select("a[href]").any { link ->
            link.attr("href").contains("do=pm") && link.selectFirst(".ico_msg") != null
        }
        val hasNewPrompt = document.select("a[href]").any { link ->
            link.attr("href").contains("do=notice") && link.selectFirst(".ico_notice") != null
        }

        val avatarUrl = document.selectFirst(".avatar_m img")?.attr("abs:src")
            ?.substringBefore("?")
            ?.takeIf(String::isNotBlank)
        val formhash = document.selectFirst("input[name=formhash]")?.attr("value").orEmpty()
        return UserProfile(
            uid = uid,
            username = username,
            avatarUrl = avatarUrl,
            groupTitle = groupTitle,
            credits = credits,
            totalCredits = totalCredits,
            partner = partner,
            formhash = formhash,
            hasNewMessage = hasNewMessage || hasNewPrompt,
            hasNewPrompt = hasNewPrompt
        )
    }

    fun mergeProfile(preferred: UserProfile, fallback: UserProfile): UserProfile = preferred.copy(
        uid = preferred.uid.ifBlank { fallback.uid },
        username = preferred.username.ifBlank { fallback.username },
        avatarUrl = preferred.avatarUrl ?: fallback.avatarUrl,
        groupTitle = preferred.groupTitle ?: fallback.groupTitle,
        credits = preferred.credits.takeIf { it > 0 } ?: fallback.credits,
        totalCredits = preferred.totalCredits.takeIf { it > 0 } ?: fallback.totalCredits,
        partner = preferred.partner.takeIf { it > 0 } ?: fallback.partner,
        posts = preferred.posts.takeIf { it > 0 } ?: fallback.posts,
        threads = preferred.threads.takeIf { it > 0 } ?: fallback.threads,
        digestCount = preferred.digestCount.takeIf { it > 0 } ?: fallback.digestCount,
        formhash = preferred.formhash.ifBlank { fallback.formhash },
        hasNewMessage = preferred.hasNewMessage || fallback.hasNewMessage,
        hasNewPrompt = preferred.hasNewPrompt || fallback.hasNewPrompt
    )

    private fun variables(rawJson: String): JSONObject {
        val root = runCatching { JSON.parseObject(rawJson) }
            .getOrElse { throw IllegalStateException("无法解析用户数据", it) }
        val message = root.getJSONObject("Message")
        val messageCode = message?.getString("messageval").orEmpty()
        if (messageCode.isNotBlank()) {
            val detail = message?.getString("messagestr")
                ?.let(::cleanText)
                ?.takeIf(String::isNotBlank)
            throw IllegalStateException(detail ?: "获取用户数据失败（$messageCode）")
        }
        return root.getJSONObject("Variables")
            ?: throw IllegalStateException("用户数据不完整")
    }

    private fun cleanText(text: String?): String {
        return LanguageModeUtil.displayText(text?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty())
    }

    private fun JSONObject.stringValue(key: String): String? =
        get(key)?.toString()?.trim()?.takeIf(String::isNotBlank)

    private fun JSONObject.intValue(key: String, fallback: Int = 0): Int =
        get(key)?.toString()?.toIntOrNull() ?: fallback
}

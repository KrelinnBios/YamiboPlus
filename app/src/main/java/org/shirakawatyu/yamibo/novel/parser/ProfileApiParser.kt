package org.shirakawatyu.yamibo.novel.parser

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
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
        val credits = variables.intValue("credits")
        val posts = variables.intValue("posts")
        val threads = variables.intValue("threads")
        val digestCount = variables.intValue("digests")
        val avatarUrl = uid.takeIf { it.isNotEmpty() }?.let {
            "$FORUM_ORIGIN/uc_server/avatar.php?uid=$it&size=small"
        }
        return UserProfile(
            uid = uid,
            username = username,
            avatarUrl = avatarUrl,
            groupTitle = groupTitle,
            credits = credits,
            posts = posts,
            threads = threads,
            digestCount = digestCount,
            formhash = formhash
        )
    }

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

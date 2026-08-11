package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import android.content.res.Configuration
import android.webkit.CookieManager
import java.util.Locale

object LanguageModeUtil {
    const val SIMPLIFIED = "zh-hans"
    const val TRADITIONAL = "zh-hant"

    fun normalize(mode: String?): String {
        return when (mode?.lowercase()) {
            TRADITIONAL, "zh-tw", "zh-hk", "traditional", "繁體中文", "繁体中文" -> TRADITIONAL
            else -> SIMPLIFIED
        }
    }

    fun acceptLanguageHeader(mode: String?): String {
        return if (normalize(mode) == TRADITIONAL) {
            "zh-TW,zh-Hant;q=0.9,zh;q=0.8,en-US;q=0.7,en;q=0.6"
        } else {
            "zh-CN,zh-Hans;q=0.9,zh;q=0.8,en-US;q=0.7,en;q=0.6"
        }
    }

    fun readerTranslationMode(mode: String?): Int {
        return if (normalize(mode) == TRADITIONAL) 2 else 1
    }

    fun applyForumCookies(mode: String?, currentUrl: String? = null) {
        val normalized = normalize(mode)
        val target = if (normalized == TRADITIONAL) "traditional" else "simplified"
        val flag = if (normalized == TRADITIONAL) "hant" else "hans"
        val cookieValues = listOf(
            "yamibo_language=$normalized; path=/; max-age=31536000; SameSite=Lax",
            "yamibo_language_mode=$normalized; path=/; max-age=31536000; SameSite=Lax",
            "yamiOpenCCMode=$target; path=/; max-age=31536000; SameSite=Lax",
            "yamiOpenCC=$flag; path=/; max-age=31536000; SameSite=Lax",
        )
        val urls = buildList {
            add("https://bbs.yamibo.com/")
            add("https://yamibo.com/")
            if (!currentUrl.isNullOrBlank()) add(currentUrl)
        }
        try {
            val cookieManager = CookieManager.getInstance()
            urls.distinct().forEach { url ->
                cookieValues.forEach { cookie -> cookieManager.setCookie(url, cookie) }
            }
            cookieManager.flush()
        } catch (_: Exception) {}
    }

    fun applyLocale(context: Context, mode: String?) {
        val locale = if (normalize(mode) == TRADITIONAL) Locale("zh", "TW") else Locale("zh", "CN")
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
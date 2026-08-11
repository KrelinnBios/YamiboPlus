package org.shirakawatyu.yamibo.novel.util

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 新手引导展示状态：按页面分别记录是否已展示过。
 * 登录后首次进入对应原生页面时弹出一次，之后不再自动弹出。
 */
object OnboardingUtil {
    enum class Page(val storageKey: String) {
        BOTTOM_NAV("bottom_nav"),
        MANGA_HOME("manga_home"),
        MANGA_READER("manga_reader"),
        FAVORITE("favorite"),
        NOVEL_READER("novel_reader"),
        HISTORY("history"),
    }

    private fun keyFor(page: Page) = stringPreferencesKey("onboarding_${page.storageKey}_shown")

    fun hasShown(page: Page, callback: (Boolean) -> Unit) {
        DataStoreUtil.getData(keyFor(page), callback = {
            callback(it.toBooleanStrictOrNull() ?: false)
        }, onNull = { callback(false) })
    }

    fun markShown(page: Page) {
        DataStoreUtil.addData(true.toString(), keyFor(page))
    }
}

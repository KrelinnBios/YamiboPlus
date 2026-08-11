package org.shirakawatyu.yamibo.novel.util

import androidx.datastore.preferences.core.stringPreferencesKey
import org.shirakawatyu.yamibo.novel.global.GlobalData

/**
 * 当前登录用户身份（uid）管理。
 *
 * 登录后从论坛页面或收藏接口拿到 uid 后本地持久化，供论坛屏蔽功能判断「这条内容是不是我自己发的」。
 * 手机版帖子页本身不带任何自身 uid 标识，所以必须提前存好、注入时回传给页面脚本。
 */
object CurrentUserUtil {
    private val key = stringPreferencesKey("current_uid")

    /** 启动时从本地读取已保存的 uid。 */
    fun load(callback: (String) -> Unit = {}) {
        DataStoreUtil.getData(key, {
            if (it.matches(Regex("[1-9]\\d*"))) GlobalData.currentUid = it
            callback(GlobalData.currentUid)
        }, onNull = { callback(GlobalData.currentUid) })
    }

    /** 保存 uid（仅接受纯数字；与现值相同则跳过写入）。 */
    fun save(uid: String?) {
        val normalized = uid?.trim().orEmpty()
        if (!normalized.matches(Regex("[1-9]\\d*"))) return
        if (GlobalData.currentUid == normalized) return
        GlobalData.currentUid = normalized
        DataStoreUtil.addData(normalized, key)
    }
}

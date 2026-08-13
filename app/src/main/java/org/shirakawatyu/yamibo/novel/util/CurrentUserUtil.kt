package org.shirakawatyu.yamibo.novel.util

import androidx.datastore.preferences.core.stringPreferencesKey
import org.shirakawatyu.yamibo.novel.global.GlobalData

/**
 * 当前登录用户身份（uid、用户名、头像）管理。
 *
 * 登录后从论坛页面或收藏接口拿到 uid 后本地持久化，供论坛屏蔽功能判断「这条内容是不是我自己发的」。
 * 手机版帖子页本身不带任何自身 uid 标识，所以必须提前存好、注入时回传给页面脚本。
 */
object CurrentUserUtil {
    private val keyUid = stringPreferencesKey("current_uid")
    private val keyUsername = stringPreferencesKey("current_username")
    private val keyAvatar = stringPreferencesKey("current_avatar")

    /** 启动时从本地读取已保存的用户信息。 */
    fun load(callback: (String) -> Unit = {}) {
        DataStoreUtil.getData(keyUid, {
            if (it.matches(Regex("[1-9]\\d*"))) GlobalData.currentUid = it
            callback(GlobalData.currentUid)
        }, onNull = { callback(GlobalData.currentUid) })
        DataStoreUtil.getData(keyUsername, {
            GlobalData.currentUserName = it.orEmpty()
        }, onNull = {})
        DataStoreUtil.getData(keyAvatar, {
            GlobalData.currentUserAvatar = it
        }, onNull = {})
    }

    /** 保存 uid（仅接受纯数字；与现值相同则跳过写入）。 */
    fun save(uid: String?) {
        val normalized = uid?.trim().orEmpty()
        if (!normalized.matches(Regex("[1-9]\\d*"))) return
        if (GlobalData.currentUid == normalized) return
        GlobalData.currentUid = normalized
        DataStoreUtil.addData(normalized, keyUid)
    }

    /** 保存用户资料（uid、用户名、头像）。 */
    fun saveProfile(uid: String?, username: String?, avatarUrl: String?) {
        uid?.let {
            val normalized = it.trim()
            if (normalized.matches(Regex("[1-9]\\d*"))) {
                GlobalData.currentUid = normalized
                DataStoreUtil.addData(normalized, keyUid)
            }
        }
        username?.let {
            GlobalData.currentUserName = it
            DataStoreUtil.addData(it, keyUsername)
        }
        avatarUrl?.let {
            GlobalData.currentUserAvatar = it
            DataStoreUtil.addData(it, keyAvatar)
        }
    }

    fun clear() {
        GlobalData.currentUid = ""
        GlobalData.currentUserName = ""
        GlobalData.currentUserAvatar = null
        DataStoreUtil.addData("", keyUid)
        DataStoreUtil.addData("", keyUsername)
        DataStoreUtil.addData("", keyAvatar)
    }
}

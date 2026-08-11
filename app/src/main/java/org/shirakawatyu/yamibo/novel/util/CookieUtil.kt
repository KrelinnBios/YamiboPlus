package org.shirakawatyu.yamibo.novel.util

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.shirakawatyu.yamibo.novel.global.GlobalData

/**
 * Cookie管理工具
 * 负责保存和获取论坛登录cookie
 */
class CookieUtil {
    companion object {
        private val key = stringPreferencesKey("yamibo")

        fun getCookie(callback: (cookie: String) -> Unit) {
            DataStoreUtil.getData(key, { cookie ->
                callback(persistentCookieHeader(cookie))
            }, onNull = {
                callback("")
            })
        }

        fun getCookieFlow(): Flow<String> {
            val dataStore =
                GlobalData.dataStore ?: throw IllegalStateException("DataStore not initialized")
            return dataStore.data
                .map { preferences ->
                    persistentCookieHeader(preferences[key].orEmpty())
                }
        }

        fun saveCookie(cookie: String) {
            val persistentCookie = persistentCookieHeader(cookie)
            GlobalData.currentCookie = persistentCookie
            DataStoreUtil.addData(persistentCookie, key)
        }

        /**
         * 只持久化登录/论坛会话 Cookie。WAF 令牌带有效期，丢失属性后保存为普通字符串会在
         * 下次启动时被错误地复活为长期 Cookie；它们应留在 CookieManager 或进程内存储中。
         */
        internal fun persistentCookieHeader(cookie: String): String =
            cookie.split(';')
                .map(String::trim)
                .filter { part ->
                    val separator = part.indexOf('=')
                    if (separator <= 0) return@filter false
                    val name = part.substring(0, separator)
                    !isTransientWafCookie(name)
                }
                .joinToString("; ")

        private fun isTransientWafCookie(name: String): Boolean =
            name.equals("abymg_id", ignoreCase = true) ||
                    name.equals("nox_jst_v1", ignoreCase = true) ||
                    name.startsWith("nox_", ignoreCase = true)
    }
}

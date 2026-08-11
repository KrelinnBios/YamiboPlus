package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil

object CacheMaintenance {
    const val RETENTION_DAYS = 7
    private const val PREFS_NAME = "cache_maintenance"
    private const val LAST_AUTO_CLEAR_KEY = "last_auto_clear"
    private const val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    fun onAutoClearChanged(context: Context, enabled: Boolean) {
        if (enabled) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_AUTO_CLEAR_KEY, System.currentTimeMillis())
                .apply()
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun runIfDue(context: Context) {
        if (!GlobalData.isAutoClearCacheEnabled.value) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRun = prefs.getLong(LAST_AUTO_CLEAR_KEY, 0L)
        if (lastRun == 0L) {
            prefs.edit().putLong(LAST_AUTO_CLEAR_KEY, now).apply()
            return
        }
        if (now - lastRun < RETENTION_MS) return

        LocalCacheUtil.getInstance(context).clearOlderThan(now - RETENTION_MS)
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        FavoriteUtil.resetMangaCacheCountsSuspend()
        prefs.edit().putLong(LAST_AUTO_CLEAR_KEY, now).apply()
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAll(context: Context) {
        LocalCacheUtil.getInstance(context).clearAllCache()
        clearImages(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_AUTO_CLEAR_KEY, System.currentTimeMillis())
            .apply()
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearImages(context: Context) {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        FavoriteUtil.resetMangaCacheCountsSuspend()
    }
}

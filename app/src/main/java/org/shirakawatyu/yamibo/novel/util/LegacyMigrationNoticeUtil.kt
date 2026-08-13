package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import java.io.File

/**
 * 记录 300 Lite 数据迁移提示是否已经关闭。
 *
 * 标记放在 noBackupFilesDir，避免导入旧版备份时被覆盖，导致重启后重复提示。
 */
object LegacyMigrationNoticeUtil {
    private const val MARKER_FILE_NAME = "legacy_migration_notice_v1_shown"

    fun hasShown(context: Context): Boolean = hasShownIn(context.noBackupFilesDir)

    fun markShown(context: Context): Boolean = markShownIn(context.noBackupFilesDir)

    internal fun hasShownIn(directory: File): Boolean =
        File(directory, MARKER_FILE_NAME).isFile

    internal fun markShownIn(directory: File): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        val marker = File(directory, MARKER_FILE_NAME)
        return marker.isFile || runCatching { marker.createNewFile() }.getOrDefault(false)
    }
}

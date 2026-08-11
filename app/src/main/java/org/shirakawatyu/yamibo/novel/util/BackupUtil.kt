package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份与恢复。
 *
 * 备份内容（白名单，见 [backupRoots]）：
 * - files/datastore/          DataStore 单文件：收藏与排序、浏览历史、黑名单、阅读进度、
 *                             更新检查档案、当前 uid、全部设置
 * - files/manga_directory/    漫画目录 JSON
 * - shared_prefs/             阅读器等 SharedPreferences 兼容存储
 *
 * 不备份：小说页面/漫画图片缓存（可重建、体积大）、WebView 数据（跨设备恢复不可靠，
 * 恢复后论坛可能需要重新登录）、崩溃日志。
 *
 * 恢复策略：先整体解压到临时目录并校验元数据，确认是本应用的备份后再替换目标文件，
 * 最后必须重启进程——运行中的 DataStore 持有旧文件句柄，不重启会把恢复内容写回旧数据。
 */
object BackupUtil {
    private const val META_ENTRY = "yamibo_backup_meta.json"
    private const val META_PACKAGE_KEY = "packageName"

    private data class BackupRoot(val zipPrefix: String, val resolve: (Context) -> File)

    // zip 内前缀 -> 本机实际目录 的白名单映射；导入时不在白名单内的条目一律忽略
    private val backupRoots = listOf(
        BackupRoot("files/datastore") { File(it.filesDir, "datastore") },
        BackupRoot("files/manga_directory") { File(it.filesDir, "manga_directory") },
        BackupRoot("shared_prefs") { File(it.dataDir, "shared_prefs") },
    )

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "300Lite_backup_$stamp.zip"
    }

    /** 导出备份到 SAF Uri，返回打包的文件数。 */
    suspend fun exportBackup(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var fileCount = 0
            context.contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    val meta = mapOf(
                        META_PACKAGE_KEY to context.packageName,
                        "createdAt" to System.currentTimeMillis(),
                    )
                    zip.putNextEntry(ZipEntry(META_ENTRY))
                    zip.write(JSON.toJSONString(meta).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    for (root in backupRoots) {
                        val dir = root.resolve(context)
                        if (!dir.isDirectory) continue
                        dir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(dir).invariantSeparatorsPath
                            zip.putNextEntry(ZipEntry("${root.zipPrefix}/$relative"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            fileCount++
                        }
                    }
                }
            } ?: throw IllegalStateException("无法写入所选位置")
            if (fileCount == 0) throw IllegalStateException("没有可备份的数据")
            fileCount
        }
    }

    /**
     * 从 SAF Uri 导入备份，返回恢复的文件数。
     * 成功后调用方必须引导用户重启应用（可用 [restartApp]）。
     */
    suspend fun importBackup(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stagingDir = File(context.cacheDir, "backup_restore_tmp")
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            try {
                var metaValid = false
                var fileCount = 0

                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            when {
                                entry.isDirectory -> Unit
                                name == META_ENTRY -> {
                                    val meta = JSON.parseObject(zip.readBytes().toString(Charsets.UTF_8))
                                    if (meta?.getString(META_PACKAGE_KEY) != context.packageName) {
                                        throw IllegalStateException("不是本应用的备份文件")
                                    }
                                    metaValid = true
                                }
                                // 防 zip slip：路径必须干净且命中白名单前缀
                                name.contains("..") -> Unit
                                backupRoots.any { name.startsWith("${it.zipPrefix}/") } -> {
                                    val target = File(stagingDir, name)
                                    if (!target.canonicalPath.startsWith(stagingDir.canonicalPath)) {
                                        throw IllegalStateException("备份文件路径非法")
                                    }
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { zip.copyTo(it) }
                                    fileCount++
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("无法读取所选文件")

                if (!metaValid) throw IllegalStateException("不是本应用的备份文件")
                if (fileCount == 0) throw IllegalStateException("备份文件中没有可恢复的数据")

                // 全部解压校验通过后，整目录替换。此后进程内的 DataStore 缓存已经过期，
                // 调用方必须尽快重启进程，避免旧数据被写回。
                for (root in backupRoots) {
                    val staged = File(stagingDir, root.zipPrefix)
                    if (!staged.isDirectory) continue
                    val target = root.resolve(context)
                    target.deleteRecursively()
                    target.parentFile?.mkdirs()
                    if (!staged.copyRecursively(target, overwrite = true)) {
                        throw IllegalStateException("写入 ${root.zipPrefix} 失败")
                    }
                }
                fileCount
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    /** 冷重启应用：拉起入口 Activity 的新任务栈后立即结束当前进程。 */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }
}

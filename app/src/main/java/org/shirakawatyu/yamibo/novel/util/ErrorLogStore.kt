package org.shirakawatyu.yamibo.novel.util

import org.shirakawatyu.yamibo.novel.global.GlobalData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 错误日志的磁盘持久化：按天分段保存在 filesDir/logs/yyyy-MM-dd.log，
 * 只保留最近 [RETAIN_DAYS] 天，超期自动淘汰。写入走单线程队列，不阻塞调用方。
 */
object ErrorLogStore {
    const val RETAIN_DAYS = 7
    /** 单个日志段最多保留的行数，超出后仅保留最新 [MAX_LINES_PER_DAY] 行。 */
    const val MAX_LINES_PER_DAY = 300

    data class DayInfo(
        val day: String,
        val lineCount: Int,
        val waf: Int,
        val network: Int,
        val http: Int
    )

    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val executor = Executors.newSingleThreadExecutor()

    /** 追加一条日志到当天文件；应用上下文未就绪时静默跳过（内存日志不受影响）。 */
    fun append(line: String) {
        val dir = logDir() ?: return
        val content = timeFormatter.format(Date()) + "  " + line + "\n"
        executor.execute {
            runCatching {
                val file = File(dir, dayFormatter.format(Date()) + ".log")
                file.appendText(content, Charsets.UTF_8)
                trimOldLines(file)
                retainOldFiles(dir)
            }
        }
    }

    /** 按天倒序列出所有日志段（自动淘汰超期文件）。 */
    fun listDays(): List<DayInfo> {
        val dir = logDir() ?: return emptyList()
        retainOldFiles(dir)
        return dir.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { file ->
                val day = file.name.removeSuffix(".log")
                val lines = runCatching { file.readText(Charsets.UTF_8) }
                    .getOrDefault("")
                    .lines()
                    .filter(String::isNotBlank)
                if (lines.isEmpty()) null else DayInfo(
                    day = day,
                    lineCount = lines.size,
                    waf = lines.count { it.contains("WAF", ignoreCase = true) },
                    network = lines.count { isNetworkLine(it) },
                    http = lines.count { it.contains("HTTP", ignoreCase = true) }
                )
            }
            ?: emptyList()
    }

    /** 读取某天的完整日志文本。 */
    fun readDay(day: String): String =
        runCatching {
            redactSensitiveValues(dayFile(day)?.readText(Charsets.UTF_8).orEmpty())
        }.getOrDefault("")

    /** 删除某天的日志段。 */
    fun deleteDay(day: String) {
        executor.execute {
            runCatching { dayFile(day)?.delete() }
        }
    }

    /** 清空全部历史日志。 */
    fun clearAll() {
        executor.execute {
            runCatching {
                logDir()?.listFiles { file -> file.name.endsWith(".log") }
                    ?.forEach { it.delete() }
            }
        }
    }

    private fun isNetworkLine(line: String): Boolean =
        line.contains("网络", ignoreCase = true) ||
            line.contains("IOException", ignoreCase = true) ||
            line.contains("timeout", ignoreCase = true) ||
            line.contains("reset", ignoreCase = true)

    /** 历史日志可能来自旧版本，读取/提交前统一隐藏会话凭证。 */
    private fun redactSensitiveValues(text: String): String =
        text.replace(
            Regex("(\"(?:auth|saltkey|password|token)\"\\s*:\\s*\")[^\"]*(\")"),
            "$1<redacted>$2"
        ).replace(
            Regex("((?:Cookie|cookie):\\s*)[^\\r\\n]+"),
            "$1<redacted>"
        )

    private fun logDir(): File? {
        val context = GlobalData.applicationContext ?: return null
        return File(context.filesDir, "logs").apply { mkdirs() }
    }

    private fun dayFile(day: String): File? {
        val dir = logDir() ?: return null
        return File(dir, "$day.log")
    }

    /** 单个日志段超过 [MAX_LINES_PER_DAY] 行时，仅保留最新若干行。 */
    private fun trimOldLines(file: File) {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        if (lines.size <= MAX_LINES_PER_DAY) return
        file.writeText(lines.takeLast(MAX_LINES_PER_DAY).joinToString("\n") + "\n", Charsets.UTF_8)
    }

    /** 删除超过 RETAIN_DAYS 天的日志文件。 */
    private fun retainOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - RETAIN_DAYS * 24L * 60L * 60L * 1000L
        dir.listFiles { file -> file.name.endsWith(".log") }?.forEach { file ->
            val day = file.name.removeSuffix(".log")
            val timestamp = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)?.time ?: 0L
            }.getOrDefault(0L)
            if (timestamp in 1 until cutoff) {
                file.delete()
            }
        }
    }
}

package org.shirakawatyu.yamibo.novel.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进程内错误日志（环形缓冲）。
 *
 * 供错误页“查看错误日志”直接展示，方便排查 WAF 405 等网络问题，无需连接电脑抓 logcat。
 */
object AppErrorLog {
    private const val MAX_ENTRIES = 200
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    private val entries = ArrayList<String>(MAX_ENTRIES)

    @Synchronized
    fun record(message: String) {
        entries.add(formatter.format(Date()) + "  " + message)
        if (entries.size > MAX_ENTRIES) {
            entries.subList(0, entries.size - MAX_ENTRIES).clear()
        }
    }

    @Synchronized
    fun snapshot(): String = entries.joinToString("\n")

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    @Synchronized
    fun clear() = entries.clear()
}

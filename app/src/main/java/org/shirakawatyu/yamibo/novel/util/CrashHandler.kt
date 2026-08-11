package org.shirakawatyu.yamibo.novel.util

import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃处理器。
 *
 * 做两件事：
 * 1. 把所有未捕获异常的堆栈写入崩溃日志文件，便于事后排查“偶发闪退”。
 * 2. 对**非主线程**的未捕获异常进行兜底吞掉——这类异常多来自后台协程/网络/图片线程，
 *    系统默认行为是直接杀掉整个进程（即用户感知的“闪退”）。记录后让进程继续存活，
 *    通常 UI 仍可正常使用。
 *
 * 主线程（UI 线程）异常不吞，仍交给系统默认处理：此时 Looper 已损坏，继续运行只会卡死，
 * 让它正常崩溃反而更安全，同时崩溃日志已经落盘。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "YamiboCrash"
    private const val CRASH_DIR = "crash"
    private const val MAX_CRASH_FILES = 15

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { saveCrashLog(thread, throwable) }

        val isMainThread = thread === Looper.getMainLooper().thread

        if (isMainThread) {
            // 主线程崩溃无法安全恢复，交还系统默认处理器正常崩溃。
            val handler = defaultHandler
            if (handler != null) {
                handler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        } else {
            // 后台线程异常：记录后吞掉，保持进程存活，避免整体闪退。
            Log.e(TAG, "后台线程未捕获异常已拦截: ${thread.name}", throwable)
        }
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, CRASH_DIR)
        if (!dir.exists() && !dir.mkdirs()) return

        val timeFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val now = Date()
        val file = File(dir, "crash_${timeFormat.format(now)}.txt")

        val stackTrace = StringWriter().use { sw ->
            PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
            sw.toString()
        }

        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        val content = buildString {
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)}")
            appendLine("版本: $versionName")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("线程: ${thread.name} (id=${thread.id})")
            appendLine("异常: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("--------------------------------")
            append(stackTrace)
        }

        file.writeText(content)
        pruneOldLogs(dir)
    }

    /** 仅保留最新的若干份崩溃日志，避免无限增长。 */
    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles { _, name -> name.startsWith("crash_") } ?: return
        if (files.size <= MAX_CRASH_FILES) return
        files.sortedByDescending { it.lastModified() }
            .drop(MAX_CRASH_FILES)
            .forEach { runCatching { it.delete() } }
    }
}

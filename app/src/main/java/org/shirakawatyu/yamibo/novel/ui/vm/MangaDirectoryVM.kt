package org.shirakawatyu.yamibo.novel.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.DirectoryStrategy
import org.shirakawatyu.yamibo.novel.bean.MangaDirectory
import org.shirakawatyu.yamibo.novel.repository.DirectoryRepository
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner

class MangaDirectoryVM(application: Application) : AndroidViewModel(application) {
    private val repo = DirectoryRepository.getInstance(application)

    // 当前正在浏览的漫画目录数据
    var currentDirectory by mutableStateOf<MangaDirectory?>(null)
        private set

    // 是否正在请求网络更新目录
    var isUpdatingDirectory by mutableStateOf(false)
        private set

    // 更新按钮的冷却时间
    var directoryCooldown by mutableIntStateOf(0)
        private set

    var showSearchShortcut by mutableStateOf(false)
        private set
    var searchShortcutCountdown by mutableIntStateOf(0)
        private set

    /**
     * 接收 WebView 传来的 HTML 进行静态解析初始化
     * 这里的 html 不一定来自 WebView。
     * API 成功时，它是由 postlist[].message 拼接伪装的 HTML 片段；
     * WebView 兜底时，才是真实页面 HTML。
     */
    fun initDirectoryFromWeb(url: String, html: String, title: String) {
        val tid = MangaTitleCleaner.extractTidFromUrl(url) ?: return
        viewModelScope.launch {
            val dir = repo.initDirectoryForThread(tid, url, title, html)
            currentDirectory = dir
            updateMangaDirectory(
                isForced = shouldForceSearchOnOpen(dir),
                currentTid = tid,
                ignoreCooldown = true
            )
        }
    }

    fun loadDirectoryByUrl(currentUrl: String, refreshAfterLoad: Boolean = false) {
        viewModelScope.launch {
            val tid = MangaTitleCleaner.extractTidFromUrl(currentUrl) ?: return@launch
            val allDirs = repo.getAllDirectories()
            val targetDir = allDirs.find { dir -> dir.chapters.any { it.tid == tid } }

            if (targetDir != null) {
                currentDirectory = targetDir
                if (refreshAfterLoad) {
                    updateMangaDirectory(
                        isForced = shouldForceSearchOnOpen(targetDir),
                        currentTid = tid,
                        ignoreCooldown = true
                    )
                }
            }
        }
    }

    private fun shouldForceSearchOnOpen(dir: MangaDirectory): Boolean {
        if (dir.strategy == DirectoryStrategy.LINKS) return false
        val smallDirectoryNeedsRetry = dir.chapters.size <= 2 &&
                (dir.lastUpdateTime == 0L || System.currentTimeMillis() - dir.lastUpdateTime > 30 * 60 * 1000L)
        if (smallDirectoryNeedsRetry) return true
        if (dir.strategy == DirectoryStrategy.SEARCHED) return false
        return dir.lastUpdateTime == 0L
    }

    /** 打开漫画后在后台更新目录。 */
    fun updateMangaDirectory(isForced: Boolean = false, currentTid: String? = null, ignoreCooldown: Boolean = false) {
        val dir = currentDirectory ?: return
        if (isUpdatingDirectory || (!ignoreCooldown && directoryCooldown > 0)) return

        viewModelScope.launch {
            isUpdatingDirectory = true
            showSearchShortcut = false

            val result =
                repo.manuallyUpdateDirectory(dir, forceSearch = isForced, currentTid = currentTid)

            result.onSuccess { updateResult ->
                currentDirectory = updateResult.directory

                if (updateResult.searchPerformed) {
                    startDirectoryCooldown(20)
                } else {
                    triggerSearchShortcutWindow()
                }
            }.onFailure { error ->
                if (error.message?.contains("搜索冷却") == true) {
                    startDirectoryCooldown(20)
                } else {
                    startDirectoryCooldown(5)
                }
            }
            isUpdatingDirectory = false
        }
    }

    private fun triggerSearchShortcutWindow() {
        viewModelScope.launch {
            showSearchShortcut = true
            searchShortcutCountdown = 5
            while (searchShortcutCountdown > 0) {
                delay(1000)
                searchShortcutCountdown--
            }
            showSearchShortcut = false
        }
    }

    private fun startDirectoryCooldown(seconds: Int) {
        viewModelScope.launch {
            directoryCooldown = seconds
            while (directoryCooldown > 0) {
                delay(1000)
                directoryCooldown--
            }
        }
    }

    fun updateDirectoryInfo(
        newTitle: String,
        newTranslationGroup: String,
        newPublisher: String,
        currentTid: String
    ) {
        val dir = currentDirectory ?: return
        if (isUpdatingDirectory) return

        viewModelScope.launch {
            isUpdatingDirectory = true
            showSearchShortcut = false
            try {
                val mergedDir = repo.renameAndMergeDirectory(
                    dir,
                    newTitle,
                    newTranslationGroup,
                    newPublisher,
                    currentTid
                )
                currentDirectory = mergedDir

                repo.manuallyUpdateDirectory(
                    currentDir = mergedDir,
                    forceSearch = true,
                    currentTid = currentTid
                ).onSuccess { updateResult ->
                    currentDirectory = updateResult.directory
                    startDirectoryCooldown(20)
                }.onFailure { error ->
                    if (error.message?.contains("搜索冷却") == true) {
                        startDirectoryCooldown(20)
                    } else {
                        startDirectoryCooldown(5)
                    }
                }
            } finally {
                isUpdatingDirectory = false
            }
        }
    }
}

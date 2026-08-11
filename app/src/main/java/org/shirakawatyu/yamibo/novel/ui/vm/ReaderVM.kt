package org.shirakawatyu.yamibo.novel.ui.vm

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.Content
import org.shirakawatyu.yamibo.novel.bean.ContentType
import org.shirakawatyu.yamibo.novel.bean.ReaderSettings
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.page.typefaceFromMode
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.GlobalChapter
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.reader.ReaderReturnBridge
import org.shirakawatyu.yamibo.novel.util.reader.ReaderMemoryPrewarmManager
import org.shirakawatyu.yamibo.novel.util.reader.ReaderSpacingOptions
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.history.HistoryUtil
import org.shirakawatyu.yamibo.novel.util.reader.CacheData
import org.shirakawatyu.yamibo.novel.util.reader.CacheUtil
import org.shirakawatyu.yamibo.novel.util.reader.AuthenticatedThreadPageLoader
import org.shirakawatyu.yamibo.novel.util.reader.ChineseConvertUtil
import org.shirakawatyu.yamibo.novel.util.reader.FontMetricsUtil
import org.shirakawatyu.yamibo.novel.util.reader.HTMLUtil
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil
import org.shirakawatyu.yamibo.novel.util.reader.TextUtil
import org.shirakawatyu.yamibo.novel.util.reader.ValueUtil
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
class ReaderVM(private val applicationContext: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderState())
    val uiState = _uiState.asStateFlow()
    private val _currentPercentage = MutableStateFlow(0f)
    val currentPercentage =  _currentPercentage.asStateFlow()

    private var pagerState: PagerState? = null
    private var maxHeight = 0.dp
    private var maxWidth = 0.dp
    private var initialized = false
    private val logTag = "ReaderVM"
    private var compositionScope: CoroutineScope? = null
    private var pageEnterTime = 0L
    private var loadJob: Job? = null
    private var loadRequestId = 0

    private companion object {
        private const val READER_API_TIMEOUT_MS = 15_000L
        private const val READER_API_MAX_ATTEMPTS = 2
        private const val READER_API_RETRY_DELAY_MS = 800L
    }

    /**
     * Reader 的解析 / 简繁转换 / 分页都属于 CPU 密集任务。
     * 使用单独的单线程 dispatcher，避免在切换简繁或大幅改字体时把 Default 线程池打满，影响动画帧。
     */
    private val readerLayoutDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ReaderLayout").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    var url by mutableStateOf("")
        private set
    // 原始入口 URL，只用于兼容旧版本缓存 key。
    // ReaderVM.url 本身会统一为 FavoriteUtil.normalizeUrl(cleanUrl)。
    private var rawReaderUrl: String = ""

    private fun readerIdentityAliases(): List<String> {
        val aliases = mutableListOf<String>()

        if (rawReaderUrl.isNotBlank()) {
            aliases += rawReaderUrl
            aliases += ReaderReturnBridge.toAbsoluteBbsUrl(rawReaderUrl)
            aliases += ReaderReturnBridge.stripReaderTransientParams(
                ReaderReturnBridge.toAbsoluteBbsUrl(rawReaderUrl)
            )
        }

        if (url.isNotBlank()) {
            aliases += ReaderReturnBridge.toAbsoluteBbsUrl(url)
            aliases += ReaderReturnBridge.stripReaderTransientParams(
                ReaderReturnBridge.toAbsoluteBbsUrl(url)
            )
        }

        return aliases
            .map { it.trim() }
            .filter { it.isNotBlank() && it != url }
            .distinct()
    }

    private suspend fun getMemoryCacheCompat(pageNum: Int): CacheData? {
        return suspendCoroutine { cont ->
            CacheUtil.getCacheCompat(
                primaryUrl = url,
                pageNum = pageNum,
                aliasUrls = readerIdentityAliases()
            ) { data ->
                cont.resume(data)
            }
        }
    }

    private fun buildReaderMemoryPrewarmTarget(pageNum: Int): ReaderMemoryPrewarmManager.Target? {
        val tid = ReaderReturnBridge.extractTid(url) ?: return null
        val authorId = currentAuthorId
            ?: _uiState.value.authorId
            ?: ReaderReturnBridge.extractAuthorId(url)
            ?: return null

        return ReaderMemoryPrewarmManager.Target(
            primaryUrl = ReaderMemoryPrewarmManager.canonicalReaderUrl(url),
            tid = tid,
            page = pageNum,
            authorId = authorId,
            aliasUrls = readerIdentityAliases()
        )
    }

    private suspend fun awaitInFlightMemoryPrewarm(pageNum: Int): CacheData? {
        val target = buildReaderMemoryPrewarmTarget(pageNum) ?: return null
        return ReaderMemoryPrewarmManager.awaitInFlightDataOrNull(target)
    }

    var isTransitioning by mutableStateOf(false)
    var showLoadingScrim by mutableStateOf(false)
        private set

    private val rawContentList = ArrayList<Content>()
    private var latestPage: Int = 0
    var currentThreadTitle: String? = null
        private set
    private var currentThreadAuthor: String = ""
    private var currentThreadSection: String = ""
    private var currentAuthorId: String? = null
        set(value) {
            field = value
            _uiState.value = _uiState.value.copy(authorId = value)
        }
    private var isPreloading = false
    private var maxPageCalculated: Int = 0
    private val PRELOAD_THRESHOLD_VERTICAL = 300
    private val PRELOAD_THRESHOLD_HORIZONTAL = 30
    private var viewBeingPreloaded = 0
    private var nextHtmlList: List<Content>? = null
    private var nextChapterList: List<ChapterInfo>? = null
    private var nextRawHtml: String? = null

    // ==================== 缓存相关 ====================
    private val diskCacheRetries = mutableMapOf<Int, Int>()
    private val MAX_CACHE_RETRIES = 2
    private val CACHE_RETRY_DELAY_MS = 5000L

    private val localCache by lazy { LocalCacheUtil.getInstance(applicationContext) }

    private val _cachedPages = MutableStateFlow<Set<Int>>(emptySet())
    val cachedPages: StateFlow<Set<Int>> = _cachedPages

    private val _cacheProgress = MutableStateFlow<CacheProgress?>(null)
    val cacheProgress: StateFlow<CacheProgress?> = _cacheProgress

    private val _isDiskCaching = MutableStateFlow(false)
    val isDiskCaching: StateFlow<Boolean> = _isDiskCaching.asStateFlow()

    private var diskCacheQueue: MutableSet<Int> = mutableSetOf()
    private var diskCacheIncludeImages: Boolean = false
    private var ignoreFirstFakeZero = false
    private var diskCacheTotalPages: Int = 0
    private var diskCacheCurrentPage: Int = 0
    private var automaticCacheMaxPage: Int = 0
    private var currentRawHtml: String? = null

    // ==================== 全书章节目录（跨论坛页聚合） ====================
    // 论坛页 -> 该页章节标题序列；随当前页加载与后台磁盘缓存逐页补全。
    private val pageChapterTitles = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()
    private var globalIndexUrl: String? = null
    private var globalIndexCollectorJob: Job? = null

    /**
     * 非普通收藏入口的缓存身份。
     *
     * 普通 OtherWebPage FAB 进入 ReaderPage 时不开放磁盘缓存，
     * 因为缓存管理缺少稳定标题/归属。
     * 但如果 OtherWebPage 命中了 ReaderReturnBridge 的同 tid 上下文，
     * 就可以复用原 ReaderPage 的稳定 URL 和标题，让缓存仍然能在管理页被识别。
     */
    private var externalCacheIdentityEnabled: Boolean = false
    private var externalCacheTitle: String? = null

    private var currentCacheSessionShowsProgress: Boolean = true
    private var currentAsciiRatios: FloatArray = FloatArray(128) { 0.5f }

    /**
     * rawContentList 的稳定版本号。
     * 版本号由 HTML、网页页码、简繁模式、图片模式计算，不再每次 parse 都递增。
     * 这样切回已解析过的简繁模式或图片模式时，layoutCache 才有机会命中。
     */
    private var rawContentListVersion: Long = 0L

    /**
     * 只缓存“正文分页结果”，不缓存尾部的“正在加载/刷新本页/下一页预览”。
     * 因为尾部内容依赖 nextHtmlList 和 currentView/maxWebView，是动态状态。
     */
    private val layoutCache = LruCache<LayoutCacheKey, List<Content>>(12)

    /**
     * 缓存 HTML -> Content 的解析结果。
     * 这层缓存能跳过 Jsoup、HTMLUtil 和 OpenCC，尤其对简体/繁体来回切换很有用。
     */
    private val parsedContentCache = LruCache<ParsedContentCacheKey, List<Content>>(12)

    private data class ParsedContentCacheKey(
        val url: String,
        val webPage: Int,
        val htmlLength: Int,
        val htmlHash: Int,
        val translationMode: Int,
        val loadImages: Boolean
    )

    private data class LayoutCacheKey(
        val url: String,
        val rawVersion: Long,
        val widthPx: Int,
        val heightPx: Int,
        val fontSizePx: Int,
        val lineHeightPx: Int,
        val letterSpacingPx: Int,
        val paddingPx: Int,
        val isVerticalMode: Boolean,
        val fontFamily: Int,
        val translationMode: Int,
        val loadImages: Boolean
    )

    /**
     * 重新分页时用的阅读位置锚点。
     * - chapterIndex：用章节序号，而不是章节标题，避免简繁转换后标题变化导致匹配失败。
     * - charOffsetInChapter / chapterProgress：字体、行高、padding 改变后，按章节内文本进度恢复位置。
     * - totalProgress：极端情况下的兜底。
     */
    private data class PageAnchor(
        val totalProgress: Float,
        val chapterIndex: Int?,
        val chapterProgress: Float,
        val charOffsetInChapter: Int
    )

    data class CacheProgress(
        val totalPages: Int,
        val currentPage: Int,
        val currentPageNum: Int,
        val isComplete: Boolean = false
    )

    init {
        viewModelScope.launch {
            localCache.index.collect { index ->
                if (url.isNotEmpty()) {
                    updateCachedPagesFromIndex(index)
                }
            }
        }
    }

    private fun updateCachedPagesFromIndex(index: Map<String, LocalCacheUtil.CacheIndex>) {
        _cachedPages.value = localCache.getCachedPageNumsCompat(
            primaryUrl = url,
            aliasUrls = readerIdentityAliases()
        )
    }

    fun setExternalCacheIdentity(enabled: Boolean, title: String?) {
        externalCacheIdentityEnabled = enabled
        externalCacheTitle = title
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun scheduleDiskCacheRefresh() {
        if (url.isBlank() || currentAuthorId == null) return
        ReaderReturnBridge.pendingCacheRefresh = ReaderReturnBridge.PendingCacheRefresh(
            url = url,
            pageNum = _uiState.value.currentView,
            authorId = currentAuthorId,
            cacheTitle = cacheTitleForDisk()
        )
    }

    private fun cacheTitleForDisk(): String? {
        return if (externalCacheIdentityEnabled) externalCacheTitle else null
    }

    // ==================== 磁盘缓存功能 ====================
    fun startCaching(
        pagesToCache: Set<Int>,
        includeImages: Boolean = false,
        showProgressDialog: Boolean = true
    ) {
        if (_isDiskCaching.value) {
            Log.w(logTag, "Already caching. New request ignored.")
            return
        }
        if (pagesToCache.isEmpty()) return

        _isDiskCaching.value = true
        diskCacheQueue = pagesToCache.toMutableSet()
        diskCacheIncludeImages = includeImages
        diskCacheTotalPages = pagesToCache.size
        diskCacheCurrentPage = 0
        diskCacheRetries.clear()
        currentCacheSessionShowsProgress = showProgressDialog
        viewModelScope.launch(Dispatchers.Main) {
            loadNextPageForDiskCache(true)
        }
    }

    private fun startAutomaticCachingIfReady(maxPage: Int) {
        if (url.isBlank() || currentAuthorId == null || maxPage <= 0) return
        if (maxPage <= automaticCacheMaxPage || _isDiskCaching.value) return

        val identityUrl = url
        val identityAuthorId = currentAuthorId
        val identityAliases = readerIdentityAliases()
        automaticCacheMaxPage = maxPage
        viewModelScope.launch(Dispatchers.IO) {
            val validCachedPages = mutableSetOf<Int>()
            for (page in 1..maxPage) {
                if (
                    isCurrentReaderCache(
                        localCache.loadPageCompat(
                            primaryUrl = identityUrl,
                            pageNum = page,
                            aliasUrls = identityAliases
                        )
                    )
                ) {
                    validCachedPages += page
                }
            }
            val pagesToCache = (1..maxPage).toSet() - validCachedPages

            withContext(Dispatchers.Main) {
                if (
                    url != identityUrl ||
                    currentAuthorId != identityAuthorId ||
                    automaticCacheMaxPage != maxPage
                ) return@withContext
                if (_isDiskCaching.value) {
                    // 校验期间若被手动缓存抢占，允许缓存结束后的下一次加载重新补齐。
                    automaticCacheMaxPage = 0
                    return@withContext
                }
                if (pagesToCache.isNotEmpty()) {
                    startCaching(
                        pagesToCache = pagesToCache,
                        includeImages = _uiState.value.loadImages,
                        showProgressDialog = false
                    )
                }
            }
        }
    }

    private fun isCurrentReaderCache(data: CacheData?): Boolean {
        return data != null &&
            data.contentVersion >= AuthenticatedThreadPageLoader.CONTENT_VERSION
    }

    private fun isDiskCacheHtmlValid(htmlContent: String?): Boolean {
        if (htmlContent.isNullOrBlank()) return false
        if (htmlContent.length < 300) return false
        if (htmlContent.contains("[Error] Content element not found") ||
            htmlContent.contains("[Error] 页面加载失败")) return false
        if (!htmlContent.contains("class=\"message\"") && !htmlContent.contains("class='message'")) return false
        return true
    }

    private fun loadPageForDiskCache(pageNum: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authorId = currentAuthorId ?: run {
                    withContext(Dispatchers.Main) { processDiskCachePage(pageNum, "", 1) }
                    return@launch
                }
                val loaded = AuthenticatedThreadPageLoader.loadReaderPage(
                    tid = extractTid(),
                    page = pageNum,
                    authorIdHint = authorId,
                    context = applicationContext
                )
                maxPageCalculated = maxOf(maxPageCalculated, loaded.maxPage)
                withContext(Dispatchers.Main) {
                    processDiskCachePage(pageNum, loaded.html, maxPageCalculated)
                }
            } catch (e: Exception) {
                Log.e(logTag, "DiskCache: Error loading page $pageNum", e)
                withContext(Dispatchers.Main) { processDiskCachePage(pageNum, "", 1) }
            }
        }
    }

    private suspend fun processDiskCachePage(pageNum: Int, html: String, maxPage: Int) {
        if (!_isDiskCaching.value || !diskCacheQueue.contains(pageNum)) return
        if (!isDiskCacheHtmlValid(html)) {
            val currentRetries = diskCacheRetries.getOrDefault(pageNum, 0)
            if (currentRetries < MAX_CACHE_RETRIES) {
                diskCacheRetries[pageNum] = currentRetries + 1
                Log.w(logTag, "DiskCache: Retry $pageNum (${currentRetries + 1}/${MAX_CACHE_RETRIES + 1})")
                delay(CACHE_RETRY_DELAY_MS)
                if (_isDiskCaching.value && diskCacheQueue.contains(pageNum)) {
                    loadPageForDiskCache(pageNum)
                }
            } else {
                Log.e(logTag, "DiskCache: Give up $pageNum after max retries")
                diskCacheRetries.remove(pageNum)
                _cachedPages.value -= pageNum
                diskCacheQueue.remove(pageNum)
                loadNextPageForDiskCache(false)
            }
            return
        }
        diskCacheRetries.remove(pageNum)

        val cacheData = CacheData(
            cachedPageNum = pageNum,
            htmlContent = html,
            maxPageNum = maxPage,
            authorId = currentAuthorId,
            contentVersion = AuthenticatedThreadPageLoader.CONTENT_VERSION
        )
        withContext(Dispatchers.IO) { localCache.savePage(url, pageNum, cacheData, diskCacheIncludeImages, cacheTitleForDisk()) }
        CacheUtil.saveCache(url, cacheData)

        withContext(Dispatchers.Main) {
            _cachedPages.value += pageNum
            diskCacheQueue.remove(pageNum)
            loadNextPageForDiskCache(false)
        }
    }

    private fun loadNextPageForDiskCache(showInitialProgress: Boolean = true) {
        if (!_isDiskCaching.value || diskCacheQueue.isEmpty()) {
            _isDiskCaching.value = false
            if (currentCacheSessionShowsProgress && _cacheProgress.value != null && _cacheProgress.value?.isComplete == false) {
                _cacheProgress.value = _cacheProgress.value?.copy(currentPage = diskCacheTotalPages, isComplete = true)
            }
            return
        }
        val pageNum = diskCacheQueue.first()
        diskCacheCurrentPage++
        if (currentCacheSessionShowsProgress) {
            val shouldShow = if (diskCacheCurrentPage == 1) showInitialProgress else true
            if (shouldShow) {
                _cacheProgress.value = CacheProgress(
                    totalPages = diskCacheTotalPages,
                    currentPage = diskCacheCurrentPage,
                    currentPageNum = pageNum
                )
            }
        }

        viewModelScope.launch {
            val memoryCacheData = getMemoryCacheCompat(pageNum).takeIf(::isCurrentReaderCache)

            if (memoryCacheData != null) {
                withContext(Dispatchers.IO) {
                    localCache.savePage(
                        novelUrl = url,
                        pageNum = pageNum,
                        data = memoryCacheData,
                        hasImages = diskCacheIncludeImages,
                        title = cacheTitleForDisk()
                    )
                }

                _cachedPages.value += pageNum
                diskCacheQueue.remove(pageNum)
                loadNextPageForDiskCache(false)
            } else {
                loadPageForDiskCache(pageNum)
            }
        }
    }

    // ==================== 阅读核心逻辑 ====================

    fun firstLoad(initUrl: String, initHeight: Dp, initWidth: Dp) {
        pageEnterTime = System.currentTimeMillis()
        viewModelScope.launch {
            val pageMatch = Regex("(?<=[?&])page=(\\d+)").find(initUrl)
            val initialPageNum = pageMatch?.groupValues?.get(1)?.toIntOrNull()

            val authorIdMatch = Regex("(?<=[?&])authorid=(\\d+)").find(initUrl)
            if (authorIdMatch != null && currentAuthorId == null) {
                currentAuthorId = authorIdMatch.groupValues[1]
            }

            var cleanUrl = initUrl.substringBefore("?")
            val queryString = initUrl.substringAfter("?", "")
            if (queryString.isNotEmpty()) {
                val keptParams = queryString.split("&").filter { param ->
                    val key = param.substringBefore("=")
                    key != "page" && key != "authorid"
                }
                if (keptParams.isNotEmpty()) {
                    cleanUrl += "?" + keptParams.joinToString("&")
                }
            }
            rawReaderUrl = cleanUrl
            url = FavoriteUtil.normalizeUrl(cleanUrl)
            maxWidth = initWidth
            maxHeight = initHeight

            // 切换书籍：重置全书目录索引状态。
            globalIndexCollectorJob?.cancel()
            globalIndexCollectorJob = null
            globalIndexUrl = null
            pageChapterTitles.clear()
            automaticCacheMaxPage = 0
            _uiState.value = _uiState.value.copy(
                globalChapters = emptyList(),
                globalChapterIndexing = false
            )

            updateCachedPagesFromIndex(localCache.index.value)

            val applySettingsAndLoad = { settings: ReaderSettings? ->
                val savedFontSize = ReaderSpacingOptions.snapFontSize(
                    settings?.fontSizePx
                        ?.let { ValueUtil.pxToSp(it) }
                        ?.value
                        ?: 24f
                )
                val savedLineHeight = ReaderSpacingOptions.snapLineHeight(
                    value = settings?.lineHeightPx
                        ?.let { ValueUtil.pxToSp(it) }
                        ?.value
                        ?: 34.56f,
                    fontSize = savedFontSize
                )
                _uiState.value = _uiState.value.copy(
                    fontSize = savedFontSize.sp,
                    lineHeight = savedLineHeight.sp,
                    padding = ReaderSpacingOptions.snapPadding(
                        settings?.paddingDp ?: 16f
                    ).dp,
                    nightMode = false,
                    backgroundColor = null,
                    loadImages = settings?.loadImages ?: false,
                    isVerticalMode = settings?.isVerticalMode ?: false,
                    translationMode = LanguageModeUtil.readerTranslationMode(GlobalData.languageMode.value),
                    fontFamily = 0
                )
                updateFontRatios()
                loadWithSettings(initialPageNum)
            }

            SettingsUtil.getSettings(
                callback = { settings -> applySettingsAndLoad(settings) },
                onNull = { applySettingsAndLoad(null) }
            )
            viewModelScope.launch {
                FavoriteUtil.getFavoriteFlow().collect { favorites ->
                    val normalizedUrl = FavoriteUtil.normalizeUrl(url)
                    val isFavorited = favorites.any { it.url == normalizedUrl }
                    _uiState.value = _uiState.value.copy(isFavorited = isFavorited)
                }
            }
        }
    }

    private fun loadWithSettings(initialPageNum: Int? = null) {
        viewModelScope.launch {
            if (!initialized) showLoadingScrim = true
            val favMap = FavoriteUtil.getFavoriteMapSuspend()
            val favorite = favMap[url]
            favorite?.let {
                currentThreadTitle = it.title
                currentThreadSection = when (it.sourceFid) {
                    "49" -> "文学区"
                    "55" -> "轻小说/译文区"
                    "60" -> "TXT小说区"
                    else -> currentThreadSection
                }
            }
            val targetView = favorite?.lastView ?: initialPageNum ?: 1
            if (favorite?.authorId != null) currentAuthorId = favorite.authorId

            val targetIndex: Int = if (_uiState.value.isVerticalMode) {
                val avgItemsPerPage = getAvgItemsPerHorizontalPage()
                ((favorite?.lastPage ?: 0) * avgItemsPerPage)
            } else {
                favorite?.lastPage ?: 0
            }

            val localData = withContext(Dispatchers.IO) {
                val cached = localCache.loadPageCompat(
                    primaryUrl = url,
                    pageNum = targetView,
                    aliasUrls = readerIdentityAliases()
                )
                if (cached != null && !isCurrentReaderCache(cached)) {
                    localCache.deletePageCompat(
                        primaryUrl = url,
                        pageNum = targetView,
                        aliasUrls = readerIdentityAliases()
                    )
                    null
                } else {
                    cached
                }
            }

            if (localData != null) {
                if (localData.authorId == currentAuthorId || currentAuthorId == null) {
                    if (currentAuthorId == null && localData.authorId != null) {
                        currentAuthorId = localData.authorId
                    }

                    _uiState.value = _uiState.value.copy(
                        currentView = targetView,
                        maxWebView = localData.maxPageNum
                    )

                    loadFinished(
                        success = true,
                        html = localData.htmlContent,
                        loadedUrl = null,
                        maxPage = localData.maxPageNum,
                        isFromCache = true,
                        cacheTargetIndex = targetIndex,
                        targetView = targetView
                    )
                    return@launch
                }
            }

            // 内存缓存
            val memData = getMemoryCacheCompat(targetView).takeIf(::isCurrentReaderCache)
            if (memData != null && (memData.authorId == currentAuthorId || currentAuthorId == null)) {
                if (currentAuthorId == null && memData.authorId != null) {
                    currentAuthorId = memData.authorId
                }

                _uiState.value = _uiState.value.copy(
                    currentView = targetView,
                    maxWebView = memData.maxPageNum
                )

                loadFinished(
                    success = true,
                    html = memData.htmlContent,
                    loadedUrl = null,
                    maxPage = memData.maxPageNum,
                    isFromCache = true,
                    cacheTargetIndex = targetIndex,
                    targetView = targetView
                )
                return@launch
            }

            // 如果 ReaderWebPage 已经在静默预热同一页，继承那次发射结果，避免 FAB 返回后再开第二个网络请求。
            val prewarmData = awaitInFlightMemoryPrewarm(targetView).takeIf(::isCurrentReaderCache)
            if (prewarmData != null && (prewarmData.authorId == currentAuthorId || currentAuthorId == null)) {
                if (currentAuthorId == null && prewarmData.authorId != null) {
                    currentAuthorId = prewarmData.authorId
                }

                _uiState.value = _uiState.value.copy(
                    currentView = targetView,
                    maxWebView = prewarmData.maxPageNum
                )

                loadFinished(
                    success = true,
                    html = prewarmData.htmlContent,
                    loadedUrl = null,
                    maxPage = prewarmData.maxPageNum,
                    isFromCache = true,
                    cacheTargetIndex = targetIndex,
                    targetView = targetView
                )
                return@launch
            }

            // 网络加载
            _uiState.value = _uiState.value.copy(currentView = targetView, initPage = targetIndex)
            startNetworkLoad(targetView)
        }
    }

    // ==================== 页面切换 (onSetView) ====================
    fun onSetView(view: Int, forceReload: Boolean = false) {
        if (view == _uiState.value.currentView && !isTransitioning && !forceReload) return

        val previousView = _uiState.value.currentView
        val previousPage = latestPage

        if (initialized) {
            saveHistory(
                pageToSave = previousPage,
                webViewToSave = previousView
            )
        }

        loadJob?.cancel()
        loadRequestId++
        val thisRequestId = loadRequestId

        // 预加载命中
        if (view == _uiState.value.currentView + 1 && nextHtmlList != null && !forceReload) {
            isTransitioning = true
            _uiState.value = _uiState.value.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentView = view
            )
            _currentPercentage.value = 0f
            currentRawHtml = nextRawHtml ?: currentRawHtml
            nextHtmlList = null
            nextChapterList = null
            nextRawHtml = null
            latestPage = 0
            showLoadingScrim = false

            saveHistory(
                pageToSave = 0,
                webViewToSave = view
            )

            return
        }

        // 清理预加载状态
        nextHtmlList = null
        nextChapterList = null
        nextRawHtml = null
        isPreloading = false

        if (forceReload) {
            CacheUtil.clearCacheEntryCompat(
                primaryUrl = url,
                pageNum = view,
                aliasUrls = readerIdentityAliases()
            )
        }

        // 立即更新 currentView 并显示遮罩
        _uiState.value = _uiState.value.copy(currentView = view, isError = false)
        _currentPercentage.value = 0f
        isTransitioning = true
        showLoadingScrim = true

        loadJob = viewModelScope.launch(Dispatchers.Main) {
            val localData = withContext(Dispatchers.IO) {
                val cached = localCache.loadPageCompat(
                    primaryUrl = url,
                    pageNum = view,
                    aliasUrls = readerIdentityAliases()
                )
                if (cached != null && !isCurrentReaderCache(cached)) {
                    localCache.deletePageCompat(
                        primaryUrl = url,
                        pageNum = view,
                        aliasUrls = readerIdentityAliases()
                    )
                    null
                } else {
                    cached
                }
            }

            if (thisRequestId != loadRequestId) return@launch

            if (localData != null && (localData.authorId == currentAuthorId || currentAuthorId == null)) {
                if (currentAuthorId == null && localData.authorId != null) {
                    currentAuthorId = localData.authorId
                }

                _uiState.value = _uiState.value.copy(
                    initPage = 0,
                    maxWebView = localData.maxPageNum
                )

                loadFinished(
                    success = true,
                    html = localData.htmlContent,
                    loadedUrl = null,
                    maxPage = localData.maxPageNum,
                    isFromCache = true,
                    cacheTargetIndex = 0,
                    targetView = view
                )
                return@launch
            }

            val memData = getMemoryCacheCompat(view).takeIf(::isCurrentReaderCache)
            if (thisRequestId != loadRequestId) return@launch
            if (memData != null && (memData.authorId == currentAuthorId || currentAuthorId == null)) {
                if (currentAuthorId == null && memData.authorId != null) {
                    currentAuthorId = memData.authorId
                }

                _uiState.value = _uiState.value.copy(
                    initPage = 0,
                    maxWebView = memData.maxPageNum
                )

                loadFinished(
                    success = true,
                    html = memData.htmlContent,
                    loadedUrl = null,
                    maxPage = memData.maxPageNum,
                    isFromCache = true,
                    cacheTargetIndex = 0,
                    targetView = view
                )
                return@launch
            }

            // 如果 ReaderWebPage 的静默预热还在进行中，等待并复用它的结果。
            val prewarmData = awaitInFlightMemoryPrewarm(view).takeIf(::isCurrentReaderCache)
            if (thisRequestId != loadRequestId) return@launch
            if (prewarmData != null && (prewarmData.authorId == currentAuthorId || currentAuthorId == null)) {
                if (currentAuthorId == null && prewarmData.authorId != null) {
                    currentAuthorId = prewarmData.authorId
                }

                _uiState.value = _uiState.value.copy(
                    initPage = 0,
                    maxWebView = prewarmData.maxPageNum
                )

                loadFinished(
                    success = true,
                    html = prewarmData.htmlContent,
                    loadedUrl = null,
                    maxPage = prewarmData.maxPageNum,
                    isFromCache = true,
                    cacheTargetIndex = 0,
                    targetView = view
                )
                return@launch
            }

            // 网络加载前清空旧正文，避免弱网下"旧页内容 + loading"误导用户。
            if (thisRequestId != loadRequestId) return@launch
            _uiState.value = _uiState.value.copy(
                htmlList = emptyList(),
                chapterList = emptyList(),
                initPage = 0,
                isError = false
            )

            try {
                val (html, maxPage, title) = loadFromApiBounded(view)
                if (thisRequestId != loadRequestId) return@launch
                loadFinished(success = true, html = html, loadedUrl = null, maxPage = maxPage,
                    title = title, isFromCache = false, targetView = view)
            } catch (e: CancellationException) {
                // 协程被取消，不处理
            } catch (e: Exception) {
                if (thisRequestId != loadRequestId) return@launch
                loadFinished(success = false, html = "", loadedUrl = null, maxPage = 1,
                    isFromCache = false, targetView = view)
            }
        }
    }

    private suspend fun loadFromApi(view: Int): Triple<String, Int, String?> {
        val loaded = AuthenticatedThreadPageLoader.loadReaderPage(
            tid = extractTid(),
            page = view,
            authorIdHint = currentAuthorId,
            context = applicationContext
        )
        if (currentAuthorId != loaded.authorId) {
            currentAuthorId = loaded.authorId
            viewModelScope.launch(Dispatchers.IO) {
                val map = FavoriteUtil.getFavoriteMapSuspend()
                map[url]?.let {
                    FavoriteUtil.updateFavoriteSuspend(it.copy(authorId = loaded.authorId))
                }
            }
        }
        currentThreadTitle = loaded.title?.takeIf(String::isNotBlank) ?: currentThreadTitle
        currentThreadAuthor = loaded.author.orEmpty().ifBlank { currentThreadAuthor }
        currentThreadSection = loaded.section?.takeIf(String::isNotBlank) ?: currentThreadSection
        maxPageCalculated = maxOf(maxPageCalculated, loaded.maxPage)

        CacheUtil.saveCache(
            url,
            CacheData(
                cachedPageNum = view,
                htmlContent = loaded.html,
                maxPageNum = maxPageCalculated,
                authorId = loaded.authorId,
                contentVersion = AuthenticatedThreadPageLoader.CONTENT_VERSION
            )
        )
        val title = currentThreadTitle
        return Triple(loaded.html, maxPageCalculated, title)
    }

    private suspend fun loadFromApiBounded(view: Int): Triple<String, Int, String?> {
        var lastError: Throwable? = null

        repeat(READER_API_MAX_ATTEMPTS) { attempt ->
            try {
                return withTimeout(READER_API_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        loadFromApi(view)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }

            if (attempt < READER_API_MAX_ATTEMPTS - 1) {
                delay(READER_API_RETRY_DELAY_MS)
            }
        }

        throw IOException("Reader API load failed or timed out: page=$view", lastError)
    }

    fun loadFinished(
        success: Boolean,
        html: String,
        loadedUrl: String?,
        maxPage: Int,
        title: String? = null,
        isFromCache: Boolean = false,
        cacheTargetIndex: Int = 0,
        targetView: Int = _uiState.value.currentView
    ) {
        if (success) currentRawHtml = html

        viewModelScope.launch {
            if (!initialized) {
                val elapsed = System.currentTimeMillis() - pageEnterTime
                if (elapsed < 350L) delay(350L - elapsed)
            }

            if (!success) {
                _uiState.value = _uiState.value.copy(isError = true, htmlList = emptyList(), maxWebView = maxPage)
                showLoadingScrim = false
                isTransitioning = false
                return@launch
            }

            if (!isFromCache) {
                _uiState.value = _uiState.value.copy(maxWebView = maxPage)
                FavoriteUtil.checkAndUpdateTitleSuspend(url, title)
            }

            val (passages, chapters) = withContext(readerLayoutDispatcher) {
                parseHtmlToContent(html, targetView)
                paginateContent(isFromCache, targetView)
            }

            recordPageChapters(targetView, chapters)

            if (isPreloading) {
                if (targetView != viewBeingPreloaded) {
                    Log.w(logTag, "Preload mismatch: expected $viewBeingPreloaded, got $targetView")
                    return@launch
                }
                isPreloading = false
                val cacheData = CacheData(
                    cachedPageNum = targetView,
                    htmlContent = html,
                    maxPageNum = maxPage,
                    authorId = currentAuthorId,
                    contentVersion = AuthenticatedThreadPageLoader.CONTENT_VERSION
                )
                CacheUtil.saveCache(url, cacheData)
                if (_cachedPages.value.contains(targetView)) {
                    launch(Dispatchers.IO) { localCache.savePage(url, targetView, cacheData, false, cacheTitleForDisk()) }
                }
                nextHtmlList = passages
                nextChapterList = chapters
                nextRawHtml = html
                val currentList = _uiState.value.htmlList
                if (currentList.isNotEmpty()) {
                    val modified = currentList.dropLast(1).toMutableList().also {
                        it.add(Content("...下一页 (网页)", ContentType.TEXT, "footer"))
                    }
                    _uiState.value = _uiState.value.copy(htmlList = modified)
                }
            } else {
                // 正常加载
                if (!isFromCache) {
                    val cacheData = CacheData(
                        cachedPageNum = targetView,
                        htmlContent = html,
                        maxPageNum = maxPage,
                        authorId = currentAuthorId,
                        contentVersion = AuthenticatedThreadPageLoader.CONTENT_VERSION
                    )
                    CacheUtil.saveCache(url, cacheData)
                    if (_cachedPages.value.contains(targetView)) {
                        launch(Dispatchers.IO) { localCache.savePage(url, targetView, cacheData, false, cacheTitleForDisk()) }
                    }
                }

                val newInitPage = if (isFromCache) cacheTargetIndex else if (initialized) 0 else _uiState.value.initPage
                val totalItems = passages.size.coerceAtLeast(1)
                val safeInitPage = newInitPage.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                val newPercent = (safeInitPage.toFloat() / totalItems) * 100f

                ignoreFirstFakeZero = safeInitPage > 0
                _uiState.value = _uiState.value.copy(
                    htmlList = passages,
                    chapterList = chapters,
                    initPage = safeInitPage,
                    maxWebView = maxPage,
                    isError = false
                )
                _currentPercentage.value = newPercent
                if (!initialized) initialized = true
                latestPage = safeInitPage
                showLoadingScrim = false
                isTransitioning = false
                startAutomaticCachingIfReady(maxPage)
                recordOpenedThread(title)

                if (initialized) {
                    saveHistory(
                        pageToSave = safeInitPage,
                        webViewToSave = targetView
                    )
                }
            }
        }
    }

    private suspend fun recordOpenedThread(title: String?) {
        val resolvedTitle = title
            ?.takeIf(String::isNotBlank)
            ?: currentThreadTitle?.takeIf(String::isNotBlank)
            ?: externalCacheTitle?.takeIf(String::isNotBlank)
            ?: return
        currentThreadTitle = resolvedTitle

        withContext(Dispatchers.IO) {
            HistoryUtil.addOrUpdateHistory(
                url = ReaderReturnBridge.toAbsoluteBbsUrl(url),
                title = resolvedTitle,
                author = currentThreadAuthor,
                section = currentThreadSection
            )
        }
    }

    fun retryLoad() {
        loadJob?.cancel()
        showLoadingScrim = true
        _uiState.value = _uiState.value.copy(isError = false)
        startNetworkLoad(_uiState.value.currentView)
    }

    fun forceRefreshCurrentPage() {
        val pageToRefresh = _uiState.value.currentView
        val readerPageToRestore = latestPage.coerceAtLeast(0)
        if (url.isBlank()) return
        viewModelScope.launch {
            nextHtmlList = null
            nextChapterList = null
            nextRawHtml = null
            isPreloading = false
            layoutCache.evictAll()
            showLoadingScrim = true
            _uiState.value = _uiState.value.copy(isError = false)
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                delay(10)
                try {
                    val (html, maxPage, title) = loadFromApiBounded(pageToRefresh)
                    loadFinished(
                        success = true,
                        html = html,
                        loadedUrl = null,
                        maxPage = maxPage,
                        title = title,
                        isFromCache = true,
                        cacheTargetIndex = readerPageToRestore,
                        targetView = pageToRefresh
                    )
                } catch (e: Exception) {
                    loadFinished(false, "", null, 1, targetView = pageToRefresh)
                }
            }
        }
    }

    private fun startNetworkLoad(view: Int) {
        loadJob?.cancel()
        loadRequestId++
        val thisRequestId = loadRequestId
        showLoadingScrim = true

        loadJob = viewModelScope.launch {
            try {
                val prewarmData = awaitInFlightMemoryPrewarm(view)
                if (thisRequestId != loadRequestId) return@launch
                if (prewarmData != null && (prewarmData.authorId == currentAuthorId || currentAuthorId == null)) {
                    if (currentAuthorId == null && prewarmData.authorId != null) {
                        currentAuthorId = prewarmData.authorId
                    }
                    _uiState.value = _uiState.value.copy(maxWebView = prewarmData.maxPageNum)
                    loadFinished(
                        success = true,
                        html = prewarmData.htmlContent,
                        loadedUrl = null,
                        maxPage = prewarmData.maxPageNum,
                        isFromCache = true,
                        cacheTargetIndex = 0,
                        targetView = view
                    )
                    return@launch
                }

                val (html, maxPage, title) = loadFromApiBounded(view)
                if (thisRequestId != loadRequestId) return@launch
                loadFinished(success = true, html = html, loadedUrl = null, maxPage = maxPage,
                    title = title, targetView = view)
            } catch (e: CancellationException) {
                // 忽略
            } catch (e: Exception) {
                if (thisRequestId != loadRequestId) return@launch
                loadFinished(success = false, html = "", loadedUrl = null, maxPage = 1, targetView = view)
            }
        }
    }

    private fun triggerPreload(targetView: Int, maxView: Int) {
        if (isPreloading || targetView > maxView || _isDiskCaching.value) return
        isPreloading = true
        viewBeingPreloaded = targetView
        nextRawHtml = null
        viewModelScope.launch {
            try {
                val authorId = currentAuthorId ?: return@launch
                val loaded = AuthenticatedThreadPageLoader.loadReaderPage(
                    tid = extractTid(),
                    page = targetView,
                    authorIdHint = authorId,
                    context = applicationContext
                )
                maxPageCalculated = maxOf(maxPageCalculated, loaded.maxPage)
                val combinedHtml = loaded.html

                CacheUtil.saveCache(
                    url,
                    CacheData(
                        cachedPageNum = targetView,
                        htmlContent = combinedHtml,
                        maxPageNum = maxPageCalculated,
                        authorId = loaded.authorId,
                        contentVersion = AuthenticatedThreadPageLoader.CONTENT_VERSION
                    )
                )

                val (passages, chapters) = withContext(readerLayoutDispatcher) {
                    val preloadContent = parseHtmlToContentList(
                        html = combinedHtml,
                        translationMode = _uiState.value.translationMode,
                        loadImages = _uiState.value.loadImages
                    )
                    paginateContent(
                        isFromCache = false,
                        targetWebPage = targetView,
                        contentOverride = preloadContent,
                        rawVersionOverride = preloadRawVersion(targetView, combinedHtml)
                    )
                }

                nextHtmlList = passages
                nextChapterList = chapters
                nextRawHtml = combinedHtml

                recordPageChapters(targetView, chapters)

                val currentList = _uiState.value.htmlList
                if (currentList.isNotEmpty()) {
                    val modified = currentList.dropLast(1).toMutableList().also {
                        it.add(Content("...下一页 (网页)", ContentType.TEXT, "footer"))
                    }
                    _uiState.value = _uiState.value.copy(htmlList = modified)
                }
            } catch (_: Exception) {
            } finally {
                isPreloading = false
            }
        }
    }

    private fun extractTid(): String {
        return url.substringAfter("tid=").substringBefore("&")
    }

    private fun parsedRawVersion(
        targetView: Int,
        html: String,
        translationMode: Int,
        loadImages: Boolean
    ): Long {
        var result = targetView.toLong()
        result = result * 31 + html.length
        result = result * 31 + html.hashCode()
        result = result * 31 + translationMode
        result = result * 31 + if (loadImages) 1 else 0
        return result
    }

    private fun parseHtmlToContent(
        html: String,
        targetView: Int = _uiState.value.currentView
    ) {
        val state = _uiState.value
        val cacheKey = ParsedContentCacheKey(
            url = url,
            webPage = targetView,
            htmlLength = html.length,
            htmlHash = html.hashCode(),
            translationMode = state.translationMode,
            loadImages = state.loadImages
        )

        val parsed = parsedContentCache.get(cacheKey) ?: parseHtmlToContentList(
            html = html,
            translationMode = state.translationMode,
            loadImages = state.loadImages
        ).also { parsedContentCache.put(cacheKey, it) }

        rawContentList.clear()
        rawContentList.addAll(parsed)
        rawContentListVersion = parsedRawVersion(
            targetView = targetView,
            html = html,
            translationMode = state.translationMode,
            loadImages = state.loadImages
        )
    }

    // 「Episode N / EP N / Chapter N」式分话标记：部分译者（如《一周一次买下同班同学》《如果你愿意成为我的朋友》）
    // 用英文标记分话，既不是「第N话」也不是居中大字号，原先识别不到。这类楼层与已识别到的中文标题（如「序章」）
    // 混排时，会因没有自己的标题而全部并入上一章，导致目录只剩第一章。标记可能在楼层正文首行，也可能紧跟在一行
    // 子标题之后（如先一行「仙台同学的价格正好五千元」再一行「Episode 1」），故扫描正文前几行。
    private val episodeHeadingRegex = Regex(
        """^(?:episode|ep|chapter)\b\s*\.?\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 扫描楼层正文前若干行，提取「Episode N」式分话标记，归一化为「Episode N」（取不到返回 null）。
     * 扫 8 行而非 3 行：部分楼层在 episode 标记前会先放几行译者注/七夕彩蛋/分隔线
     * （如《同班同学》episode 155 前有两行注释加一行「---」）。正文段落带缩进、不会以 episode 开头，
     * 故多扫几行仍安全。
     */
    private fun extractEpisodeHeading(postText: String): String? {
        postText.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .take(8)
            .forEach { line ->
                episodeHeadingRegex.find(line)?.let { match ->
                    return "Episode ${match.groupValues[1]}"
                }
            }
        return null
    }

    // 引导标题栏检测用：遇到这些标签视为「正文/换行开始」，标题段到此结束。
    private val titleBarBreakTags = setOf(
        "div", "p", "br", "table", "ul", "ol", "blockquote", "center", "hr", "img"
    )
    // 标题不应以引号/括号/项目符号开头（那是对话或正文），也不应以句末标点结尾。
    private val titleBarLeadingDeny = "「『（(《〈【[“\"'*—-…~·".toSet()
    private val titleBarTrailingDeny = "。！？!?".toSet()

    /**
     * 提取楼层「引导标题栏」：作者把分话标题写在正文第一段（第一个 div/p/br 等块级中断）之前，
     * 常见为 <strong>夏梦4</strong>、<font size=3>望春 5</font>、秋惑3、即使没有羽翼 1 等短中文标题。
     * 这类标题字号多为 3，既非居中也非大字号，extractStructuralHeading 认不到；写法又有「加粗/不加粗、
     * 与正文相连/<br> 分隔」多种变体，纯靠 firstLine 也不稳。这里按 DOM 取「首个块级中断前的短文本」统一覆盖。
     * 仅当引导段「短且不像正文/对话」时才返回，避免把正文首句误判成标题。
     */
    private fun extractLeadingTitleBar(node: org.jsoup.nodes.Element): String? {
        val sb = StringBuilder()
        for (child in node.childNodes()) {
            when (child) {
                is org.jsoup.nodes.TextNode -> sb.append(child.text())
                is org.jsoup.nodes.Element -> {
                    if (child.tagName().lowercase() in titleBarBreakTags) {
                        // i.pstatus 删除后开头可能残留 <br>，标题在其后，先跳过。
                        if (sb.isBlank() && child.tagName().lowercase() == "br") continue
                        return finalizeTitleBar(sb.toString())
                    }
                    // 购买、引用和楼层导航链接不是正文标题，不能把链接文字收进章节目录。
                    if (isReaderLinkedTitleElement(child)) return null
                    // 其余内联元素（strong/b/font/i/span 等）取其文本继续累积。
                    sb.append(child.text())
                }
                else -> {}
            }
            // 引导段过长说明这是正文而非标题，放弃。
            if (sb.length > 40) return null
        }
        return finalizeTitleBar(sb.toString())
    }

    private fun finalizeTitleBar(raw: String): String? {
        val title = raw.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        val cleaned = title.replace(Regex("^[#＃]+\\s*"), "").trim()
        if (cleaned.isEmpty() || cleaned.length > 24) return null
        if (cleaned.first() in titleBarLeadingDeny) return null
        if (
            cleaned.last() in titleBarTrailingDeny ||
            endsWithReaderTitleContinuationPunctuation(cleaned)
        ) {
            return null
        }
        return cleaned
    }

    private fun convertChineseIfNeeded(text: String, translationMode: Int): String {
        if (text.isBlank()) return text
        return when (translationMode) {
            1 -> ChineseConvertUtil.toSimplified(text, applicationContext)
            2 -> ChineseConvertUtil.toTraditional(text, applicationContext)
            else -> text
        }
    }

    private fun parseHtmlToContentList(
        html: String,
        translationMode: Int,
        loadImages: Boolean
    ): List<Content> {
        val result = ArrayList<Content>()
        val doc = Jsoup.parse(html)
        // 只删「本帖最后由…编辑」这类编辑提示（i.pstatus），不要删所有 <i>：
        // 部分作者把章节标题写成斜体 <i>第二章…</i>，删光 <i> 会连标题一起丢，导致目录缺章。
        doc.select("i.pstatus").forEach { it.remove() }

        val messageNodes = doc.getElementsByClass("message")
        if (messageNodes.isEmpty()) return result

        val threadId = extractTid()
        val linkedDirectoryMessages = messageNodes.map { node ->
            isReaderLinkedChapterDirectory(node, threadId)
        }
        val rawTexts = messageNodes.map { node ->
            HTMLUtil.toText(markReaderEmbeddedChapterHeadings(node, threadId).html())
        }
        val delimiter = "|||YAMIBO_SEP|||"
        val combinedText = rawTexts.joinToString(delimiter)

        val convertedCombinedText = if (combinedText.isNotBlank()) {
            when (translationMode) {
                1 -> ChineseConvertUtil.toSimplified(combinedText, applicationContext)
                2 -> ChineseConvertUtil.toTraditional(combinedText, applicationContext)
                else -> combinedText
            }
        } else {
            combinedText
        }

        val convertedTexts = convertedCombinedText.split(delimiter)
        val chapterSegments = convertedTexts.map(::splitReaderEmbeddedChapterSegments)
        val firstSegmentedMessageIndex = chapterSegments.indexOfFirst { segments ->
            containsReaderChapterStart(segments)
        }
        val replyRegex = Regex("发表于\\s*\\d{4}-\\d{1,2}-\\d{1,2}")

        val firstLines = messageNodes.indices.map { i ->
            (convertedTexts.getOrElse(i) { rawTexts[i] })
                .lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        }
        val dividerHeadings = messageNodes.mapIndexed { index, node ->
            if (linkedDirectoryMessages[index]) {
                null
            } else {
                extractReaderChapterHeadingAfterDivider(node)
                    ?.let { convertChineseIfNeeded(it, translationMode) }
            }
        }
        val usesDividerChapterHeadings = dividerHeadings.any { it != null }

        // 多种标题信号共同识别真正的章节小标题（Pair 的第二个值表示是否「硬分章」）。
        // 命中的楼层才开启新章节，其余楼层视为上一章延续，不再把简介/闲聊/致谢/正文句当成章节。
        // 整帖都识别不到时，回退到旧的「取楼层首行」逻辑，兼容普通排版的小说。
        val detectedHeadings: List<Pair<String, Boolean>?> = messageNodes.indices.map { i ->
            if (linkedDirectoryMessages[i]) return@map null
            val firstLine = firstLines[i]
            if (firstLine.contains(replyRegex)) return@map null
            val textHeading = extractReaderTextChapterHeading(firstLine)
            val dividerHeading = dividerHeadings[i]
            when {
                // 1) 作者留言后的分隔线正文：优先取分隔线后的正式编号标题。
                dividerHeading != null -> dividerHeading.take(30) to true
                // 2) 「第N章/序章…」编号标题：硬分章，每次出现都是新章。
                textHeading != null -> textHeading.take(30) to true
                // 3) 「现在 - 2044 年 1 月」这类带完整日期的小标题：作者按此分章，硬分章。
                else -> {
                    val dateHeading = extractReaderDateSubHeading(messageNodes[i])
                    if (dateHeading != null) {
                        convertChineseIfNeeded(dateHeading, translationMode) to true
                    } else {
                        // 4) 「Episode N」式英文分话标记：每话一个楼层，硬分章。
                        val episodeHeading = extractEpisodeHeading(
                            convertedTexts.getOrElse(i) { rawTexts[i] }
                        )
                        if (episodeHeading != null) {
                            episodeHeading to true
                        } else {
                            // 5) 引导式中文标题栏（<strong>夏梦4</strong>/<font>秋惑3</font> 等，字号非 5/6/7）：
                            //    软标题，连续同名才合并。若同页已经采用“留言 + 分隔线 + 正文标题”的排版，
                            //    则不再拿其它楼层的首句兜底，避免把致谢或更新说明收进目录。
                            val titleBar = if (usesDividerChapterHeadings) {
                                null
                            } else {
                                extractLeadingTitleBar(messageNodes[i])
                            }
                            if (titleBar != null) {
                                convertChineseIfNeeded(titleBar, translationMode) to false
                            } else {
                                // 6) 居中/大字号的装饰性标题：软标题，连续同名才合并。
                                extractReaderStructuralHeading(messageNodes[i])
                                    ?.let { convertChineseIfNeeded(it, translationMode) to false }
                            }
                        }
                    }
                }
            }
        }
        val useDetectedHeadings = detectedHeadings.any { it != null } ||
            firstSegmentedMessageIndex >= 0

        var currentValidTitle: String? = null

        for (i in messageNodes.indices) {
            val node = messageNodes[i]
            val firstLine = firstLines[i]

            if (firstLine.contains(replyRegex)) continue

            // 作品存在楼层内分段章节时，章节开始之前的独立介绍标题不属于正文目录；
            // 同一楼层里的作品标题仍保留，并从其中首个分段标题开始切出正文第一章。
            val suppressPrefaceHeading = firstSegmentedMessageIndex >= 0 &&
                i < firstSegmentedMessageIndex &&
                detectedHeadings[i]?.second == false

            val messageSegments = chapterSegments.getOrElse(i) {
                splitReaderEmbeddedChapterSegments(
                    convertedTexts.getOrElse(i) { rawTexts.getOrElse(i) { "" } }
                )
            }
            val messageHasSegmentedChapter = containsReaderChapterStart(messageSegments)
            messageSegments.forEachIndexed { segmentIndex, segment ->
                var isHardStart = false
                if (segment.title != null) {
                    currentValidTitle = segment.title
                    isHardStart = true
                } else if (segmentIndex == 0 && !messageHasSegmentedChapter) {
                    if (useDetectedHeadings) {
                        if (suppressPrefaceHeading) {
                            currentValidTitle = null
                        } else {
                            // 命中标题的楼层开启新章节，其余楼层沿用上一章标题（不新增目录项）。
                            detectedHeadings[i]?.let { (title, isHard) ->
                                currentValidTitle = title
                                isHardStart = isHard
                            }
                        }
                    } else {
                        currentValidTitle = firstLine.take(30)
                    }
                }

                if (segment.text.isNotBlank()) {
                    result.add(
                        Content(segment.text, ContentType.TEXT, currentValidTitle, chapterStart = isHardStart)
                    )
                }
            }

            if (loadImages) {
                for (element in node.getElementsByTag("img")) {
                    var src = element.attr("zoomfile")
                    if (src.isBlank()) src = element.attr("file")
                    if (src.isBlank()) src = element.attr("src")
                    if (src.isBlank() || src.contains("smiley/")) continue

                    val normalizedSrc = if (!src.startsWith("http://") && !src.startsWith("https://")) {
                        "${RequestConfig.BASE_URL}/${src}"
                    } else {
                        src
                    }
                    result.add(Content(normalizedSrc, ContentType.IMG, currentValidTitle))
                }
            }
        }

        return result
    }

    private fun preloadRawVersion(targetView: Int, html: String): Long {
        val state = _uiState.value
        return parsedRawVersion(
            targetView = targetView,
            html = html,
            translationMode = state.translationMode,
            loadImages = state.loadImages
        )
    }

    private fun paginateContent(
        isFromCache: Boolean = false,
        targetWebPage: Int? = null,
        contentOverride: List<Content>? = null,
        rawVersionOverride: Long? = null
    ): Pair<List<Content>, List<ChapterInfo>> {
        updateFontRatios()
        val contentSnapshot = contentOverride ?: rawContentList.toList()
        val effectiveRawVersion = rawVersionOverride ?: rawContentListVersion
        val state = _uiState.value
        val webPageNum = targetWebPage ?: state.currentView

        val pageContentWidth = maxWidth - (state.padding + state.padding)
        val topPadding = 24.dp
        val footerHeight = 87.dp
        val pageContentHeight = maxHeight - topPadding - footerHeight

        val cacheKey = LayoutCacheKey(
            url = url,
            rawVersion = effectiveRawVersion,
            widthPx = ValueUtil.dpToPx(pageContentWidth).toInt(),
            heightPx = ValueUtil.dpToPx(pageContentHeight).toInt(),
            fontSizePx = ValueUtil.spToPx(state.fontSize).toInt(),
            lineHeightPx = ValueUtil.spToPx(state.lineHeight).toInt(),
            letterSpacingPx = ValueUtil.spToPx(state.letterSpacing).toInt(),
            paddingPx = ValueUtil.dpToPx(state.padding).toInt(),
            isVerticalMode = state.isVerticalMode,
            fontFamily = state.fontFamily,
            translationMode = state.translationMode,
            loadImages = state.loadImages
        )

        val basePassages = layoutCache.get(cacheKey) ?: buildBasePassages(
            contentSnapshot = contentSnapshot,
            state = state,
            pageContentWidth = pageContentWidth,
            pageContentHeight = pageContentHeight
        ).also { layoutCache.put(cacheKey, it) }

        val passages = ArrayList<Content>(basePassages.size + 1).apply {
            addAll(basePassages)
            addDynamicFooterOrPreview(webPageNum, state)
        }

        val chapterList = buildChapterList(passages)
        return Pair(passages, chapterList)
    }

    private fun buildBasePassages(
        contentSnapshot: List<Content>,
        state: ReaderState,
        pageContentWidth: Dp,
        pageContentHeight: Dp
    ): List<Content> {
        return if (state.isVerticalMode) {
            TextUtil.pagingTextVertical(
                rawContentList = contentSnapshot,
                width = pageContentWidth,
                fontSize = state.fontSize,
                letterSpacing = state.letterSpacing,
                charRatios = currentAsciiRatios,
                typeface = typefaceFromMode(state.fontFamily)
            )
        } else {
            val passagesList = ArrayList<Content>()
            for (content in contentSnapshot) {
                when (content.type) {
                    ContentType.TEXT -> {
                        val pagedText = TextUtil.pagingText(
                            content.data,
                            pageContentHeight,
                            pageContentWidth,
                            state.fontSize,
                            state.letterSpacing,
                            state.lineHeight,
                            currentAsciiRatios,
                            typefaceFromMode(state.fontFamily)
                        )
                        // 硬分章标记只放在该楼层分页后的第一片上，后续分页片不再算新章起点。
                        for ((pieceIndex, t) in pagedText.withIndex()) {
                            passagesList.add(
                                Content(
                                    t,
                                    ContentType.TEXT,
                                    content.chapterTitle,
                                    chapterStart = content.chapterStart && pieceIndex == 0
                                )
                            )
                        }
                    }
                    ContentType.IMG -> passagesList.add(content)
                }
            }
            passagesList
        }
    }

    private fun MutableList<Content>.addDynamicFooterOrPreview(webPageNum: Int, state: ReaderState) {
        if (nextHtmlList != null && nextHtmlList!!.isNotEmpty()) {
            add(nextHtmlList!!.first())
        } else if (webPageNum < state.maxWebView) {
            add(Content("正在加载...", ContentType.TEXT, "footer"))
        } else {
            add(Content("刷新本页内容", ContentType.TEXT, "footer"))
        }
    }

    private fun buildChapterList(passages: List<Content>): List<ChapterInfo> {
        val chapterList = mutableListOf<ChapterInfo>()
        var lastTitle: String? = null
        passages.forEachIndexed { index, content ->
            val title = content.chapterTitle
            if (title != null && title != "footer" && (content.chapterStart || title != lastTitle)) {
                // 硬分章（chapterStart）即便与上一章同名也新建目录项，
                // 以支持作者把两个同名「第N章」分楼层发布的情况。
                chapterList.add(ChapterInfo(title = title, startIndex = index))
                lastTitle = title
            }
        }
        return chapterList
    }

    // ==================== 全书章节目录构建 ====================

    /** 从解析后的 Content 列表抽取「按出现顺序的章节标题序列」，与 buildChapterList 的分章规则一致。 */
    private fun chapterTitleSequence(content: List<Content>): List<String> {
        val titles = mutableListOf<String>()
        var lastTitle: String? = null
        for (c in content) {
            val title = c.chapterTitle
            if (title != null && title != "footer" && (c.chapterStart || title != lastTitle)) {
                titles.add(title)
                lastTitle = title
            }
        }
        return titles
    }

    /** 记录某论坛页的章节标题序列并刷新全书目录；同时确保后台索引收集器已启动。 */
    private fun recordPageChapters(page: Int, chapters: List<ChapterInfo>) {
        if (page < 1) return
        val titles = chapters.map { it.title }
        if (pageChapterTitles[page] == titles) {
            ensureGlobalIndexCollector()
            return
        }
        pageChapterTitles[page] = titles
        publishGlobalChapters()
        ensureGlobalIndexCollector()
    }

    /** 把各页章节标题按论坛页升序拍平成全书目录，写入 state。 */
    private fun publishGlobalChapters() {
        val maxPage = _uiState.value.maxWebView.coerceAtLeast(1)
        val global = pageChapterTitles.toSortedMap().flatMap { (page, titles) ->
            titles.mapIndexed { order, title -> GlobalChapter(page, order, title) }
        }
        val indexedPages = pageChapterTitles.keys.count { it in 1..maxPage }
        _uiState.value = _uiState.value.copy(
            globalChapters = global,
            globalChapterIndexing = indexedPages < maxPage
        )
    }

    /**
     * 启动「全书目录」后台收集器：观察本地磁盘缓存（由自动缓存逐页填充），
     * 每当有新论坛页被缓存就解析其章节标题并入全书目录。复用已缓存数据，不额外发网络请求。
     */
    private fun ensureGlobalIndexCollector() {
        if (url.isBlank()) return
        if (globalIndexUrl == url && globalIndexCollectorJob?.isActive == true) return
        globalIndexUrl = url
        globalIndexCollectorJob?.cancel()
        globalIndexCollectorJob = viewModelScope.launch {
            localCache.index.collect {
                indexCachedPages()
            }
        }
    }

    /** 解析所有「已磁盘缓存但尚未入全书目录」的论坛页，补全章节标题。 */
    private suspend fun indexCachedPages() {
        val cachedPages = localCache.getCachedPageNumsCompat(
            primaryUrl = url,
            aliasUrls = readerIdentityAliases()
        )
        val translationMode = _uiState.value.translationMode
        var changed = false
        for (page in cachedPages) {
            if (pageChapterTitles.containsKey(page)) continue
            val data = withContext(Dispatchers.IO) {
                localCache.loadPageCompat(
                    primaryUrl = url,
                    pageNum = page,
                    aliasUrls = readerIdentityAliases()
                )
            }
            if (data == null || !isCurrentReaderCache(data)) continue
            val content = withContext(readerLayoutDispatcher) {
                parseHtmlToContentList(data.htmlContent, translationMode, false)
            }
            pageChapterTitles[page] = chapterTitleSequence(content)
            changed = true
        }
        if (changed) publishGlobalChapters()
    }

    private fun processPageChange(newPage: Int) {
        val oldPage = latestPage
        val state = _uiState.value
        val list = state.htmlList

        if (list.isNotEmpty() && newPage < list.size && oldPage >= 0 && oldPage < list.size) {
            val oldChapter = list[oldPage].chapterTitle
            val newChapter = list[newPage].chapterTitle
            if (newChapter != null && newChapter != oldChapter) {
                saveHistory(newPage)
            } else if (!state.isVerticalMode) {
                if (!isTransitioning) saveHistory(newPage)
            }
        }

        val listSize = list.size
        if (listSize > 0 && newPage == listSize - 1 && nextHtmlList != null && !isTransitioning) {
            isTransitioning = true
            val newCurrentView = state.currentView + 1
            _uiState.value = state.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentView = newCurrentView
            )
            _currentPercentage.value = 0f
            currentRawHtml = nextRawHtml ?: currentRawHtml
            nextHtmlList = null
            nextChapterList = null
            nextRawHtml = null
            latestPage = 0
            showLoadingScrim = false
            return
        }

        val lastContentPageIndex = (listSize - 2).coerceAtLeast(0)
        val threshold = if (state.isVerticalMode) PRELOAD_THRESHOLD_VERTICAL else PRELOAD_THRESHOLD_HORIZONTAL
        val triggerPageIndex = (lastContentPageIndex - threshold).coerceAtLeast(0)

        if (listSize > 0 && !isPreloading && nextHtmlList == null &&
            state.currentView < state.maxWebView && !isTransitioning && newPage >= triggerPageIndex) {
            triggerPreload(state.currentView + 1, state.maxWebView)
        }

        latestPage = newPage
    }

    fun onPageChange(curPagerState: PagerState, scope: CoroutineScope) {
        if (pagerState == null) pagerState = curPagerState
        if (compositionScope == null) compositionScope = scope

        val newPage = curPagerState.targetPage
        if (ignoreFirstFakeZero) {
            if (newPage == 0) return
            ignoreFirstFakeZero = false
        }
        if (isTransitioning) {
            val isSettledAtInit = curPagerState.settledPage == _uiState.value.initPage && newPage == _uiState.value.initPage
            val userInterrupted = !curPagerState.isScrollInProgress &&
                    curPagerState.settledPage != _uiState.value.initPage &&
                    curPagerState.settledPage == newPage
            if (isSettledAtInit) {
                isTransitioning = false
                latestPage = _uiState.value.initPage
            } else if (userInterrupted) {
                isTransitioning = false
                latestPage = newPage
            } else {
                return
            }
        }

        val list = _uiState.value.htmlList
        if (list.isEmpty() || newPage >= list.size || newPage == latestPage) return

        val totalPages = curPagerState.pageCount.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalPages) * 100f
        _currentPercentage.value = percent

        processPageChange(newPage)
    }

    fun onVerticalPageSettled(newPage: Int) {
        if (ignoreFirstFakeZero) {
            if (newPage == 0) return
            ignoreFirstFakeZero = false
        }
        if (isTransitioning) {
            isTransitioning = false
            latestPage = newPage
        }
        if (newPage == latestPage) return

        val totalRows = _uiState.value.htmlList.size.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalRows) * 100f
        _currentPercentage.value = percent

        processPageChange(newPage)
    }

    fun saveCurrentHistory() {
        if (initialized) {
            saveHistory(
                pageToSave = latestPage,
                webViewToSave = _uiState.value.currentView
            )
        }
    }

    private fun saveHistory(
        pageToSave: Int,
        webViewToSave: Int = _uiState.value.currentView
    ) {
        if (url.isBlank()) return

        val state = _uiState.value
        val currentList = state.htmlList
        val safePage = pageToSave.coerceIn(0, (currentList.size - 1).coerceAtLeast(0))

        val currentChapter = currentList
            .getOrNull(safePage)
            ?.chapterTitle
            ?.takeIf { it != "footer" }

        val valueToSave: Int = if (state.isVerticalMode) {
            val avgItemsPerPage = getAvgItemsPerHorizontalPage()
            (safePage.toFloat() / avgItemsPerPage.toFloat()).toInt()
        } else {
            safePage
        }

        val urlToSave = FavoriteUtil.normalizeUrl(url)
        val authorIdToSave = currentAuthorId

        viewModelScope.launch(Dispatchers.IO) {
            val map = FavoriteUtil.getFavoriteMapSuspend()
            val fav = map[urlToSave] ?: return@launch

            if (fav.lastPage == valueToSave &&
                fav.lastView == webViewToSave &&
                fav.lastChapter == currentChapter &&
                fav.authorId == authorIdToSave
            ) return@launch

            FavoriteUtil.updateFavoriteSuspend(
                fav.copy(
                    lastPage = valueToSave,
                    lastView = webViewToSave,
                    lastChapter = currentChapter,
                    authorId = authorIdToSave
                )
            )
        }
    }

    private fun getAvgItemsPerHorizontalPage(): Int {
        val state = _uiState.value
        val topPadding = 24.dp
        val footerHeight = 87.dp
        val pageContentHeight = maxHeight - topPadding - footerHeight
        val pageContentHeightPx = ValueUtil.dpToPx(pageContentHeight)
        val lineHeightPx = ValueUtil.spToPx(state.lineHeight)
        return (pageContentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    }

    private fun contentCharLength(content: Content): Int {
        return if (content.type == ContentType.TEXT) {
            content.data.length.coerceAtLeast(1)
        } else {
            // 图片也占一个位置，避免“章节里全是图片”时进度无法前进。
            1
        }
    }

    private fun contentEndExclusive(pages: List<Content>): Int {
        val footerIndex = pages.indexOfFirst { it.chapterTitle == "footer" }
        return if (footerIndex >= 0) footerIndex else pages.size
    }

    private fun capturePageAnchor(currentPage: Int): PageAnchor {
        val state = _uiState.value
        val pages = state.htmlList
        val contentEnd = contentEndExclusive(pages)
        val totalItems = contentEnd.coerceAtLeast(1)
        val safePage = currentPage.coerceIn(0, (totalItems - 1).coerceAtLeast(0))

        val chapterIndex = state.chapterList
            .indexOfLast { it.startIndex <= safePage }
            .takeIf { it >= 0 }

        if (chapterIndex == null) {
            return PageAnchor(
                totalProgress = safePage.toFloat() / totalItems.toFloat(),
                chapterIndex = null,
                chapterProgress = 0f,
                charOffsetInChapter = 0
            )
        }

        val chapterStart = state.chapterList[chapterIndex].startIndex
        val chapterEndExclusive = state.chapterList
            .getOrNull(chapterIndex + 1)
            ?.startIndex
            ?: contentEnd

        val safeChapterStart = chapterStart.coerceIn(0, contentEnd)
        val safeChapterEnd = chapterEndExclusive.coerceIn(safeChapterStart, contentEnd)

        var totalCharsInChapter = 0
        var charsBeforeCurrentPage = 0

        for (i in safeChapterStart until safeChapterEnd) {
            val len = contentCharLength(pages[i])
            if (i < safePage) charsBeforeCurrentPage += len
            totalCharsInChapter += len
        }

        val chapterProgress = if (totalCharsInChapter > 0) {
            charsBeforeCurrentPage.toFloat() / totalCharsInChapter.toFloat()
        } else {
            0f
        }

        return PageAnchor(
            totalProgress = safePage.toFloat() / totalItems.toFloat(),
            chapterIndex = chapterIndex,
            chapterProgress = chapterProgress.coerceIn(0f, 1f),
            charOffsetInChapter = charsBeforeCurrentPage
        )
    }

    private fun resolvePageAnchor(
        anchor: PageAnchor,
        newPages: List<Content>,
        newChapters: List<ChapterInfo>,
        preferChapterProgress: Boolean = false
    ): Int {
        val newPageCount = newPages.size.coerceAtLeast(1)
        val maxIndex = (newPageCount - 1).coerceAtLeast(0)

        val contentEnd = contentEndExclusive(newPages)
        val chapterIndex = anchor.chapterIndex
        if (chapterIndex != null && chapterIndex in newChapters.indices) {
            val chapterStart = newChapters[chapterIndex].startIndex.coerceIn(0, contentEnd)
            val chapterEndExclusive = newChapters
                .getOrNull(chapterIndex + 1)
                ?.startIndex
                ?: contentEnd
            val chapterEnd = chapterEndExclusive.coerceIn(chapterStart, contentEnd)

            var totalCharsInNewChapter = 0
            for (i in chapterStart until chapterEnd) {
                totalCharsInNewChapter += contentCharLength(newPages[i])
            }

            if (totalCharsInNewChapter <= 0) {
                return chapterStart.coerceIn(0, maxIndex)
            }

            val rawTargetCharOffset = when {
                preferChapterProgress -> {
                    (anchor.chapterProgress * totalCharsInNewChapter).toInt()
                }
                anchor.charOffsetInChapter > 0 -> {
                    anchor.charOffsetInChapter
                }
                else -> {
                    (anchor.chapterProgress * totalCharsInNewChapter).toInt()
                }
            }

            val targetCharOffset = rawTargetCharOffset
                .coerceIn(0, (totalCharsInNewChapter - 1).coerceAtLeast(0))

            var accumulated = 0
            for (i in chapterStart until chapterEnd) {
                accumulated += contentCharLength(newPages[i])
                if (accumulated > targetCharOffset) {
                    return i.coerceIn(0, maxIndex)
                }
            }

            return (chapterEnd - 1).coerceIn(0, maxIndex)
        }

        return (anchor.totalProgress * newPageCount)
            .toInt()
            .coerceIn(0, maxIndex)
    }

    private fun applyRepaginatedContent(
        newPages: List<Content>,
        newChapters: List<ChapterInfo>,
        pageToScrollTo: Int
    ) {
        val newPageCount = newPages.size.coerceAtLeast(1)
        val safePage = pageToScrollTo.coerceIn(0, (newPageCount - 1).coerceAtLeast(0))
        val newPercent = (safePage.toFloat() / newPageCount.toFloat()) * 100f

        ignoreFirstFakeZero = safePage > 0
        _uiState.value = _uiState.value.copy(
            htmlList = newPages,
            chapterList = newChapters,
            initPage = safePage,
            isError = false
        )
        _currentPercentage.value = newPercent
        latestPage = safePage
        showLoadingScrim = false
        isTransitioning = false
    }

    private fun saveCurrentSettings() {
        val state = _uiState.value
        val settings = ReaderSettings(
            fontSizePx = ValueUtil.spToPx(state.fontSize),
            lineHeightPx = ValueUtil.spToPx(state.lineHeight),
            paddingDp = state.padding.value,
            nightMode = false,
            backgroundColor = null,
            loadImages = state.loadImages,
            isVerticalMode = state.isVerticalMode,
            fontFamily = 0
        )
        SettingsUtil.saveSettings(settings)
    }

    fun saveSettings(currentPage: Int) {
        val anchor = capturePageAnchor(currentPage)
        saveCurrentSettings()
        viewModelScope.launch {
            showLoadingScrim = true
            kotlinx.coroutines.yield()

            val (newPages, newChapters) = withContext(readerLayoutDispatcher) {
                paginateContent()
            }

            val pageToScrollTo = resolvePageAnchor(anchor, newPages, newChapters)
            applyRepaginatedContent(newPages, newChapters, pageToScrollTo)
        }
    }

    fun setReadingMode(isVertical: Boolean, currentPage: Int) {
        if (isVertical == _uiState.value.isVerticalMode) return
        _uiState.value = _uiState.value.copy(isVerticalMode = isVertical, initPage = currentPage)
        saveSettings(currentPage)
    }

    fun toggleChapterDrawer(show: Boolean) {
        _uiState.value = _uiState.value.copy(showChapterDrawer = show)
    }

    fun onSetLineHeight(lineHeight: TextUnit) {
        val currentFontSizeValue = _uiState.value.fontSize.value
        val snappedLineHeight = ReaderSpacingOptions.snapLineHeight(
            value = lineHeight.value,
            fontSize = currentFontSizeValue
        )
        _uiState.value = _uiState.value.copy(lineHeight = snappedLineHeight.sp)
    }

    fun onSetFontSize(fontSize: TextUnit) {
        val currentFontSize = _uiState.value.fontSize.value
        val currentLineHeight = _uiState.value.lineHeight.value
        val currentLineHeightRatio = currentLineHeight / currentFontSize
        val safeFontSize = ReaderSpacingOptions.snapFontSize(fontSize.value)
        val safeLineHeight = ReaderSpacingOptions.snapLineHeight(
            value = safeFontSize * currentLineHeightRatio,
            fontSize = safeFontSize
        )
        _uiState.value = _uiState.value.copy(
            fontSize = safeFontSize.sp,
            lineHeight = safeLineHeight.sp
        )
        updateFontRatios()
    }

    fun onSetPadding(padding: Dp) {
        _uiState.value = _uiState.value.copy(
            padding = ReaderSpacingOptions.snapPadding(padding.value).dp
        )
    }

    fun toggleLoadImages(load: Boolean) {
        if (_uiState.value.loadImages == load) return

        val currentPage = latestPage
        val anchor = capturePageAnchor(currentPage)
        _uiState.value = _uiState.value.copy(loadImages = load)
        saveCurrentSettings()

        val html = currentRawHtml
        if (html != null) {
            viewModelScope.launch {
                showLoadingScrim = true
                kotlinx.coroutines.yield()

                val (newPages, newChapters) = withContext(readerLayoutDispatcher) {
                    parseHtmlToContent(html, _uiState.value.currentView)
                    paginateContent()
                }

                val pageToScrollTo = resolvePageAnchor(anchor, newPages, newChapters)
                applyRepaginatedContent(newPages, newChapters, pageToScrollTo)
            }
        } else {
            _uiState.value = _uiState.value.copy(initPage = currentPage)
            onSetView(_uiState.value.currentView, forceReload = true)
        }
    }

    override fun onCleared() {
        if (initialized) saveHistory(latestPage)
        nextHtmlList = null
        nextChapterList = null
        nextRawHtml = null
        isPreloading = false
        layoutCache.evictAll()
        parsedContentCache.evictAll()
        readerLayoutDispatcher.close()
        super.onCleared()
    }

    fun syncGlobalTranslationMode(mode: String, currentPage: Int) {
        val translationMode = LanguageModeUtil.readerTranslationMode(mode)
        if (_uiState.value.translationMode == translationMode) return

        val anchor = capturePageAnchor(currentPage)
        _uiState.value = _uiState.value.copy(translationMode = translationMode)

        val html = currentRawHtml
        if (html != null) {
            viewModelScope.launch {
                showLoadingScrim = true
                kotlinx.coroutines.yield()

                val (newPages, newChapters) = withContext(readerLayoutDispatcher) {
                    parseHtmlToContent(html, _uiState.value.currentView)
                    paginateContent()
                }

                val pageToScrollTo = resolvePageAnchor(anchor, newPages, newChapters, preferChapterProgress = true)
                applyRepaginatedContent(newPages, newChapters, pageToScrollTo)

                // 简繁切换后标题需同步转换：清空并重建全书目录（当前页即时更新，
                // 其余已缓存页按最新 translationMode 重新解析；indexCachedPages 读取的是最新状态）。
                pageChapterTitles.clear()
                recordPageChapters(_uiState.value.currentView, newChapters)
                indexCachedPages()
            }
        }
    }

    fun updateFontRatios() {
        val state = _uiState.value
        val typeface = typefaceFromMode(state.fontFamily)
        val fontSizePx = ValueUtil.spToPx(state.fontSize)
        currentAsciiRatios = FontMetricsUtil.getAsciiWidthRatios(typeface, fontSizePx)
    }
}

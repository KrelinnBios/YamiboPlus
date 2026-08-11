package org.shirakawatyu.yamibo.novel.util.manga

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 漫画图片统一加载管线。
 */
@OptIn(ExperimentalCoilApi::class)
object MangaImagePipeline {
    private const val TAG = "MangaImagePipeline"
    private const val REFERER = "https://bbs.yamibo.com/"
    private const val WEBVIEW_WAIT_TIMEOUT_MS = 60_000L

    enum class Source {
        WEBVIEW_TAKEOVER,
        HANDOFF_HOT,
        HANDOFF_COLD,
        NATIVE_HOT_WINDOW,
        NATIVE_WARM_WINDOW,
        NATIVE_COLD_WINDOW,
        CHAPTER_COLD_PREFETCH
    }

    enum class LoadResult {
        MEMORY_HIT,
        DISK_HIT,
        NETWORK_SUCCESS,
        NETWORK_FAILED,
        CANCELLED
    }

    private data class InFlightLoad(
        val cacheKey: String,
        val url: String,
        val source: Source,
        val deferred: Deferred<LoadResult>,
        val owners: MutableSet<String>
    )

    private data class PrefetchSpec(
        val url: String,
        val source: Source,
        val memory: Boolean,
        val rank: Int
    )

    private data class RequestContext(
        val cookie: String,
        val referer: String
    )

    private const val MAX_REQUEST_CONTEXTS = 600

    private val pipelineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val requestContextLock = Any()
    private val requestContexts =
        object : LinkedHashMap<String, RequestContext>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, RequestContext>?
            ): Boolean = size > MAX_REQUEST_CONTEXTS
        }

    private val inFlight = ConcurrentHashMap<String, InFlightLoad>()
    private val ownerUrlKeys = ConcurrentHashMap<String, MutableSet<String>>()
    private val cancelJobs = ConcurrentHashMap<String, Job>() // 延迟取消任务池
    private val ownerLastIndex = ConcurrentHashMap<String, Int>()
    private val ownerDirections = ConcurrentHashMap<String, Int>()
    private val ownerDirectionStreak = ConcurrentHashMap<String, Int>()
    private data class RecentWindow(
        val hotKeys: List<String>,
        val nonHotKeys: Set<String>
    )
    private val ownerRecentWindows = ConcurrentHashMap<String, ArrayDeque<RecentWindow>>()
    private val chapterColdOwnerParents = ConcurrentHashMap<String, String>()
    private val parentChapterColdOwners = ConcurrentHashMap<String, MutableSet<String>>()

    /** WebView takeover / handoff 首屏热图 / Native 当前附近热图。 */
    private val highSemaphore = Semaphore(4)

    /** Native 中距离窗口预热。 */
    private val warmSemaphore = Semaphore(3)

    /** 远距离预取、跨章节整章冷预取，只进磁盘，低并发。 */
    private val coldSemaphore = Semaphore(2)

    /**
     * WebView 图片请求接管入口。
     */
    fun interceptForWebView(
        context: Context,
        url: String,
        headers: Map<String, String>? = null
    ): WebResourceResponse? {
        val appContext = context.applicationContext
        val diskCache = appContext.imageLoader.diskCache ?: return null
        val key = cacheKey(url)
        rememberRequestContext(listOf(url), headers.orEmpty(), "")

        openSnapshot(diskCache, key)?.let { snapshot ->
            return createResponse(url, snapshot)
        }

        val ownerKey = "webview:$key"
        val deferred = ensureDiskCached(
            context = appContext,
            url = url,
            headers = headers.orEmpty(),
            ownerKey = ownerKey,
            source = Source.WEBVIEW_TAKEOVER,
            memory = false
        )

        return try {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(WEBVIEW_WAIT_TIMEOUT_MS) {
                    deferred.await()
                }
                openSnapshot(diskCache, key)?.let { snapshot -> createResponse(url, snapshot) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WebView takeover failed: $url", t)
            null
        }
    }

    fun handoffPrefetch(
        context: Context,
        urls: List<String>,
        clickedIndex: Int = 0,
        headers: Map<String, String> = emptyMap(),
        cookie: String = ""
    ) {
        val normalized = urls.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        rememberRequestContext(normalized, headers, cookie)

        val safeIndex = clickedIndex.coerceIn(0, normalized.lastIndex)
        val ordered = buildList {
            addAll(normalized.drop(safeIndex))
            addAll(normalized.take(safeIndex).asReversed())
        }.distinct()

        val ownerKey = "handoff:${ordered.firstOrNull().orEmpty().hashCode()}:${System.nanoTime()}"
        val firstUrl = ordered.first()
        val firstLoad = ensureDiskCached(
            context = context.applicationContext,
            url = firstUrl,
            headers = headers,
            ownerKey = ownerKey,
            source = Source.HANDOFF_HOT,
            memory = true,
            explicitCookie = cookie
        )

        // 大图章节先把首张图完整送到缓存，再放行后续预取。
        // 否则 3~5 MB 图片会同时占满同一 host 的连接和下行带宽，首屏反而最慢。
        pipelineScope.launch {
            runCatching { firstLoad.await() }
            ordered.drop(1).forEachIndexed { index, imgUrl ->
                val hot = index < 2
                ensureDiskCached(
                    context = context.applicationContext,
                    url = imgUrl,
                    headers = headers,
                    ownerKey = ownerKey,
                    source = if (hot) Source.HANDOFF_HOT else Source.HANDOFF_COLD,
                    memory = hot,
                    explicitCookie = cookie
                )
            }
        }
    }

    /**
     * Native 阅读页当前窗口预热入口。
     */
    fun updateNativeWindow(
        context: Context,
        ownerKey: String,
        urls: List<String>,
        currentIndex: Int,
        cookie: String = "",
        isVerticalMode: Boolean = true,
        isRtl: Boolean = false
    ) {
        if (urls.isEmpty() || currentIndex !in urls.indices) {
            cancelOwner(ownerKey)
            return
        }
        rememberRequestContext(urls, emptyMap(), cookie)
        val previousIndex = ownerLastIndex.put(ownerKey, currentIndex)

        val rawDirection = when {
            previousIndex == null -> ownerDirections[ownerKey] ?: 1
            currentIndex > previousIndex -> 1
            currentIndex < previousIndex -> -1
            else -> ownerDirections[ownerKey] ?: 1
        }

        val oldDirection = ownerDirections[ownerKey] ?: rawDirection

        val streak = if (rawDirection == oldDirection) {
            0
        } else {
            (ownerDirectionStreak[ownerKey] ?: 0) + 1
        }

        val direction = if (rawDirection != oldDirection && streak >= 2) {
            ownerDirectionStreak[ownerKey] = 0
            rawDirection
        } else {
            ownerDirectionStreak[ownerKey] = streak
            oldDirection
        }

        ownerDirections[ownerKey] = direction
        val specs = buildNativeWindowSpecs(
            urls = urls,
            currentIndex = currentIndex,
            direction = direction,
            isVerticalMode = isVerticalMode,
            isRtl = isRtl
        )
        val currentWindowKeys = specs.map { cacheKey(it.url) }.toSet()

        val currentRecentWindow = RecentWindow(
            hotKeys = specs
                .filter { it.source == Source.NATIVE_HOT_WINDOW }
                .sortedBy { it.rank }
                .map { cacheKey(it.url) }
                .take(2),
            nonHotKeys = specs
                .filterNot { it.source == Source.NATIVE_HOT_WINDOW }
                .map { cacheKey(it.url) }
                .toSet()
        )

        val recentWindows = ownerRecentWindows.getOrPut(ownerKey) { ArrayDeque() }

        val retainedHotKeys = buildList {
            for (window in recentWindows) {
                for (key in window.hotKeys) {
                    if (size >= 2) break
                    add(key)
                }
                if (size >= 2) break
            }
        }.toSet()

        val retainedNonHotKeys = buildSet {
            recentWindows.forEach { addAll(it.nonHotKeys) }
        }

        val desiredKeys = buildSet {
            addAll(currentWindowKeys)
            addAll(retainedHotKeys)
            addAll(retainedNonHotKeys)
        }.toMutableSet()

        if (currentWindowKeys.isNotEmpty()) {
            recentWindows.addFirst(currentRecentWindow)
            while (recentWindows.size > 2) {
                recentWindows.removeLast()
            }
        }

        val previousKeys = ownerUrlKeys[ownerKey].orEmpty().toSet()

        previousKeys
            .filterNot { it in desiredKeys }
            .forEach { releaseOwnerFromUrl(ownerKey, it) }

        ownerUrlKeys[ownerKey] = desiredKeys

        val visibleLoad = synchronized(lock) {
            inFlight[cacheKey(urls[currentIndex])]?.deferred
        }

        fun startWindowPrefetch() {
            specs.sortedBy { it.rank }.forEach { spec ->
                ensureDiskCached(
                    context = context.applicationContext,
                    url = spec.url,
                    ownerKey = ownerKey,
                    source = spec.source,
                    memory = spec.memory,
                    explicitCookie = cookie
                )
            }
        }

        if (visibleLoad != null && !visibleLoad.isCompleted) {
            pipelineScope.launch {
                runCatching { visibleLoad.await() }
                if (ownerLastIndex[ownerKey] == currentIndex) {
                    startWindowPrefetch()
                }
            }
        } else {
            startWindowPrefetch()
        }
    }

    /**
     * 跨章节边缘页预取。
     */
    fun prefetchChapterEdge(
        context: Context,
        ownerKey: String,
        urls: List<String>,
        cookie: String = ""
    ) {
        val normalized = urls.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        rememberRequestContext(normalized, emptyMap(), cookie)

        normalized.forEach { imgUrl ->
            ensureDiskCached(
                context = context.applicationContext,
                url = imgUrl,
                ownerKey = ownerKey,
                source = Source.NATIVE_HOT_WINDOW,
                memory = true,
                explicitCookie = cookie
            )
        }
    }

    /**
     * 跨章节整章冷预取。
     */
    fun coldPrefetchChapter(
        context: Context,
        urls: List<String>,
        parentOwnerKey: String? = null,
        cookie: String = ""
    ) {
        val normalized = urls.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        rememberRequestContext(normalized, emptyMap(), cookie)

        val ownerKey = "chapter:${normalized.firstOrNull().orEmpty().hashCode()}:${System.nanoTime()}"
        registerChapterColdOwner(parentOwnerKey, ownerKey)

        normalized.forEach { imgUrl ->
            ensureDiskCached(
                context = context.applicationContext,
                url = imgUrl,
                ownerKey = ownerKey,
                source = Source.CHAPTER_COLD_PREFETCH,
                memory = false,
                explicitCookie = cookie
            )
        }

        synchronized(lock) {
            if (ownerUrlKeys[ownerKey].isNullOrEmpty()) {
                unregisterChapterColdOwner(ownerKey)
            }
        }
    }

    /**
     * 给需要 listener 的 UI 使用：
     * MangaImagePipeline.newImageRequestBuilder(...).listener(...).build()
     */
    fun newImageRequestBuilder(
        context: Context,
        url: String,
        cookie: String = "",
        forceVersion: Int = 0,
        memory: Boolean = true
    ): ImageRequest.Builder {
        val dataUrl = cacheBustingUrl(url, forceVersion)
        val key = cacheKey(url)

        return ImageRequest.Builder(context.applicationContext)
            .data(dataUrl)
            .diskCacheKey(key)
            .memoryCacheKey(MemoryCache.Key(key))
            .crossfade(false)
            .applyDefaultHeaders(url = url, explicitCookie = cookie, headers = emptyMap())
            .apply {
                if (forceVersion > 0) {
                    // 强刷时：跳过旧缓存读取，但把新结果写回同一个原始 key。
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    diskCachePolicy(CachePolicy.WRITE_ONLY)
                } else {
                    memoryCachePolicy(if (memory) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }
    }

    suspend fun evict(context: Context, url: String) = withContext(Dispatchers.IO) {
        val key = cacheKey(url)
        try {
            context.applicationContext.imageLoader.diskCache?.remove(key)
            context.applicationContext.imageLoader.memoryCache?.remove(MemoryCache.Key(key))
        } catch (t: Throwable) {
            Log.w(TAG, "Evict failed: $url", t)
        }
    }

    suspend fun evictAll(context: Context, urls: List<String>) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val diskCache = appContext.imageLoader.diskCache
        val memoryCache = appContext.imageLoader.memoryCache
        urls.distinctBy(::cacheKey).forEach { url ->
            val key = cacheKey(url)
            runCatching {
                diskCache?.remove(key)
                memoryCache?.remove(MemoryCache.Key(key))
            }.onFailure { error ->
                Log.w(TAG, "Failed to evict cached manga image: $url", error)
            }
        }
    }

    fun cancelOwner(ownerKey: String) {
        val keys = ownerUrlKeys.remove(ownerKey)?.toSet().orEmpty()
        keys.forEach { key -> releaseOwnerFromUrl(ownerKey, key) }

        ownerLastIndex.remove(ownerKey)
        ownerDirections.remove(ownerKey)
        ownerDirectionStreak.remove(ownerKey)
        ownerRecentWindows.remove(ownerKey)
        unregisterChapterColdOwner(ownerKey)
    }

    /**
     * 只取消某个 Native 页面 owner 的窗口预加载，不关闭全局 pipeline。
     * 不提供 shutdown()：这是应用级单例，取消 scope 后无法安全重建。
     */
    fun cancelNativeWindow(ownerKey: String) {
        cancelOwner(ownerKey)
    }

    /**
     * 取消某个 Native 阅读页启动的跨章节整章冷预取。
     * 已经完成并写入磁盘的缓存不受影响，只取消还在排队/下载的冷预取任务。
     */
    fun cancelChapterColdPrefetches(parentOwnerKey: String) {
        val owners = parentChapterColdOwners.remove(parentOwnerKey)?.toSet().orEmpty()
        owners.forEach { ownerKey ->
            chapterColdOwnerParents.remove(ownerKey)
            cancelOwner(ownerKey)
        }
    }

    private fun registerChapterColdOwner(parentOwnerKey: String?, ownerKey: String) {
        val parent = parentOwnerKey?.takeIf { it.isNotBlank() } ?: return
        chapterColdOwnerParents[ownerKey] = parent
        parentChapterColdOwners
            .getOrPut(parent) { ConcurrentHashMap.newKeySet<String>() }
            .add(ownerKey)
    }

    private fun unregisterChapterColdOwner(ownerKey: String) {
        val parent = chapterColdOwnerParents.remove(ownerKey) ?: return
        parentChapterColdOwners[parent]?.remove(ownerKey)
        if (parentChapterColdOwners[parent]?.isEmpty() == true) {
            parentChapterColdOwners.remove(parent)
        }
    }

    private fun ensureDiskCached(
        context: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        ownerKey: String,
        source: Source,
        memory: Boolean,
        explicitCookie: String = ""
    ): Deferred<LoadResult> {
        val appContext = context.applicationContext
        val imageLoader = appContext.imageLoader
        val diskCache = imageLoader.diskCache
        val key = cacheKey(url)

        if (memory && imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null) {
            return CompletableDeferred(LoadResult.MEMORY_HIT)
        }

        if (diskCache != null && openSnapshot(diskCache, key)?.use { true } == true) {
            return CompletableDeferred(LoadResult.DISK_HIT)
        }

        synchronized(lock) {
            ownerUrlKeys.getOrPut(ownerKey) { mutableSetOf() }.add(key)

            val existing = inFlight[key]
            if (existing != null) {
                existing.owners.add(ownerKey)
                // 若该请求正处于延迟取消倒计时中，中止取消操作（复活）
                cancelJobs.remove(key)?.cancel()
                return existing.deferred
            }

            val deferredRef = AtomicReference<Deferred<LoadResult>?>(null)
            val deferred = pipelineScope.async(start = CoroutineStart.LAZY) {
                try {
                    withPermitFor(source) {
                        executeCoilLoad(
                            context = appContext,
                            url = url,
                            headers = headers,
                            source = source,
                            memory = memory,
                            explicitCookie = explicitCookie
                        )
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) {
                        LoadResult.CANCELLED
                    } else {
                        Log.w(TAG, "Load failed [$source]: $url", t)
                        LoadResult.NETWORK_FAILED
                    }
                } finally {
                    synchronized(lock) {
                        val current = inFlight[key]
                        if (current?.deferred === deferredRef.get()) {
                            inFlight.remove(key)

                            current?.owners?.forEach { owner ->
                                ownerUrlKeys[owner]?.remove(key)
                                if (ownerUrlKeys[owner]?.isEmpty() == true) {
                                    ownerUrlKeys.remove(owner)
                                    unregisterChapterColdOwner(owner)
                                }
                            }
                        }
                    }
                }
            }

            deferredRef.set(deferred)
            inFlight[key] = InFlightLoad(
                cacheKey = key,
                url = url,
                source = source,
                deferred = deferred,
                owners = mutableSetOf(ownerKey)
            )

            deferred.start()
            return deferred
        }
    }

    private suspend fun executeCoilLoad(
        context: Context,
        url: String,
        headers: Map<String, String>,
        source: Source,
        memory: Boolean,
        explicitCookie: String
    ): LoadResult {
        val key = cacheKey(url)
        val imageLoader = context.imageLoader

        if (memory && imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null) {
            return LoadResult.MEMORY_HIT
        }

        imageLoader.diskCache?.let { diskCache ->
            if (openSnapshot(diskCache, key)?.use { true } == true) {
                return LoadResult.DISK_HIT
            }
        }

        val request = ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(key)
            .memoryCacheKey(MemoryCache.Key(key))
            .memoryCachePolicy(if (memory) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .applyDefaultHeaders(url = url, explicitCookie = explicitCookie, headers = headers)
            .build()

        val result = imageLoader.execute(request)
        return if (result is SuccessResult) {
            LoadResult.NETWORK_SUCCESS
        } else {
            Log.w(TAG, "Coil returned non-success result [$source]: $url")
            LoadResult.NETWORK_FAILED
        }
    }

    private suspend fun <T> withPermitFor(source: Source, block: suspend () -> T): T {
        return when (source) {
            Source.WEBVIEW_TAKEOVER,
            Source.HANDOFF_HOT,
            Source.NATIVE_HOT_WINDOW -> highSemaphore.withPermit { block() }

            Source.NATIVE_WARM_WINDOW -> warmSemaphore.withPermit { block() }

            Source.HANDOFF_COLD,
            Source.NATIVE_COLD_WINDOW,
            Source.CHAPTER_COLD_PREFETCH -> coldSemaphore.withPermit { block() }
        }
    }

    private fun buildNativeWindowSpecs(
        urls: List<String>,
        currentIndex: Int,
        direction: Int,
        isVerticalMode: Boolean,
        isRtl: Boolean
    ): List<PrefetchSpec> {

        val readDirection = if (direction < 0) -1 else 1
        val oppositeDirection = -readDirection

        val specs = mutableListOf<PrefetchSpec>()
        var rank = 0

        fun add(offset: Int, source: Source, memory: Boolean) {
            val index = currentIndex + offset
            val imgUrl = urls.getOrNull(index) ?: return
            specs += PrefetchSpec(
                url = imgUrl,
                source = source,
                memory = memory,
                rank = rank++
            )
        }

        for (step in 1..10) {
            val offset = readDirection * step
            when (step) {
                1, 2, 3 -> add(offset, Source.NATIVE_HOT_WINDOW, memory = true)
                in 4..7 -> add(offset, Source.NATIVE_WARM_WINDOW, memory = false)
                else -> add(offset, Source.NATIVE_COLD_WINDOW, memory = false)
            }
        }

        for (step in 1..2) {
            val offset = oppositeDirection * step
            add(offset, Source.NATIVE_HOT_WINDOW, memory = true)
        }

        return specs.distinctBy { cacheKey(it.url) }
    }

    private fun releaseOwnerFromUrl(ownerKey: String, cacheKey: String) {
        synchronized(lock) {
            ownerUrlKeys[ownerKey]?.remove(cacheKey)
            val load = inFlight[cacheKey] ?: return
            load.owners.remove(ownerKey)
            if (load.owners.isEmpty() && load.source != Source.WEBVIEW_TAKEOVER) {
                cancelJobs[cacheKey]?.cancel()
                cancelJobs[cacheKey] = pipelineScope.launch {
                    delay(1500L)
                    synchronized(lock) {
                        val currentLoad = inFlight[cacheKey]
                        if (currentLoad != null && currentLoad.owners.isEmpty()) {
                            currentLoad.deferred.cancel()
                            inFlight.remove(cacheKey)
                        }
                        cancelJobs.remove(cacheKey)
                    }
                }
            }
        }
    }

    private fun ImageRequest.Builder.applyDefaultHeaders(
        url: String,
        explicitCookie: String,
        headers: Map<String, String>
    ): ImageRequest.Builder {
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) addHeader(key, value)
        }

        val requestContext = synchronized(requestContextLock) {
            requestContexts[cacheKey(url)]
        }
        val headerCookie = headers.valueIgnoreCase("Cookie")
        val cookie = explicitCookie
            .ifBlank { headerCookie }
            .ifBlank { requestContext?.cookie.orEmpty() }
            .ifBlank { YamiboSession.cookieFor(url) }

        if (cookie.isNotBlank()) setHeader("Cookie", cookie)
        if (url.contains("yamibo.com", ignoreCase = true)) {
            val referer = headers.valueIgnoreCase("Referer")
                .ifBlank { requestContext?.referer.orEmpty() }
                .ifBlank { REFERER }
            setHeader("Referer", referer)
        }

        return this
    }

    private fun rememberRequestContext(
        urls: List<String>,
        headers: Map<String, String>,
        explicitCookie: String
    ) {
        val headerCookie = headers.valueIgnoreCase("Cookie")
        val referer = headers.valueIgnoreCase("Referer")
        synchronized(requestContextLock) {
            urls.forEach { url ->
                val key = cacheKey(url)
                val previous = requestContexts[key]
                val cookie = explicitCookie
                    .ifBlank { headerCookie }
                    .ifBlank { previous?.cookie.orEmpty() }
                requestContexts[key] = RequestContext(
                    cookie = cookie,
                    referer = referer.ifBlank { previous?.referer.orEmpty() }
                )
            }
        }
    }

    private fun Map<String, String>.valueIgnoreCase(name: String): String {
        return entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            .orEmpty()
    }

    private fun cacheKey(url: String): String {
        return url
            .replace(Regex("""([?&])_t=\d+(&?)""")) { match ->
                val prefix = match.groupValues[1]
                val suffix = match.groupValues[2]
                when {
                    prefix == "?" && suffix == "&" -> "?"
                    prefix == "?" -> ""
                    suffix == "&" -> "&"
                    else -> ""
                }
            }
            .removeSuffix("?")
            .removeSuffix("&")
    }

    fun countDiskCachedPages(context: Context, urls: List<String>): Int {
        val diskCache = context.applicationContext.imageLoader.diskCache ?: return 0
        return urls
            .distinctBy(::cacheKey)
            .count { url ->
                openSnapshot(diskCache, cacheKey(url))?.use { true } == true
            }
    }

    /** 统计给定图片 URL 在磁盘缓存中的实际字节大小（仅计已命中的快照）。 */
    fun sumDiskCachedBytes(context: Context, urls: List<String>): Long {
        val diskCache = context.applicationContext.imageLoader.diskCache ?: return 0L
        return urls
            .distinctBy(::cacheKey)
            .sumOf { url ->
                openSnapshot(diskCache, cacheKey(url))?.use { snapshot ->
                    runCatching { snapshot.data.toFile().length() }.getOrDefault(0L)
                } ?: 0L
            }
    }

    private fun cacheBustingUrl(url: String, forceVersion: Int): String {
        if (forceVersion <= 0) return url
        return if (url.contains("?")) "$url&_t=$forceVersion" else "$url?_t=$forceVersion"
    }

    private fun openSnapshot(diskCache: DiskCache, key: String): DiskCache.Snapshot? {
        return try {
            diskCache.openSnapshot(key)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createResponse(url: String, snapshot: DiskCache.Snapshot): WebResourceResponse {
        val inputStream = SnapshotInputStream(snapshot)
        return WebResourceResponse(getMimeType(url), null, inputStream).apply {
            responseHeaders = mutableMapOf(
                "Cache-Control" to "public, max-age=31536000",
                "Access-Control-Allow-Origin" to "*"
            )
        }
    }

    private fun getMimeType(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains(".png") -> "image/png"
            lowerUrl.contains(".gif") -> "image/gif"
            lowerUrl.contains(".webp") -> "image/webp"
            lowerUrl.contains(".bmp") -> "image/bmp"
            lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") -> "image/jpeg"
            else -> "image/jpeg"
        }
    }

    private class SnapshotInputStream(
        private val snapshot: DiskCache.Snapshot,
        private val delegate: InputStream = snapshot.data.toFile().inputStream()
    ) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray): Int = delegate.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun skip(n: Long): Long = delegate.skip(n)
        override fun available(): Int = delegate.available()

        override fun close() {
            try {
                delegate.close()
            } finally {
                snapshot.close()
            }
        }
    }
}

package org.shirakawatyu.yamibo.novel.ui.vm

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.bean.MangaUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.bean.MangaUpdateCheckStrategy
import org.shirakawatyu.yamibo.novel.bean.NovelUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi
import org.shirakawatyu.yamibo.novel.network.NovelApi
import org.shirakawatyu.yamibo.novel.ui.state.FavoriteState
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.CacheMaintenance
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.CurrentUserUtil
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteAddUtil
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteDeleteUtil
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.favorite.TombstoneQueueUtil
import org.shirakawatyu.yamibo.novel.util.manga.MangaImagePipeline
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil
import org.shirakawatyu.yamibo.novel.bean.OtherUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.util.updateCheck.MangaUpdateCheckUtil
import org.shirakawatyu.yamibo.novel.util.updateCheck.NovelUpdateCheckUtil
import org.shirakawatyu.yamibo.novel.util.updateCheck.OtherUpdateCheckUtil
import org.shirakawatyu.yamibo.novel.util.updateCheck.UpdateCheckEngine
import org.shirakawatyu.yamibo.novel.util.updateCheck.UpdateCheckResult
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class FetchState { IDLE, BACKGROUND, MANUAL }

class FavoriteVM(private val applicationContext: Context) : ViewModel() {
    companion object {
        /** 手动检查小说很快结束时，至少让加载圈稳定可见一小段时间，避免闪烁。 */
        private const val MIN_UPDATE_CHECK_VISIBLE_MS = 650L
        private const val MANGA_CACHE_URL_LIMIT = 500

        private val MANGA_SECTIONS_BY_FID = linkedMapOf(
            "30" to "中文百合漫画区",
            "37" to "百合漫画图源区"
        )
        private val NOVEL_SECTIONS_BY_FID = linkedMapOf(
            "49" to "文學區",
            "55" to "轻小说/译文区",
            "60" to "TXT小说区"
        )
        private val MANGA_FIDS = FavoriteTypeResolver.MANGA_FIDS
        private val NOVEL_FIDS = FavoriteTypeResolver.NOVEL_FIDS
        private val MANGA_SECTIONS = MANGA_SECTIONS_BY_FID.values
        private val NOVEL_SECTIONS = NOVEL_SECTIONS_BY_FID.values
    }

    private val _uiState = MutableStateFlow(FavoriteState())
    val uiState = _uiState.asStateFlow()
    private val stateMutex = Mutex()

    // 记录当前的刷新状态，默认为空闲
    private val currentFetchState = AtomicReference(FetchState.IDLE)

    // 请求世代ID，用于打断旧的递归任务
    private val fetchGeneration = AtomicLong(0)
    private val logTag = "FavoriteVM"
    private var allFavorites: List<Favorite> = listOf()
    private var updateCheckNovels: List<NovelUpdateCheckProfile> = listOf()
    private var updateCheckMangas: List<MangaUpdateCheckProfile> = listOf()
    private var updateCheckOthers: List<OtherUpdateCheckProfile> = listOf()

    // 预加载的表单校验码
    private var prefetchFormHash: String? = null

    // 记录最后一次成功触发刷新的时间戳
    private var lastSmartSyncTime = 0L

    // 冷却时间，5秒内不重复发起后台同步
    private val SMART_SYNC_COOLDOWN = 5_000L

    // 等待队列：保存正在倒计时的那个任务
    private var pendingSyncJob: Job? = null
    private var fetchJob: Job? = null
    private var batchUpdateCheckJob: Job? = null
    private val updateCheckVisibleSince = mutableMapOf<String, Long>()
    private val updateCheckHideJobs = mutableMapOf<String, Job>()
    private val typeProbeJobs = mutableMapOf<String, Job>()
    private val typeProbeJobsLock = Any()
    private var classificationQueueJob: Job? = null
    private var lastNavigateTime = 0L
    private val SMART_SYNC_TIMEOUT = 20 * 60 * 1000L

    // 记录正在删除过程中的URL
    private val pendingDeleteUrls = mutableSetOf<String>()

    enum class RefreshStrategy {
        FULL,   // 全量刷新
        SMART,  // 增量刷新
        SKIP    // 跳过刷新
    }

    var nextResumeStrategy = RefreshStrategy.FULL
    var currentCategory: Int = -1
        private set
    var lastPauseTime = 0L
    var isFavoritePageVisible = false

    // 本地缓存工具
    private val localCache by lazy { LocalCacheUtil.getInstance(applicationContext) }

    init {
        viewModelScope.launch {
            TombstoneQueueUtil.initQueue()
            retryPendingDeletesQuietly()

            FavoriteUtil.getFavoriteFlow()
                .flowOn(Dispatchers.IO)
                .collect { fullList ->
                    stateMutex.withLock {
                        allFavorites = fullList
                        updateUiList()
                    }
                    classificationQueueJob?.cancel()
                    classificationQueueJob = viewModelScope.launch {
                        fullList
                            .filter { getStoredFavoriteType(it) == 0 }
                            .forEach { favorite ->
                                probeFavoriteTypeInBackground(favorite)
                                delay(300L)
                            }
                    }
                    refreshCacheInfo(localCache.index.value)
                    val titleMap = fullList.associate {
                        val cleanTitle = it.title.replace(Regex("^(?:【.*?】|\\[.*?\\]|\\s)+"), "")
                            .ifBlank { it.title }
                        it.url to cleanTitle
                    }
                    localCache.updateCacheTitlesCompat(
                        titlesMap = titleMap,
                        normalizeUrl = { FavoriteUtil.normalizeUrl(it) }
                    )
                }
        }

        viewModelScope.launch {
            localCache.index.collect { index ->
                refreshCacheInfo(index)
            }
        }

        viewModelScope.launch {
            NovelUpdateCheckUtil.getUpdateCheckFlow()
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    stateMutex.withLock {
                        updateCheckNovels = list
                    }
                    _uiState.update { it.copy(updateCheckNovels = list) }
                }
        }

        viewModelScope.launch {
            MangaUpdateCheckUtil.getUpdateCheckFlow()
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    stateMutex.withLock {
                        updateCheckMangas = list
                    }
                    _uiState.update { it.copy(updateCheckMangas = list) }
                }
        }

        viewModelScope.launch {
            OtherUpdateCheckUtil.getUpdateCheckFlow()
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    stateMutex.withLock {
                        updateCheckOthers = list
                    }
                    _uiState.update { it.copy(updateCheckOthers = list) }
                }
        }

        // 初始化检查引擎（进程级），并把"正在检查"的集合镜像到 UI 状态。
        // UI 层保留最短可见时长，避免小说检查过快导致加载圈闪一下就消失。
        UpdateCheckEngine.ensureInit(applicationContext)
        viewModelScope.launch {
            UpdateCheckEngine.inFlight.collect { set ->
                mirrorUpdateCheckingUrls(set)
            }
        }
    }

    fun setCategory(category: Int) {
        currentCategory = category
        updateUiList()
    }

    private data class TypeProbeResult(
        val type: Int,
        val title: String,
        val authorId: String,
        val sourceFid: String
    )

    /**
     * 后台探测未定收藏的类型。
     *
     * 只请求论坛线程 API 并更新本地收藏元数据，不打开额外页面或 WebView，
     * 因此左滑探测不会把用户从收藏页带走。
     */
    fun probeFavoriteTypeInBackground(favorite: Favorite) {
        if (getStoredFavoriteType(favorite) != 0) return
        val alreadyProbing = synchronized(typeProbeJobsLock) {
            typeProbeJobs[favorite.url]?.isActive == true
        }
        if (alreadyProbing) return

        val url = favorite.url
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = probeFavoriteTypeSuspend(favorite)
                if (result == null) return@launch

                applyTypeProbeResult(favorite, result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(logTag, "后台探测收藏类型失败: ${favorite.url}", e)
            } finally {
                synchronized(typeProbeJobsLock) { typeProbeJobs.remove(url) }
            }
        }
        synchronized(typeProbeJobsLock) { if (job.isActive) typeProbeJobs[url] = job }
    }

    suspend fun resolveFavoriteTypeForOpen(favorite: Favorite): Int {
        val inferredType = getStoredFavoriteType(favorite)
        if (inferredType != 0) return inferredType

        return withContext(Dispatchers.IO) {
            val result = runCatching { probeFavoriteTypeSuspend(favorite) }.getOrNull()
                ?: return@withContext 3
            applyTypeProbeResult(favorite, result)
            result.type
        }
    }

    fun getReliableFavoriteType(favorite: Favorite): Int = getStoredFavoriteType(favorite)

    private fun getStoredFavoriteType(favorite: Favorite): Int =
        FavoriteTypeResolver.reliableType(favorite)

    private suspend fun probeFavoriteTypeSuspend(favorite: Favorite): TypeProbeResult? {
        val tid = MangaTitleCleaner.extractTidFromUrl(favorite.url)
        if (tid == null) {
            return TypeProbeResult(
                type = 3,
                title = favorite.title,
                authorId = favorite.authorId ?: "",
                sourceFid = FavoriteTypeResolver.DETECTED_OTHER_SOURCE_FID
            )
        }

        val novelApi = YamiboRetrofit.getInstance().create(NovelApi::class.java)
        val resp = novelApi.getThreadMetaLight(tid).string()
        val json = JSON.parseObject(resp)
        val variables = json.getJSONObject("Variables") ?: return null
        val thread = variables.getJSONObject("thread") ?: return null
        val forumName = variables.getJSONObject("forum")?.getString("name") ?: ""
        val title = FavoriteUtil.decodeTitle(thread.getString("subject") ?: favorite.title)
        val authorId = thread.getString("authorid") ?: ""
        val fid = thread.getString("fid") ?: ""

        val type = when {
            fid in MANGA_FIDS || MANGA_SECTIONS.any { forumName.contains(it) } -> 2
            fid in NOVEL_FIDS || (forumName.isNotEmpty() && NOVEL_SECTIONS.any { forumName.contains(it) }) -> 1
            fid.isNotBlank() || forumName.isNotBlank() -> 3
            // 元数据不完整不能直接记为“其他”，否则一次临时异常会永久隐藏真实漫画/小说。
            else -> return null
        }
        val resolvedSourceFid = fid.ifBlank {
            if (type == 3) FavoriteTypeResolver.DETECTED_OTHER_SOURCE_FID else ""
        }

        return TypeProbeResult(
            type = type,
            title = title,
            authorId = authorId,
            sourceFid = resolvedSourceFid
        )
    }

    private suspend fun applyTypeProbeResult(favorite: Favorite, result: TypeProbeResult): Boolean {
        var updatedFavorite: Favorite? = null

        stateMutex.withLock {
            val old = allFavorites.find { it.url == favorite.url } ?: favorite
            var next = old

            when (result.type) {
                1 -> {
                    val aid = result.authorId.takeIf { it.isNotBlank() }
                    next = next.copy(type = 1, authorId = aid, sourceFid = result.sourceFid)

                    val cleanTitle = cleanNovelProbeTitle(result.title)
                    if (cleanTitle.isNotBlank()) next = next.copy(title = cleanTitle)
                }

                2 -> next = next.copy(type = 2, sourceFid = result.sourceFid)
                else -> next = next.copy(type = 3, sourceFid = result.sourceFid)
            }

            if (next != old) {
                allFavorites = allFavorites.map { if (it.url == next.url) next else it }
                updatedFavorite = next
            }
        }

        updatedFavorite?.let { updated ->
            FavoriteUtil.updateFavoriteSuspend(updated)
            when (updated.type) {
                1 -> {
                    MangaUpdateCheckUtil.removeProfileSuspend(updated.url)
                    OtherUpdateCheckUtil.removeProfileSuspend(updated.url)
                }

                2 -> {
                    NovelUpdateCheckUtil.removeProfileSuspend(updated.url)
                    OtherUpdateCheckUtil.removeProfileSuspend(updated.url)
                }

                3 -> {
                    NovelUpdateCheckUtil.removeProfileSuspend(updated.url)
                    MangaUpdateCheckUtil.removeProfileSuspend(updated.url)
                }
            }
        }
        withContext(Dispatchers.Main) { updateUiList() }
        return updatedFavorite != null
    }

    private fun cleanNovelProbeTitle(rawTitle: String): String =
        rawTitle.replace(
            Regex("\\s+[-—–_]+\\s+.*?(文學區|轻小说/译文区|TXT小说区|百合会|论坛).*$"),
            ""
        ).trim().ifBlank { rawTitle }

    private fun mirrorUpdateCheckingUrls(actualCheckingUrls: Set<String>) {
        val now = System.currentTimeMillis()

        actualCheckingUrls.forEach { url ->
            updateCheckHideJobs.remove(url)?.cancel()
            updateCheckVisibleSince.putIfAbsent(url, now)
        }

        val currentVisible = _uiState.value.checkingUpdateUrls
        val visibleWithActive = currentVisible + actualCheckingUrls
        if (visibleWithActive != currentVisible) {
            _uiState.update { it.copy(checkingUpdateUrls = visibleWithActive) }
        }

        val removedUrls = visibleWithActive - actualCheckingUrls
        removedUrls.forEach { url ->
            val shownFor = now - (updateCheckVisibleSince[url] ?: now)
            val remainMs = (MIN_UPDATE_CHECK_VISIBLE_MS - shownFor).coerceAtLeast(0L)

            if (remainMs == 0L) {
                updateCheckVisibleSince.remove(url)
                updateCheckHideJobs.remove(url)?.cancel()
                _uiState.update { it.copy(checkingUpdateUrls = it.checkingUpdateUrls - url) }
            } else if (updateCheckHideJobs[url]?.isActive != true) {
                updateCheckHideJobs[url] = viewModelScope.launch {
                    delay(remainMs)
                    if (!UpdateCheckEngine.isChecking(url)) {
                        updateCheckVisibleSince.remove(url)
                        _uiState.update { it.copy(checkingUpdateUrls = it.checkingUpdateUrls - url) }
                    }
                    updateCheckHideJobs.remove(url)
                }
            }
        }
    }

    private fun updateUiList() {
        val currentState = _uiState.value
        val baseList = if (currentState.isInManageMode) {
            allFavorites
        } else {
            allFavorites.filter { !it.isHidden }
        }

        val pendingFiltered = if (pendingDeleteUrls.isNotEmpty()) {
            baseList.filter { it.url !in pendingDeleteUrls }
        } else {
            baseList
        }

        val supportedOnly = pendingFiltered.filter { favorite ->
            when (favorite.type) {
                0, 3 -> true
                1, 2 -> FavoriteTypeResolver.reliableType(favorite) == favorite.type
                else -> false
            }
        }

        val filteredList = if (currentCategory == -1) {
            supportedOnly
        } else {
            supportedOnly.filter { it.type == currentCategory }
        }
        val orderedList = FavoriteUtil.orderPinnedFavoritesFirst(filteredList)

        val categoryCounts = mapOf(
            -1 to supportedOnly.size,
            1 to supportedOnly.count { it.type == 1 },
            2 to supportedOnly.count { it.type == 2 },
            3 to supportedOnly.count { it.type == 3 }
        )

        _uiState.update {
            it.copy(
                favoriteList = orderedList,
                categoryCounts = categoryCounts
            )
        }
    }

    fun refreshList(showLoading: Boolean = true, isSmartSync: Boolean = false) {
        if (isSmartSync) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLast = currentTime - lastSmartSyncTime

            if (timeSinceLast < SMART_SYNC_COOLDOWN) {
                if (pendingSyncJob?.isActive == true) return
                val waitTime = SMART_SYNC_COOLDOWN - timeSinceLast
                pendingSyncJob = viewModelScope.launch {
                    delay(waitTime)
                    lastSmartSyncTime = System.currentTimeMillis()
                    executeActualRefresh(showLoading, isSmartSync = true)
                }
                return
            } else {
                lastSmartSyncTime = currentTime
            }
        } else {
            pendingSyncJob?.cancel()
            lastSmartSyncTime = System.currentTimeMillis()
        }

        executeActualRefresh(showLoading, isSmartSync)
    }

    private fun executeActualRefresh(showLoading: Boolean, isSmartSync: Boolean) {
        val requestedState = if (isSmartSync) FetchState.BACKGROUND else FetchState.MANUAL

        while (true) {
            val currentState = currentFetchState.get()
            // 只有确实存在存活的拉取任务时才拦截重复刷新；
            // 否则说明上一次刷新异常中断后状态泄漏（卡在 MANUAL），放行让新刷新接管，
            // 避免下拉刷新从此永远无响应。
            if ((currentState == requestedState || currentState == FetchState.MANUAL) &&
                fetchJob?.isActive == true
            ) {
                // 已有拉取在途（常见于返回前台触发的 showLoading=false 全量同步占用了 MANUAL 状态）。
                // 此时若是用户手动下拉刷新（showLoading=true），不能被静默去重——否则下拉指示器
                // 不会进入旋转态，看起来就是“图标不转、刷新没反应”。让指示器转起来跟随这次在途拉取
                // 一起收尾即可：在途任务的 generation 未变，完成时会调用 releaseStateIfCurrent 关闭它。
                if (showLoading) {
                    _uiState.update { it.copy(isRefreshing = true) }
                }
                return
            }
            if (currentFetchState.compareAndSet(currentState, requestedState)) break
        }

        val currentGen = fetchGeneration.incrementAndGet()

        if (showLoading) {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    refreshLoadedCount = 0,
                    refreshTotalCount = 0
                )
            }
        }

        CookieUtil.getCookie {
            fetchJob?.cancel()
            fetchJob = viewModelScope.launch(Dispatchers.IO) {
                fetchAllFavoritesSuspend(
                    isSmartSync = isSmartSync,
                    isBackground = isSmartSync,
                    generation = currentGen
                )
            }
        }

        // 看门狗：拉取链路 30 秒内没有正常收尾（如取 cookie 回调丢失、请求挂死）时强制释放，
        // 防止 isRefreshing 永远为 true、刷新图标卡住。正常完成时此调用是幂等的空操作。
        viewModelScope.launch {
            delay(30_000L)
            releaseStateIfCurrent(currentGen)
        }
    }

    private fun releaseStateIfCurrent(generation: Long) {
        if (fetchGeneration.get() == generation) {
            currentFetchState.set(FetchState.IDLE)
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun fetchAllFavoritesSuspend(
        isSmartSync: Boolean,
        isBackground: Boolean,
        generation: Long
    ) {
        val favoriteApi = YamiboRetrofit.getInstance().create(FavoriteApi::class.java)
        var currentPage = 1
        var currentTotalPages = 1
        var currentIsSmartSync = isSmartSync
        val accumulatedList = ArrayList<Favorite>()

        while (currentCoroutineContext().isActive && generation == fetchGeneration.get()) {
            try {
                val resp = favoriteApi.getMyFavThread(currentPage).string()
                if (generation != fetchGeneration.get()) break

                val json = JSON.parseObject(resp)
                val variables = json.getJSONObject("Variables")
                    ?: throw Exception("Missing Variables")
                prefetchFormHash = variables.getString("formhash") ?: prefetchFormHash
                // 登录态接口会带 member_uid，顺手存下当前用户 uid（屏蔽功能用来排除自己的内容）。
                CurrentUserUtil.save(variables.getString("member_uid"))
                val list = variables.getJSONArray("list")
                val pageList = mutableListOf<Favorite>()

                if (list != null && list.isNotEmpty()) {
                    for (i in 0 until list.size) {
                        val item = list.getJSONObject(i) ?: continue
                        val favId = item.getString("favid") ?: ""
                        val title = FavoriteUtil.decodeTitle(item.getString("title") ?: "")
                        val url = FavoriteUtil.normalizeUrl(item.getString("url") ?: "")

                        val favorite = Favorite(title, url)
                        favorite.favId = favId
                        pageList.add(favorite)
                    }
                }

                val serverCount = variables.getString("count")?.toIntOrNull()
                val perPage = variables.getString("perpage")?.toIntOrNull() ?: 20

                if (pageList.isNotEmpty()) {
                    val pendingUrls = TombstoneQueueUtil.getPendingUrls()
                    val safePageList = stateMutex.withLock {
                        pageList.filterNot { pendingUrls.contains(it.url) }
                    }

                    accumulatedList.addAll(safePageList)
                    val hasNewItems = FavoriteUtil.mergeFavoritesProgressiveSuspend(safePageList)

                    if (generation != fetchGeneration.get()) break

                    if (serverCount != null && serverCount >= 0) {
                        val loadedCount = ((currentPage - 1) * perPage + pageList.size)
                            .coerceAtMost(serverCount)
                        _uiState.update {
                            it.copy(
                                refreshLoadedCount = loadedCount,
                                refreshTotalCount = serverCount
                            )
                        }
                    }

                    if (currentPage == 1) {
                        val count = serverCount ?: 0
                        currentTotalPages = if (count > 0) {
                            (count + perPage - 1) / perPage
                        } else 1
                        val hasNextPage = currentPage < currentTotalPages

                        stateMutex.withLock {
                            if (allFavorites.size > count) {
                                currentIsSmartSync = false
                            } else if (!hasNextPage) {
                                currentIsSmartSync = false
                            }
                        }
                    }

                    val hasNextPage = currentPage < currentTotalPages

                    val shouldContinue = if (currentIsSmartSync) {
                        hasNewItems && hasNextPage
                    } else {
                        hasNextPage
                    }

                    if (shouldContinue) {
                        val dynamicDelay = if (isBackground) {
                            (1200L - ((currentTotalPages - 1) * 300L)).coerceIn(600L, 1000L)
                        } else 300L
                        delay(dynamicDelay)
                        currentPage++
                    } else {
                        if (!currentIsSmartSync) {
                            FavoriteUtil.cleanupDeletedFavoritesSuspend(accumulatedList)
                        }
                        break
                    }
                } else {
                    if (currentPage == 1) {
                        val isLoggedIn = !variables.getString("auth").isNullOrBlank()

                        if (!isLoggedIn) {
                            if (isFavoritePageVisible) {
                                withContext(Dispatchers.Main) {
                                    YamiboToast.show(
                                        context = applicationContext,
                                        message = "登录状态异常",
                                        durationMillis = 1500L
                                    )
                                }
                            }
                            break
                        }

                        // 只有服务端明确返回 count=0 才确认是空收藏，否则拒绝清理
                        val count = variables.getString("count")?.toIntOrNull() ?: -1
                        if (count == 0) {
                            FavoriteUtil.cleanupDeletedFavoritesSuspend(emptyList())
                        } else if (isFavoritePageVisible) {
                            withContext(Dispatchers.Main) {
                                YamiboToast.show(
                                    context = applicationContext,
                                    message = "网络状态异常",
                                    durationMillis = 1500L
                                )
                            }
                        }
                        break
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                break
            }
        }
        releaseStateIfCurrent(generation)
    }

    fun getEffectiveResumeStrategy(): RefreshStrategy {
        if (nextResumeStrategy == RefreshStrategy.SKIP) {
            val elapsed = System.currentTimeMillis() - lastNavigateTime
            if (elapsed > SMART_SYNC_TIMEOUT) {
                return RefreshStrategy.SMART
            }
        }
        return nextResumeStrategy
    }

    fun updateStrategyBeforeNavigation(type: Int) {
        lastNavigateTime = System.currentTimeMillis()

        nextResumeStrategy = when (type) {
            1 -> RefreshStrategy.SKIP
            2 -> RefreshStrategy.SKIP
            else -> RefreshStrategy.SMART
        }
    }

    fun updateMangaProgress(
        favoriteUrl: String,
        chapterUrl: String,
        chapterTitle: String,
        pageIndex: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                val updated = allFavorites.map { fav ->
                    if (fav.url == favoriteUrl) {
                        val updatedFav = fav.copy(
                            lastMangaUrl = chapterUrl,
                            lastChapter = chapterTitle,
                            lastPage = pageIndex
                        )
                        launch { FavoriteUtil.updateFavoriteSuspend(updatedFav) }
                        updatedFav
                    } else fav
                }
                allFavorites = updated
            }
            withContext(Dispatchers.Main) { updateUiList() }
        }
    }

    fun updateMangaCachedPages(
        favoriteUrl: String,
        cachedPages: Int,
        cachedBytes: Long = 0,
        cachedUrls: List<String> = emptyList()
    ) {
        if (cachedPages < 0) return
        viewModelScope.launch(Dispatchers.IO) {
            var updatedFavorite: Favorite? = null
            stateMutex.withLock {
                allFavorites = allFavorites.map { favorite ->
                    if (favorite.url == FavoriteUtil.normalizeUrl(favoriteUrl)) {
                        val mergedUrls = (favorite.mangaCacheUrls + cachedUrls)
                            .asSequence()
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .distinct()
                            .toList()
                            .takeLast(MANGA_CACHE_URL_LIMIT)
                        if (favorite.mangaCachedPages == cachedPages &&
                            favorite.mangaCacheBytes == cachedBytes &&
                            favorite.mangaCacheUrls == mergedUrls
                        ) {
                            return@map favorite
                        }
                        favorite.copy(
                            mangaCachedPages = cachedPages,
                            mangaCacheBytes = cachedBytes,
                            mangaCacheUrls = mergedUrls
                        ).also {
                            updatedFavorite = it
                        }
                    } else {
                        favorite
                    }
                }
            }
            updatedFavorite?.let { FavoriteUtil.updateFavoriteSuspend(it) }
            if (updatedFavorite != null) {
                withContext(Dispatchers.Main) { updateUiList() }
            }
        }
    }

    fun toggleManageMode() {
        val realCookie =
            android.webkit.CookieManager.getInstance().getCookie("https://bbs.yamibo.com") ?: ""
        val isLoggedIn = realCookie.contains("EeqY_2132_auth=")

        if (!isLoggedIn) return
        val newMode = !_uiState.value.isInManageMode
        _uiState.update { it.copy(
            isInManageMode = newMode,
            selectedItems = emptySet()
        ) }
        updateUiList()
    }

    fun toggleItemSelection(url: String) {
        if (!_uiState.value.isInManageMode) return

        val newSelections = _uiState.value.selectedItems.toMutableSet()
        if (newSelections.contains(url)) newSelections.remove(url)
        else newSelections.add(url)

        _uiState.update { it.copy(selectedItems = newSelections) }
    }

    /** 设置当前选中集合（用于「全选/取消全选」）。 */
    fun setSelectedItems(urls: Set<String>) {
        if (!_uiState.value.isInManageMode) return
        _uiState.update { it.copy(selectedItems = urls) }
    }

    fun hideSelectedItems() {
        val itemsToHide = _uiState.value.selectedItems
        if (itemsToHide.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            FavoriteUtil.updateHiddenStatus(itemsToHide, true)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedItems = emptySet()) }
            }
        }
    }

    fun unhideSelectedItems() {
        val itemsToUnhide = _uiState.value.selectedItems
        if (itemsToUnhide.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            FavoriteUtil.updateHiddenStatus(itemsToUnhide, false)

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedItems = emptySet()) }
            }
        }
    }


    fun deleteSelectedFavorites(onToast: (String) -> Unit) {
        val itemsToDeleteUrls = _uiState.value.selectedItems.toSet()
        if (itemsToDeleteUrls.isEmpty()) return

        val itemsToTombstone = allFavorites.filter {
            itemsToDeleteUrls.contains(it.url) && !it.favId.isNullOrEmpty()
        }

        if (itemsToTombstone.isEmpty()) {
            onToast("数据缺失 请刷新")
            return
        }

        val favIdsToDelete = itemsToTombstone.map { it.favId!! }
        val backupList = allFavorites

        viewModelScope.launch(Dispatchers.Main) {
            stateMutex.withLock {
                TombstoneQueueUtil.addItems(itemsToTombstone)

                pendingDeleteUrls.addAll(itemsToDeleteUrls)
                val updatedList = allFavorites.filterNot { itemsToDeleteUrls.contains(it.url) }
                allFavorites = updatedList
                FavoriteUtil.saveFavoriteOrder(updatedList)
            }
            _uiState.update { it.copy(selectedItems = emptySet(), isInManageMode = false) }
            updateUiList()
        }

        // 后台请求删除 + 自动清理本地缓存
        viewModelScope.launch(Dispatchers.IO) {
            val isSuccess =
                FavoriteDeleteUtil.deleteFavoritesBatch(prefetchFormHash, favIdsToDelete)
            stateMutex.withLock {
                // 无论成功还是失败，网络请求结束，移除黑名单
                pendingDeleteUrls.removeAll(itemsToDeleteUrls)

                if (!isSuccess) {
                    // 失败，回滚数据
                    allFavorites = backupList
                    FavoriteUtil.saveFavoriteOrder(backupList)
                }
            }
            if (isSuccess) {
                TombstoneQueueUtil.removeUrls(itemsToDeleteUrls)
                itemsToDeleteUrls.forEach { url ->
                    try {
                        val normalizedUrl = FavoriteUtil.normalizeUrl(url)

                        localCache.deleteNovelCompat(
                            primaryUrl = normalizedUrl,
                            aliasUrls = cacheAliasesForNormalizedUrl(normalizedUrl, url)
                        )
                    } catch (_: Exception) {
                    }
                    NovelUpdateCheckUtil.removeProfileSuspend(url)
                    MangaUpdateCheckUtil.removeProfileSuspend(url)
                    OtherUpdateCheckUtil.removeProfileSuspend(url)
                }
                refreshCacheInfo()
            } else {
                stateMutex.withLock {
                    TombstoneQueueUtil.removeUrls(itemsToDeleteUrls)
                    allFavorites = backupList
                    FavoriteUtil.saveFavoriteOrder(backupList)
                }
                withContext(Dispatchers.Main) {
                    updateUiList()
                    onToast("网络异常，删除失败")
                }
            }
        }
    }

    private fun refreshCacheInfo(index: Map<String, LocalCacheUtil.CacheIndex>) {
        try {
            val cacheInfoMap = mutableMapOf<String, CacheInfo>()

            index.forEach { (rawUrl, novelCache) ->
                if (novelCache.pages.isEmpty()) return@forEach

                val normalizedUrl = FavoriteUtil.normalizeUrl(rawUrl)
                val totalPages = novelCache.pages.size
                val totalSize = novelCache.pages.values.sumOf { it.fileSize }
                val pagesWithImages = novelCache.pages.values.count { it.hasImages }

                val old = cacheInfoMap[normalizedUrl]
                cacheInfoMap[normalizedUrl] = old?.copy(
                    totalPages = old.totalPages + totalPages,
                    totalSize = old.totalSize + totalSize,
                    pagesWithImages = old.pagesWithImages + pagesWithImages,
                    title = old.title ?: novelCache.title
                )
                    ?: CacheInfo(
                        url = normalizedUrl,
                        totalPages = totalPages,
                        totalSize = totalSize,
                        pagesWithImages = pagesWithImages,
                        title = novelCache.title
                    )
            }

            _uiState.update { it.copy(cacheInfoMap = cacheInfoMap) }
        } catch (e: Exception) {
            Log.e(logTag, "从内存索引刷新缓存信息失败", e)
            _uiState.update { it.copy(cacheInfoMap = emptyMap()) }
        }
    }

    data class CacheInfo(
        val url: String,
        val totalPages: Int,
        val totalSize: Long,
        val pagesWithImages: Int,
        val title: String? = null
    )

    fun refreshCacheInfo() = refreshCacheInfo(localCache.index.value)

    fun getCacheInfo(callback: (Map<String, CacheInfo>) -> Unit) {
        refreshCacheInfo(localCache.index.value)
        callback(_uiState.value.cacheInfoMap)
    }

    private fun cacheAliasesForNormalizedUrl(normalizedUrl: String, originalUrl: String): List<String> {
        val absoluteUrl = org.shirakawatyu.yamibo.novel.util.reader.ReaderReturnBridge
            .toAbsoluteBbsUrl(normalizedUrl)

        val aliasesFromIndex = localCache.index.value.keys.filter { rawKey ->
            rawKey != normalizedUrl && FavoriteUtil.normalizeUrl(rawKey) == normalizedUrl
        }

        return buildList {
            add(originalUrl)
            add(absoluteUrl)
            addAll(aliasesFromIndex)
        }
            .map { it.trim() }
            .filter { it.isNotBlank() && it != normalizedUrl }
            .distinct()
    }

    fun clearAllCache() {
        viewModelScope.launch {
            try {
                localCache.clearAllCache()
            } catch (e: Exception) {
                Log.e(logTag, "清除所有缓存失败", e)
            }
        }
    }

    fun getDirectoryList(callback: (List<org.shirakawatyu.yamibo.novel.bean.MangaDirectory>) -> Unit) {
        viewModelScope.launch {
            val repo = org.shirakawatyu.yamibo.novel.repository.DirectoryRepository.getInstance(
                applicationContext
            )
            viewModelScope.launch(Dispatchers.Main) { callback(repo.getAllDirectories()) }
        }
    }

    fun deleteDirectory(cleanName: String, callback: () -> Unit) {
        viewModelScope.launch {
            org.shirakawatyu.yamibo.novel.repository.DirectoryRepository.getInstance(
                applicationContext
            ).deleteDirectory(cleanName)
            viewModelScope.launch(Dispatchers.Main) { callback() }
        }
    }

    fun clearAllDirectories(callback: () -> Unit) {
        viewModelScope.launch {
            org.shirakawatyu.yamibo.novel.repository.DirectoryRepository.getInstance(
                applicationContext
            ).clearAllDirectories()
            viewModelScope.launch(Dispatchers.Main) { callback() }
        }
    }

    fun moveToTop(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FavoriteUtil.moveUrlToTopSuspend(url)
        }
    }

    fun unpinToOriginal(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FavoriteUtil.restoreUrlToOriginalSuspend(url)
        }
    }

    fun clearFavoriteCache(favorite: Favorite, onToast: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val normalizedUrl = FavoriteUtil.normalizeUrl(favorite.url)
                localCache.deleteNovelCompat(
                    primaryUrl = normalizedUrl,
                    aliasUrls = cacheAliasesForNormalizedUrl(normalizedUrl, favorite.url)
                )

                if (favorite.type == 2) {
                    val cachedUrls = stateMutex.withLock {
                        allFavorites.firstOrNull { it.url == normalizedUrl }
                            ?.mangaCacheUrls
                            ?: favorite.mangaCacheUrls
                    }
                    val usedLegacyFallback = cachedUrls.isEmpty()
                    if (usedLegacyFallback) {
                        CacheMaintenance.clearImages(applicationContext)
                    } else {
                        MangaImagePipeline.evictAll(applicationContext, cachedUrls)
                    }
                    stateMutex.withLock {
                        allFavorites = allFavorites.map { item ->
                            if (usedLegacyFallback || item.url == normalizedUrl) {
                                item.copy(
                                    mangaCachedPages = 0,
                                    mangaCacheBytes = 0,
                                    mangaCacheUrls = emptyList()
                                )
                            } else {
                                item
                            }
                        }
                        FavoriteUtil.saveFavoriteOrder(allFavorites)
                    }
                }
            }.onSuccess {
                refreshCacheInfo()
                withContext(Dispatchers.Main) {
                    updateUiList()
                    onToast(
                        if (favorite.type == 2) "漫画图片缓存已清理"
                        else "缓存已清理"
                    )
                }
            }.onFailure { error ->
                Log.e(logTag, "清理 ${favorite.url} 的缓存失败", error)
                withContext(Dispatchers.Main) {
                    onToast("缓存清理失败")
                }
            }
        }
    }

    fun deleteFavorite(favorite: Favorite, onToast: (String) -> Unit) {
        val favId = favorite.favId
        if (favId.isNullOrBlank()) {
            onToast("收藏数据缺失，请刷新后重试")
            return
        }

        val url = favorite.url
        val backupList = allFavorites
        viewModelScope.launch(Dispatchers.Main) {
            stateMutex.withLock {
                TombstoneQueueUtil.addItems(listOf(favorite))
                pendingDeleteUrls.add(url)
                allFavorites = allFavorites.filterNot { it.url == url }
                FavoriteUtil.saveFavoriteOrder(allFavorites)
            }
            updateUiList()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val success = FavoriteDeleteUtil.deleteFavoritesBatch(prefetchFormHash, listOf(favId))
            stateMutex.withLock {
                pendingDeleteUrls.remove(url)
                if (!success) {
                    allFavorites = backupList
                    FavoriteUtil.saveFavoriteOrder(backupList)
                }
            }

            if (success) {
                TombstoneQueueUtil.removeUrls(setOf(url))
                NovelUpdateCheckUtil.removeProfileSuspend(url)
                MangaUpdateCheckUtil.removeProfileSuspend(url)
                OtherUpdateCheckUtil.removeProfileSuspend(url)
            } else {
                TombstoneQueueUtil.removeUrls(setOf(url))
                withContext(Dispatchers.Main) {
                    updateUiList()
                    onToast("网络异常，删除失败")
                }
            }
        }
    }

    /**
     * 阅读器收藏按钮：未收藏则添加当前帖子，已收藏则取消收藏。
     * 以本地收藏数据（DataStore）为准判断当前状态，避免收藏页未打开时 allFavorites 为空导致误判。
     */
    fun toggleFavorite(url: String, title: String, tid: String, onToast: (String) -> Unit) {
        if (GlobalData.currentUid.isBlank()) {
            onToast("请先登录后再收藏")
            return
        }
        val normalizedUrl = FavoriteUtil.normalizeUrl(url)
        viewModelScope.launch(Dispatchers.IO) {
            val existing = FavoriteUtil.getFavoriteMapSuspend()[normalizedUrl]
            withContext(Dispatchers.Main) {
                if (existing != null) {
                    deleteFavorite(existing, onToast)
                } else {
                    addFavoriteInternal(normalizedUrl, title, tid, onToast)
                }
            }
        }
    }

    /**
     * 添加收藏：先调论坛接口收藏帖子，成功后写入本地收藏，再后台静默同步补齐 favId 等远端元数据
     * （取消收藏依赖 favId）。
     */
    private fun addFavoriteInternal(
        normalizedUrl: String,
        title: String,
        tid: String,
        onToast: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = FavoriteAddUtil.addThreadFavorite(tid)
            withContext(Dispatchers.Main) {
                if (success) {
                    stateMutex.withLock {
                        if (allFavorites.none { it.url == normalizedUrl }) {
                            allFavorites = listOf(Favorite(title, normalizedUrl)) + allFavorites
                            FavoriteUtil.saveFavoriteOrder(allFavorites)
                        }
                    }
                    updateUiList()
                    onToast("收藏成功")
                    // 后台静默同步，补齐 favId/标题等远端元数据
                    refreshList(showLoading = false)
                } else {
                    onToast("收藏失败，请稍后重试")
                }
            }
        }
    }

    /**
     * 后台重试离线删除任务
     */
    private suspend fun retryPendingDeletesQuietly() {
        withContext(Dispatchers.IO) {
            val pendingEntries = TombstoneQueueUtil.getPendingEntries()
            if (pendingEntries.isEmpty()) return@withContext

            val favIdsToDelete = pendingEntries.mapNotNull {
                it.substringAfter("|", "").takeIf { id -> id.isNotBlank() }
            }

            if (favIdsToDelete.isNotEmpty()) {
                val isSuccess = FavoriteDeleteUtil.deleteFavoritesBatch(null, favIdsToDelete)

                if (isSuccess) {
                    TombstoneQueueUtil.removeEntries(pendingEntries)
                    pendingEntries.forEach { entry ->
                        val url = entry.substringBefore("|")
                        NovelUpdateCheckUtil.removeProfileSuspend(url)
                        MangaUpdateCheckUtil.removeProfileSuspend(url)
                        OtherUpdateCheckUtil.removeProfileSuspend(url)
                    }
                }
            }
        }
    }

    fun setFavoriteTypeManually(
        favorite: Favorite,
        type: Int,
        onToast: (String) -> Unit = {}
    ) {
        if (type !in 1..3) return
        viewModelScope.launch(Dispatchers.IO) {
            val updatedFavorite = favorite.copy(
                type = type,
                sourceFid = FavoriteTypeResolver.MANUAL_SOURCE_FID
            )

            stateMutex.withLock {
                allFavorites = allFavorites.map {
                    if (it.url == updatedFavorite.url) updatedFavorite else it
                }
            }
            FavoriteUtil.updateFavoriteSuspend(updatedFavorite)
            NovelUpdateCheckUtil.removeProfileSuspend(updatedFavorite.url)
            MangaUpdateCheckUtil.removeProfileSuspend(updatedFavorite.url)
            OtherUpdateCheckUtil.removeProfileSuspend(updatedFavorite.url)

            withContext(Dispatchers.Main) {
                updateUiList()
                onToast(
                    when (type) {
                        1 -> "已设为小说"
                        2 -> "已设为漫画"
                        else -> "已设为其他，仅从收藏页过滤"
                    }
                )
            }
        }
    }

    fun checkUpdateAfterTypeProbe(favorite: Favorite) {
        val storedType = getStoredFavoriteType(favorite)
        if (storedType != 0) {
            val checkedFavorite = if (storedType == favorite.type) {
                favorite
            } else {
                favorite.copy(type = storedType)
            }
            when (storedType) {
                1 -> checkNovelUpdate(checkedFavorite)
                2 -> checkMangaUpdate(checkedFavorite)
                else -> checkOtherUpdate(checkedFavorite)
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { probeFavoriteTypeSuspend(favorite) }.getOrNull()
            if (result == null) {
                markUpdateCheckFinished(favorite.url, UpdateCheckResult.FAILURE)
                withContext(Dispatchers.Main) {
                    YamiboToast.show(
                        context = applicationContext,
                        message = "暂时无法识别收藏类型，请稍后重试"
                    )
                }
                return@launch
            }
            applyTypeProbeResult(favorite, result)
            val checkedFavorite = when (result.type) {
                1 -> {
                    val cleanTitle = cleanNovelProbeTitle(result.title).ifBlank { favorite.title }
                    favorite.copy(
                        type = 1,
                        title = cleanTitle,
                        authorId = result.authorId.takeIf { it.isNotBlank() },
                        sourceFid = result.sourceFid
                    )
                }

                2 -> favorite.copy(type = 2, sourceFid = result.sourceFid)
                else -> favorite.copy(type = 3, sourceFid = result.sourceFid)
            }

            when (checkedFavorite.type) {
                1 -> checkNovelUpdate(checkedFavorite)
                2 -> checkMangaUpdate(checkedFavorite)
                else -> checkOtherUpdate(checkedFavorite)
            }
        }
    }
    /**
     * 下拉刷新后的批量更新检查。
     *
     * @param categoryType 当前展示的分类：1 只查小说、2 只查漫画、3 只查其他，其余值查全部。
     * 结果不再走 YamiboToast，而是写入 [FavoriteState.batchRefreshResult]，
     * 由收藏页底部同一个胶囊展示（与“正在刷新”同位置同样式，仅文字不同）。
     */
    fun checkAllFavoritesForUpdates(categoryType: Int = -1) {
        val items = allFavorites.filter { favorite ->
            favorite.type in 1..3 && (categoryType !in 1..3 || favorite.type == categoryType)
        }
        val checkedUrls = items.map { it.url }.toSet()
        val scopeLabel = when (categoryType) {
            1 -> "小说"
            2 -> "漫画"
            3 -> "其他收藏"
            else -> "收藏"
        }
        UpdateCheckEngine.ensureInit(applicationContext)
        batchUpdateCheckJob?.cancel()
        if (items.isEmpty()) {
            _uiState.update {
                it.copy(
                    failedUpdateUrls = it.failedUpdateUrls - checkedUrls,
                    batchRefreshResult = "全部${scopeLabel}刷新完成"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isBatchChecking = true,
                batchRefreshResult = null,
                failedUpdateUrls = it.failedUpdateUrls - checkedUrls
            )
        }
        batchUpdateCheckJob = viewModelScope.launch(Dispatchers.IO) {
            val myJob = coroutineContext[Job]
            try {
                val failedUrls = linkedSetOf<String>()
                items.forEachIndexed { index, favorite ->
                    if (!isActive) return@launch
                    if (index > 0) delay(600)
                    val result = runUpdateCheckForFavorite(favorite, notify = false)
                    if (result == UpdateCheckResult.FAILURE) {
                        failedUrls += favorite.url
                    }
                }
                _uiState.update {
                    it.copy(
                        // 只按本轮实际检查过的条目增量更新失败集合，
                        // 分类刷新时不能把其它分类先前的失败记录抹掉。
                        failedUpdateUrls = (it.failedUpdateUrls - checkedUrls) + failedUrls,
                        batchRefreshResult = if (failedUrls.isEmpty()) {
                            "全部${scopeLabel}刷新完成"
                        } else {
                            "${failedUrls.size} 个${scopeLabel}更新失败，请点刷新图标重试"
                        }
                    )
                }
            } finally {
                // 只有自己仍是当前批量任务时才收起“正在刷新”，
                // 避免被新一轮批量检查取消后误清新任务的进行中状态。
                if (batchUpdateCheckJob === myJob) {
                    _uiState.update { it.copy(isBatchChecking = false) }
                }
            }
        }
    }

    fun clearBatchRefreshResult() {
        _uiState.update { it.copy(batchRefreshResult = null) }
    }

    fun retryFailedUpdateChecks() {
        val failedUrls = _uiState.value.failedUpdateUrls
        val items = allFavorites.filter { favorite ->
            favorite.url in failedUrls && favorite.type in 1..3
        }
        val retryUrls = items.map { it.url }.toSet()
        UpdateCheckEngine.ensureInit(applicationContext)
        batchUpdateCheckJob?.cancel()
        if (items.isEmpty()) {
            _uiState.update { it.copy(failedUpdateUrls = emptySet()) }
            YamiboToast.show(context = applicationContext, message = "没有需要重试的收藏")
            return
        }

        _uiState.update { it.copy(failedUpdateUrls = it.failedUpdateUrls - retryUrls) }
        batchUpdateCheckJob = viewModelScope.launch(Dispatchers.IO) {
            val stillFailedUrls = linkedSetOf<String>()
            items.forEachIndexed { index, favorite ->
                if (!isActive) return@launch
                if (index > 0) delay(600)
                val result = runUpdateCheckForFavorite(favorite, notify = false)
                if (result == UpdateCheckResult.FAILURE) {
                    stillFailedUrls += favorite.url
                }
            }
            _uiState.update {
                it.copy(failedUpdateUrls = (it.failedUpdateUrls - retryUrls) + stillFailedUrls)
            }
            withContext(Dispatchers.Main) {
                if (stillFailedUrls.isEmpty()) {
                    YamiboToast.show(context = applicationContext, message = "失败项已重试完成")
                } else {
                    YamiboToast.show(
                        context = applicationContext,
                        message = "仍有 ${stillFailedUrls.size} 个收藏更新失败"
                    )
                }
            }
        }
    }
    private suspend fun runUpdateCheckForFavorite(
        favorite: Favorite,
        notify: Boolean,
        overrideStrategy: MangaUpdateCheckStrategy? = null,
        overrideSearchKeyword: String? = null,
        overrideCleanBookName: String? = null
    ): UpdateCheckResult {
        return when (favorite.type) {
            1 -> UpdateCheckEngine.checkNovelAwait(favorite, notify)
            2 -> UpdateCheckEngine.checkMangaAwait(
                favorite,
                overrideStrategy,
                overrideSearchKeyword,
                overrideCleanBookName,
                notify
            )
            3 -> UpdateCheckEngine.checkOtherAwait(favorite, notify)
            else -> UpdateCheckResult.SKIPPED
        }
    }

    private fun markUpdateCheckStarted(url: String) {
        _uiState.update { it.copy(failedUpdateUrls = it.failedUpdateUrls - url) }
    }

    private fun markUpdateCheckFinished(url: String, result: UpdateCheckResult) {
        if (result == UpdateCheckResult.FAILURE) {
            _uiState.update { it.copy(failedUpdateUrls = it.failedUpdateUrls + url) }
        }
    }

    fun checkNovelUpdate(favorite: Favorite) {
        UpdateCheckEngine.ensureInit(applicationContext)
        viewModelScope.launch(Dispatchers.IO) {
            markUpdateCheckStarted(favorite.url)
            val result = runUpdateCheckForFavorite(favorite, notify = true)
            markUpdateCheckFinished(favorite.url, result)
        }
    }
    fun clearNovelUpdateCheckFlag(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NovelUpdateCheckUtil.clearUpdateFlagSuspend(url)
        }
    }

    fun checkOtherUpdate(favorite: Favorite) {
        UpdateCheckEngine.ensureInit(applicationContext)
        viewModelScope.launch(Dispatchers.IO) {
            markUpdateCheckStarted(favorite.url)
            val result = runUpdateCheckForFavorite(favorite, notify = true)
            markUpdateCheckFinished(favorite.url, result)
        }
    }

    fun checkMangaUpdate(
        favorite: Favorite,
        overrideStrategy: MangaUpdateCheckStrategy? = null,
        overrideSearchKeyword: String? = null,
        overrideCleanBookName: String? = null
    ) {
        UpdateCheckEngine.ensureInit(applicationContext)
        viewModelScope.launch(Dispatchers.IO) {
            markUpdateCheckStarted(favorite.url)
            val result = runUpdateCheckForFavorite(
                favorite,
                notify = true,
                overrideStrategy = overrideStrategy,
                overrideSearchKeyword = overrideSearchKeyword,
                overrideCleanBookName = overrideCleanBookName
            )
            markUpdateCheckFinished(favorite.url, result)
        }
    }
    fun clearMangaUpdateCheckFlag(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            MangaUpdateCheckUtil.clearUpdateFlagSuspend(url)
        }
    }

    fun getSearchCooldownRemainingMs(): Long {
        return 0L
    }

}

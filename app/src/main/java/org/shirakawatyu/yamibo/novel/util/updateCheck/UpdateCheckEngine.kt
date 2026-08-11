package org.shirakawatyu.yamibo.novel.util.updateCheck

import android.content.Context
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.DirectoryStrategy
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.bean.MangaUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.bean.MangaUpdateCheckStrategy
import org.shirakawatyu.yamibo.novel.bean.NovelUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.bean.OtherUpdateCheckProfile
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.MangaApi
import org.shirakawatyu.yamibo.novel.network.NovelApi
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.repository.DirectoryRepository

enum class UpdateCheckResult { SUCCESS, FAILURE, SKIPPED }

/** 手动检查确认没有新增内容时，视为用户已处理旧提示；后台检查则继续保留未读更新。 */
internal fun shouldKeepUnreadUpdate(
    hadUnreadUpdate: Boolean,
    detectedUpdate: Boolean,
    acknowledgeExistingUpdate: Boolean
): Boolean = detectedUpdate || (hadUnreadUpdate && !acknowledgeExistingUpdate)

/**
 * 应用级（进程生命周期）更新检查引擎。
 *
 * - 手动检查共用同一套执行逻辑与节流锁，避免重复实现。
 * - 通过 [inFlight] 暴露"正在检查"的 url 集合；UI 据此显示转圈。
 * - 所有结果都写入 DataStore；UI 通过各自的 Flow 刷新角标。
 * - notify=true 时弹 Toast；notify=false 时全程静默。
 */
object UpdateCheckEngine {

    // 进程级作用域：不随任何 ViewModel / Composition 销毁而取消，避免页面切换打断检查。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    fun ensureInit(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: error("UpdateCheckEngine 未初始化，请先调用 ensureInit(context)")

    // ---- 正在检查的 url 集合 ----
    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
    val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()

    fun isChecking(url: String): Boolean = _inFlight.value.contains(url)

    private val inFlightMutex = Mutex()

    private suspend fun tryEnter(url: String): Boolean =
        inFlightMutex.withLock {
            if (url in _inFlight.value) false
            else {
                _inFlight.value = _inFlight.value + url
                true
            }
        }

    private suspend fun leave(url: String) =
        inFlightMutex.withLock {
            _inFlight.value = _inFlight.value - url
        }

    // ---- 节流：按 url hash 的条带锁，同 url 必互斥，不同书大概率并行 ----
    private const val CHECK_LOCK_STRIPES = 16
    private val urlCheckLocks = Array(CHECK_LOCK_STRIPES) { Mutex() }
    private fun checkLockFor(url: String): Mutex =
        urlCheckLocks[(url.hashCode() and Int.MAX_VALUE) % CHECK_LOCK_STRIPES]

    // ---- 小说元数据请求节流（原 VM 里的逻辑搬来）----
    private val novelMetaRequestMutex = Mutex()
    private const val NOVEL_META_REQUEST_INTERVAL_MS = 400L
    @Volatile private var lastNovelMetaRequestStartedAt = 0L

    private fun extractTid(url: String): String? =
        Regex("tid=(\\d+)").find(url)?.groupValues?.get(1)

    private suspend fun toast(message: String) {
        val ctx = appContext ?: return
        withContext(Dispatchers.Main) {
            YamiboToast.show(context = ctx, message = message)
        }
    }

    // =========================================================================
    // 对外入口
    // =========================================================================

    /** 手动检查小说（来自滑动卡片）。 */
    fun checkNovel(favorite: Favorite, notify: Boolean = true) {
        if (favorite.type != 1) return
        scope.launch { checkNovelAwait(favorite, notify) }
    }

    suspend fun checkNovelAwait(
        favorite: Favorite,
        notify: Boolean = true
    ): UpdateCheckResult {
        if (favorite.type != 1) return UpdateCheckResult.SKIPPED
        return performNovel(favorite.url, favorite.title, favorite.authorId, notify)
    }

    /** 手动检查"其他"帖子（来自滑动卡片）。不需要 authorId。 */
    fun checkOther(favorite: Favorite, notify: Boolean = true) {
        if (favorite.type != 3) return
        scope.launch { checkOtherAwait(favorite, notify) }
    }

    suspend fun checkOtherAwait(
        favorite: Favorite,
        notify: Boolean = true
    ): UpdateCheckResult {
        if (favorite.type != 3) return UpdateCheckResult.SKIPPED
        return performOther(favorite.url, favorite.title, notify)
    }

    /** 手动检查漫画（来自滑动卡片 / 配置弹窗）。 */
    fun checkManga(
        favorite: Favorite,
        overrideStrategy: MangaUpdateCheckStrategy? = null,
        overrideSearchKeyword: String? = null,
        overrideCleanBookName: String? = null,
        notify: Boolean = true
    ) {
        if (favorite.type != 2) return
        scope.launch {
            checkMangaAwait(
                favorite,
                overrideStrategy,
                overrideSearchKeyword,
                overrideCleanBookName,
                notify
            )
        }
    }

    suspend fun checkMangaAwait(
        favorite: Favorite,
        overrideStrategy: MangaUpdateCheckStrategy? = null,
        overrideSearchKeyword: String? = null,
        overrideCleanBookName: String? = null,
        notify: Boolean = true
    ): UpdateCheckResult {
        if (favorite.type != 2) return UpdateCheckResult.SKIPPED
        return performManga(
            favorite.url,
            favorite.title,
            overrideStrategy,
            overrideSearchKeyword,
            overrideCleanBookName,
            notify
        )
    }
    // =========================================================================
    // 实际执行（设置/清除 inFlight + 真正的网络逻辑）
    // =========================================================================

    private suspend fun performNovel(
        url: String,
        title: String,
        authorId: String?,
        notify: Boolean
    ): UpdateCheckResult {
        if (!tryEnter(url)) {
            if (notify) toast("正在查询更新")
            return UpdateCheckResult.SKIPPED
        }
        return try {
            runNovelUpdateCheck(url, title, authorId, notify)
        } finally {
            leave(url)
        }
    }

    private suspend fun performManga(
        url: String,
        title: String,
        overrideStrategy: MangaUpdateCheckStrategy?,
        overrideSearchKeyword: String?,
        overrideCleanBookName: String?,
        notify: Boolean
    ): UpdateCheckResult {
        if (!tryEnter(url)) {
            if (notify) toast("正在查询更新")
            return UpdateCheckResult.SKIPPED
        }
        return try {
            runMangaUpdateCheck(url, title, overrideStrategy, overrideSearchKeyword, overrideCleanBookName, notify)
        } finally {
            leave(url)
        }
    }

    private suspend fun performOther(
        url: String,
        title: String,
        notify: Boolean
    ): UpdateCheckResult {
        if (!tryEnter(url)) {
            if (notify) toast("正在查询更新")
            return UpdateCheckResult.SKIPPED
        }
        return try {
            runOtherUpdateCheck(url, title, notify)
        } finally {
            leave(url)
        }
    }
    private suspend fun fetchOtherRepliesQueued(tid: String): Int? {
        return novelMetaRequestMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastNovelMetaRequestStartedAt
            if (elapsed in 0L until NOVEL_META_REQUEST_INTERVAL_MS) {
                delay(NOVEL_META_REQUEST_INTERVAL_MS - elapsed)
            }
            lastNovelMetaRequestStartedAt = System.currentTimeMillis()

            val novelApi = YamiboRetrofit.getInstance().create(NovelApi::class.java)
            val resp = novelApi.getThreadMetaLight(tid).string()
            val json = JSON.parseObject(resp)
            val thread = json.getJSONObject("Variables")?.getJSONObject("thread")
            thread?.getString("replies")?.toIntOrNull()
        }
    }

    private suspend fun fetchNovelRepliesQueued(tid: String, authorId: String?): Int? {
        val filteredReplies = if (!authorId.isNullOrBlank()) {
            try {
                novelMetaRequestMutex.withLock {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastNovelMetaRequestStartedAt
                    if (elapsed in 0L until NOVEL_META_REQUEST_INTERVAL_MS) {
                        delay(NOVEL_META_REQUEST_INTERVAL_MS - elapsed)
                    }
                    lastNovelMetaRequestStartedAt = System.currentTimeMillis()

                    val novelApi = YamiboRetrofit.getInstance().create(NovelApi::class.java)
                    val resp = novelApi.getThreadMeta(tid, authorId).string()
                    val json = JSON.parseObject(resp)
                    val thread = json.getJSONObject("Variables")?.getJSONObject("thread")
                    thread?.getString("replies")?.toIntOrNull()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        } else {
            null
        }
        if (filteredReplies != null) return filteredReplies
        return fetchOtherRepliesQueued(tid)
    }

    // ---- 小说检查（搬自原 FavoriteVM.runNovelUpdateCheck，新增 notify 门控）----
    private suspend fun runNovelUpdateCheck(
        url: String,
        title: String,
        authorId: String?,
        notify: Boolean
    ): UpdateCheckResult {
        val tid = extractTid(url)
        if (tid == null) {
            if (notify) toast("查询失败，无法识别帖子ID")
            return UpdateCheckResult.FAILURE
        }
        return checkLockFor(url).withLock {
            val profile = NovelUpdateCheckUtil.getMapSuspend()[url]
            try {
                val currentReplies = fetchNovelRepliesQueued(tid, authorId)
                if (currentReplies == null) {
                    if (notify) toast("查询失败：没有读取到回复数")
                    profile?.let { NovelUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis()) }
                    return@withLock UpdateCheckResult.FAILURE
                }
                val checkedAt = System.currentTimeMillis()
                if (profile == null) {
                    // 首次追踪：建立基线，不算"有更新"
                    NovelUpdateCheckUtil.saveProfileSuspend(
                        NovelUpdateCheckProfile(
                            title = title, url = url, authorId = authorId.orEmpty(),
                            savedReplies = currentReplies, hasUpdate = false, lastCheckTime = checkedAt
                        )
                    )
                    if (notify) toast("已记录当前更新状态")
                    return@withLock UpdateCheckResult.SUCCESS
                }
                val detectedUpdate = currentReplies > profile.savedReplies
                val keepUnreadUpdate = shouldKeepUnreadUpdate(
                    hadUnreadUpdate = profile.hasUpdate,
                    detectedUpdate = detectedUpdate,
                    acknowledgeExistingUpdate = notify
                )
                NovelUpdateCheckUtil.updateRepliesSuspend(
                    url = url, newReplies = currentReplies,
                    hasUpdate = keepUnreadUpdate, lastCheckTime = checkedAt
                )
                if (notify) toast(if (detectedUpdate) "检测到小说更新" else "没有检测到小说更新")
                UpdateCheckResult.SUCCESS
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 失败也记录 lastCheckTime，保留最近一次检查时间。
                profile?.let { NovelUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis()) }
                if (notify) toast("查询小说更新失败")
                UpdateCheckResult.FAILURE
            }
        }
    }

    // ---- "其他"帖子检查（不需要 authorId，使用 getThreadMetaLight）----
    private suspend fun runOtherUpdateCheck(
        url: String,
        title: String,
        notify: Boolean
    ): UpdateCheckResult {
        val tid = extractTid(url)
        if (tid == null) {
            if (notify) toast("查询失败，无法识别帖子ID")
            return UpdateCheckResult.FAILURE
        }

        return checkLockFor(url).withLock {
            val profile = OtherUpdateCheckUtil.getMapSuspend()[url]
            try {
                val currentReplies = fetchOtherRepliesQueued(tid)
                if (currentReplies == null) {
                    if (notify) toast("查询失败：没有读取到回复数")
                    profile?.let { OtherUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis()) }
                    return@withLock UpdateCheckResult.FAILURE
                }
                val checkedAt = System.currentTimeMillis()
                if (profile == null) {
                    OtherUpdateCheckUtil.saveProfileSuspend(
                        OtherUpdateCheckProfile(
                            title = title, url = url,
                            savedReplies = currentReplies, hasUpdate = false, lastCheckTime = checkedAt
                        )
                    )
                    if (notify) toast("已记录当前更新状态")
                    return@withLock UpdateCheckResult.SUCCESS
                }
                val detectedUpdate = currentReplies > profile.savedReplies
                val keepUnreadUpdate = shouldKeepUnreadUpdate(
                    hadUnreadUpdate = profile.hasUpdate,
                    detectedUpdate = detectedUpdate,
                    acknowledgeExistingUpdate = notify
                )
                OtherUpdateCheckUtil.updateRepliesSuspend(
                    url = url, newReplies = currentReplies,
                    hasUpdate = keepUnreadUpdate, lastCheckTime = checkedAt
                )
                if (notify) toast(if (detectedUpdate) "检测到更新" else "没有检测到更新")
                UpdateCheckResult.SUCCESS
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                profile?.let { OtherUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis()) }
                if (notify) toast("查询更新失败")
                UpdateCheckResult.FAILURE
            }
        }
    }

    // ---- 漫画检查（搬自原 FavoriteVM.runMangaUpdateCheck，新增 notify 门控）----
    private suspend fun runMangaUpdateCheck(
        url: String,
        title: String,
        overrideStrategy: MangaUpdateCheckStrategy?,
        overrideSearchKeyword: String?,
        overrideCleanBookName: String?,
        notify: Boolean
    ): UpdateCheckResult {
        val ctx = requireContext()
        val tid = extractTid(url)
        if (tid == null) {
            if (notify) toast("查询失败，无法识别帖子ID")
            return UpdateCheckResult.FAILURE
        }

        return checkLockFor(url).withLock {
            val repo = DirectoryRepository.getInstance(ctx)
            val oldProfile = MangaUpdateCheckUtil.getMapSuspend()[url]
            try {
                var mangaDir = if (oldProfile != null) {
                    repo.getDirectoryByCleanName(oldProfile.cleanBookName)
                        ?: repo.getAllDirectories().find { dir -> dir.chapters.any { it.tid == tid } }
                } else {
                    repo.getAllDirectories().find { dir -> dir.chapters.any { it.tid == tid } }
                }

                if (mangaDir == null) {
                    val mangaApi = YamiboRetrofit.getInstance().create(MangaApi::class.java)
                    val resp = mangaApi.getThreadDetailApi(tid).string()
                    val json = JSON.parseObject(resp)
                    val postlist = json.getJSONObject("Variables")?.getJSONArray("postlist")
                    val message = postlist?.getJSONObject(0)?.getString("message")
                    if (message.isNullOrBlank()) {
                        if (notify) toast("初始化漫画目录失败")
                        return@withLock UpdateCheckResult.FAILURE
                    }
                    val html = "<div class=\"message\">$message</div>"
                    mangaDir = repo.initDirectoryForThread(tid, url, title, html)
                }

                if (overrideCleanBookName != null && overrideCleanBookName.isNotBlank() &&
                    overrideCleanBookName != mangaDir.cleanBookName
                ) {
                    mangaDir = repo.renameAndMergeDirectory(
                        mangaDir,
                        overrideCleanBookName,
                        mangaDir.translationGroup.orEmpty(),
                        mangaDir.publisherName ?: mangaDir.publisherUid.orEmpty(),
                        tid
                    )
                }

                val strategy = overrideStrategy
                    ?: oldProfile?.strategy
                    ?: if (mangaDir.strategy == DirectoryStrategy.TAG)
                        MangaUpdateCheckStrategy.TAG else MangaUpdateCheckStrategy.SEARCH

                val keyword = overrideSearchKeyword ?: oldProfile?.searchKeyword ?: mangaDir.searchKeyword
                val cleanBookName = overrideCleanBookName ?: oldProfile?.cleanBookName ?: mangaDir.cleanBookName
                val baseChapterCount = oldProfile?.savedChapterCount ?: mangaDir.chapters.size
                val baseLatestTid = oldProfile?.savedLatestTid ?: (mangaDir.chapters.lastOrNull()?.tid ?: "")

                val isFirstCheck = oldProfile == null
                if (isFirstCheck) {
                    MangaUpdateCheckUtil.saveProfileSuspend(
                        MangaUpdateCheckProfile(
                            title = title, url = url, cleanBookName = cleanBookName,
                            searchKeyword = keyword, strategy = strategy,
                            savedChapterCount = baseChapterCount, savedLatestTid = baseLatestTid,
                            hasUpdate = false, lastCheckTime = 0L
                        )
                    )
                } else {
                    val existing = oldProfile!!
                    if (existing.searchKeyword != keyword || existing.strategy != strategy) {
                        MangaUpdateCheckUtil.saveProfileSuspend(
                            existing.copy(title = title, searchKeyword = keyword, strategy = strategy)
                        )
                    }
                }

                val dirForUpdate = if (keyword != mangaDir.searchKeyword) {
                    mangaDir.copy(searchKeyword = keyword)
                } else mangaDir

                val forceSearch = strategy == MangaUpdateCheckStrategy.SEARCH
                val result = repo.manuallyUpdateDirectory(dirForUpdate, forceSearch = forceSearch, currentTid = tid)
                if (result.isFailure) {
                    MangaUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis())
                    if (notify) toast("查询漫画更新失败")
                    return@withLock UpdateCheckResult.FAILURE
                }

                val updatedDir = result.getOrThrow().directory
                val newCount = updatedDir.chapters.size
                val newLatestTid = updatedDir.chapters.lastOrNull()?.tid ?: ""
                val snapshotCount = if (newCount > 0) newCount else baseChapterCount
                val snapshotLatestTid = newLatestTid.ifEmpty { baseLatestTid }
                val detectedUpdate = newCount > baseChapterCount ||
                        (newLatestTid.isNotEmpty() && baseLatestTid.isNotEmpty() && newLatestTid != baseLatestTid)

                val keepUnreadUpdate = shouldKeepUnreadUpdate(
                    hadUnreadUpdate = oldProfile?.hasUpdate == true,
                    detectedUpdate = !isFirstCheck && detectedUpdate,
                    acknowledgeExistingUpdate = notify
                )
                MangaUpdateCheckUtil.updateSnapshotSuspend(
                    url = url,
                    chapterCount = snapshotCount,
                    latestTid = snapshotLatestTid,
                    hasUpdate = keepUnreadUpdate,
                    lastCheckTime = System.currentTimeMillis(),
                    searchKeyword = keyword,
                    strategy = strategy,
                    cleanBookName = mangaDir.cleanBookName
                )
                if (notify) toast(
                    when {
                        isFirstCheck -> "已记录当前更新状态"
                        detectedUpdate -> "检测到漫画更新"
                        else -> "没有检测到漫画更新"
                    }
                )
                UpdateCheckResult.SUCCESS
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                MangaUpdateCheckUtil.updateCheckTimeSuspend(url, System.currentTimeMillis())
                if (notify) toast("查询漫画更新失败")
                UpdateCheckResult.FAILURE
            }
        }
    }
}

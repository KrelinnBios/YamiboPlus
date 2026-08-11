package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.shirakawatyu.yamibo.novel.bean.MangaHomeItem
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.MangaApi
import org.shirakawatyu.yamibo.novel.parser.MangaHtmlParser
import org.shirakawatyu.yamibo.novel.ui.state.MangaHomeState
import org.shirakawatyu.yamibo.novel.util.manga.MangaCoverSelector
import org.shirakawatyu.yamibo.novel.util.manga.MangaTitleCleaner

class MangaHomeVM : ViewModel() {
    companion object {
        val sections = listOf(
            "30" to "中文漫画",
            "37" to "漫画图源"
        )
        private val allowedFids = sections.map { it.first }

        // 搜索翻页上限与翻页间隔：限制总请求数并错峰，避免触发论坛搜索限流。
        private const val MAX_SEARCH_PAGES = 20
        private const val SEARCH_PAGE_INTERVAL_MS = 500L
    }

    private val api = YamiboRetrofit.getInstance().create(MangaApi::class.java)
    private val _uiState = MutableStateFlow(MangaHomeState())
    val uiState = _uiState.asStateFlow()
    private val coverSemaphore = Semaphore(6)
    // tid -> 封面 URL 的内存缓存：切换分区、翻页、返回首页时复用，避免重复请求帖子详情接口。
    private val coverCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var refreshVersion = 0L
    private var searchFormHash: String? = null
    private var lastSearchAt = 0L

    init {
        refresh()
    }

    fun setSection(fid: String) {
        if (fid == _uiState.value.selectedFid || fid !in allowedFids) return
        _uiState.update { it.copy(selectedFid = fid, query = "") }
        refresh()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun submitSearch() {
        refresh()
    }

    fun clearSearch() {
        if (_uiState.value.query.isBlank()) return
        _uiState.update { it.copy(query = "") }
        refresh()
    }

    fun refresh() {
        val requestState = _uiState.value
        val version = ++refreshVersion
        viewModelScope.launch(Dispatchers.IO) {
            if (version != refreshVersion) return@launch
            _uiState.update {
                it.copy(
                    page = 1,
                    isLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    error = null
                )
            }
            runCatching {
                if (requestState.query.isBlank()) {
                    loadForumPage(requestState.selectedFid, 1)
                } else {
                    search(requestState.selectedFid, requestState.query)
                }
            }
                .onSuccess { loaded ->
                    if (version != refreshVersion) return@onSuccess
                    // 已缓存的封面直接套上，立即显示；其余逐张异步补。
                    val deduped = loaded.distinctBy(MangaHomeItem::tid)
                        .map { it.copy(coverUrl = coverCache[it.tid]) }
                    _uiState.update {
                        it.copy(
                            items = deduped,
                            isLoading = false,
                            hasMore = loaded.isNotEmpty()
                        )
                    }
                    loadCoversProgressive(deduped.take(16).filter { it.coverUrl == null }, version)
                }
                .onFailure { error ->
                    if (version != refreshVersion) return@onFailure
                    _uiState.update {
                        it.copy(
                            items = emptyList(),
                            isLoading = false,
                            error = friendlyError(error, "漫画列表加载失败")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isNotBlank()) return
        val nextPage = state.page + 1
        val requestFid = state.selectedFid
        val requestVersion = refreshVersion
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }
            runCatching { loadForumPage(requestFid, nextPage) }
                .onSuccess { loaded ->
                    if (requestVersion != refreshVersion ||
                        _uiState.value.selectedFid != requestFid
                    ) return@onSuccess
                    val appended = loaded.map { it.copy(coverUrl = coverCache[it.tid]) }
                    _uiState.update {
                        it.copy(
                            items = (it.items + appended).distinctBy(MangaHomeItem::tid),
                            page = nextPage,
                            isLoadingMore = false,
                            hasMore = loaded.isNotEmpty()
                        )
                    }
                    loadCoversProgressive(appended.take(10).filter { it.coverUrl == null }, requestVersion)
                }
                .onFailure { error ->
                    if (requestVersion != refreshVersion ||
                        _uiState.value.selectedFid != requestFid
                    ) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = friendlyError(error, "加载更多失败")
                        )
                    }
                }
        }
    }

    // 网络类异常（stream was reset / 超时 / DNS / 连接失败等）原始信息对用户没意义，
    // 还会把"stream was reset: PROTOCOL_ERROR"这种吓人的英文直接抛到漫画首页。
    // 统一换成可操作的中文提示；IllegalStateException 携带的是给用户看的中文提示（如"请先登录"），原样保留。
    private fun friendlyError(e: Throwable, fallback: String): String {
        if (e is IllegalStateException) return e.message?.takeIf(String::isNotBlank) ?: fallback
        val msg = e.message.orEmpty()
        val isNetwork = e is java.io.IOException ||
                msg.contains("stream was reset", ignoreCase = true) ||
                msg.contains("PROTOCOL_ERROR", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true)
        return if (isNetwork) "网络不太稳定，下拉重试一下" else fallback
    }

    private suspend fun loadForumPage(fid: String, page: Int): List<MangaHomeItem> {
        val json = JSON.parseObject(api.getForumDisplay(fid, page).string())
        val message = json.getJSONObject("Message")?.getString("messageval")
        if (!message.isNullOrBlank()) {
            throw IllegalStateException("请先在个人页登录后查看漫画版区")
        }
        val variables = json.getJSONObject("Variables")
            ?: throw IllegalStateException("论坛返回数据不完整")
        searchFormHash = variables.getString("formhash")?.takeIf(String::isNotBlank)
            ?: searchFormHash
        val forumName = variables.getJSONObject("forum")?.getString("name").orEmpty()
        val announcementTypeIds = variables.getJSONObject("threadtypes")
            ?.getJSONObject("types")
            ?.entries
            ?.filter { (_, value) -> value.toString().contains("公告") }
            ?.map { it.key }
            ?.toSet()
            .orEmpty()
        val threads = variables.getJSONArray("forum_threadlist") ?: return emptyList()
        return (0 until threads.size).mapNotNull { index ->
            val thread = threads.getJSONObject(index)
            if (thread.getIntValue("displayorder") > 0) return@mapNotNull null
            if (thread.getString("typeid") in announcementTypeIds) return@mapNotNull null
            val tid = thread.getString("tid")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = thread.getString("subject").orEmpty()
            if (MangaTitleCleaner.isAdministrativeThread(title)) return@mapNotNull null
            MangaHomeItem(
                tid = tid,
                title = title,
                url = threadUrl(tid),
                authorName = thread.getString("author").orEmpty(),
                date = thread.getString("dateline").orEmpty(),
                forumName = forumName
            )
        }
    }

    private suspend fun search(fid: String, rawQuery: String): List<MangaHomeItem> {
        val query = rawQuery.trim()
        if (query.isBlank()) return emptyList()
        val forumKeyword = MangaTitleCleaner.getForumSearchKeyword(query)
        val now = System.currentTimeMillis()
        if (now - lastSearchAt < 10_000L) {
            throw IllegalStateException("搜索过于频繁，请稍后再试")
        }
        val formHash = searchFormHash ?: run {
            val json = JSON.parseObject(api.getForumDisplay(fid, 1).string())
            json.getJSONObject("Variables")?.getString("formhash")
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("无法获取搜索校验信息，请重新登录")
        }
        searchFormHash = formHash
        lastSearchAt = now

        val html = api.searchForum(
            formHash = formHash,
            fids = listOf(fid),
            keyword = forumKeyword
        ).string()
        if (MangaHtmlParser.isFloodControlOrError(html)) {
            throw IllegalStateException("搜索过于频繁，请稍后再试")
        }

        val allItems = MangaHtmlParser.parseListHtml(html).toMutableList()
        val searchId = MangaHtmlParser.extractSearchId(html)
        val totalPages = MangaHtmlParser.extractTotalPages(html).coerceAtMost(MAX_SEARCH_PAGES)
        if (searchId != null && totalPages > 1 && allItems.isNotEmpty()) {
            for (page in 2..totalPages) {
                // 翻页之间留出间隔，避免连续快速请求触发论坛限流（444 / 防灌水）。
                delay(SEARCH_PAGE_INTERVAL_MS)
                // 单页失败（网络/444/防灌水）只截断后续翻页，保留已拿到的结果，
                // 绝不让一页出错把整次搜索清空（表现为“搜不出来”或“只有一部分”）。
                val pageHtml = runCatching {
                    api.searchForumPage(searchid = searchId, page = page).string()
                }.getOrNull() ?: break
                if (MangaHtmlParser.isFloodControlOrError(pageHtml)) break
                val pageItems = MangaHtmlParser.parseListHtml(pageHtml)
                if (pageItems.isEmpty()) break
                allItems += pageItems
            }
        }

        return allItems
            .asSequence()
            .filterNot { MangaTitleCleaner.isAdministrativeThread(it.rawTitle) }
            .filter {
                MangaTitleCleaner.matchesSearchQuery(
                    "${it.rawTitle} ${it.authorName.orEmpty()}",
                    query
                )
            }
            .distinctBy { it.tid }
            .map { chapter ->
                MangaHomeItem(
                    tid = chapter.tid,
                    title = chapter.rawTitle,
                    url = threadUrl(chapter.tid),
                    authorName = chapter.authorName.orEmpty(),
                    forumName = sections.firstOrNull { it.first == fid }?.second.orEmpty()
                )
            }
            .toList()
    }

    // 封面逐张异步加载：每拿到一张就单独刷新对应条目，不再等整批拿齐，首页明显更快出图。
    private fun loadCoversProgressive(items: List<MangaHomeItem>, version: Long) {
        items.forEach { item ->
            viewModelScope.launch(Dispatchers.IO) {
                coverSemaphore.withPermit {
                    if (version != refreshVersion) return@withPermit
                    val cover = coverCache[item.tid]
                        ?: runCatching { findFirstImage(item.tid) }.getOrNull()
                        ?: return@withPermit
                    coverCache[item.tid] = cover
                    if (version != refreshVersion) return@withPermit
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.tid == item.tid && it.coverUrl == null) {
                                    it.copy(coverUrl = cover)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun findFirstImage(tid: String): String? {
        val json = JSON.parseObject(api.getThreadDetailApi(tid).string())
        val variables = json.getJSONObject("Variables") ?: return null
        val threadAuthorId = variables.getJSONObject("thread")?.getString("authorid").orEmpty()
        val posts = variables.getJSONArray("postlist") ?: return null
        val messages = mutableListOf<String>()
        val attachmentUrls = mutableListOf<String>()

        for (index in 0 until posts.size) {
            val post = posts.getJSONObject(index) ?: continue
            val postAuthorId = post.getString("authorid").orEmpty()
            if (index != 0 && threadAuthorId.isNotBlank() && postAuthorId != threadAuthorId) continue

            messages += post.getString("message").orEmpty()
            val attachments = post.getJSONObject("attachments") ?: continue
            attachments.keys.forEach { key ->
                val attachment = attachments.getJSONObject(key) ?: return@forEach
                MangaCoverSelector.attachmentImageUrl(
                    urlPrefix = attachment.getString("url"),
                    attachmentPath = attachment.getString("attachment")
                )?.let(attachmentUrls::add)
            }
        }

        return MangaCoverSelector.firstCoverUrl(messages, attachmentUrls)
    }

    private fun threadUrl(tid: String) =
        "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=$tid&mobile=2"
}

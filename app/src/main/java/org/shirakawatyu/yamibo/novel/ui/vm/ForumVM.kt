package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.repository.ForumRepository
import org.shirakawatyu.yamibo.novel.ui.state.ForumSort
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.state.ForumThreadState
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import java.io.IOException

class ForumVM(
    private val repository: ForumRepository = ForumRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForumState())
    val uiState = _uiState.asStateFlow()
    private var requestVersion = 0L

    init {
        refresh()
    }

    fun openForum(forum: ForumBoard) {
        if (_uiState.value.selectedForum?.id == forum.id) return
        _uiState.update { it.copy(selectedForum = forum, threads = emptyList(), page = 1) }
        refresh()
    }

    fun showForumIndex() {
        requestVersion++
        _uiState.update {
            it.copy(
                selectedForum = null,
                threads = emptyList(),
                page = 1,
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
                error = null
            )
        }
        if (_uiState.value.categories.isEmpty()) refresh()
    }

    fun setSort(sort: ForumSort) {
        if (_uiState.value.sortBy == sort) return
        _uiState.update { it.copy(sortBy = sort, threads = emptyList(), page = 1) }
        refresh()
    }

    fun setFilterType(typeId: String?) {
        if (_uiState.value.filterType == typeId) return
        _uiState.update { it.copy(filterType = typeId, threads = emptyList(), page = 1) }
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        val selectedForum = state.selectedForum
        val version = ++requestVersion
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null, page = 1) }
        if (selectedForum == null) refreshBanners(version)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (selectedForum == null) {
                    repository.getForumIndex()
                } else {
                    val filter = if (state.filterType != null) "typeid" else null
                    repository.getThreads(
                        forumId = selectedForum.id,
                        page = 1,
                        orderBy = state.sortBy.apiValue,
                        filter = filter,
                        typeId = state.filterType
                    )
                }
            }.onSuccess { result ->
                if (version != requestVersion) return@onSuccess
                _uiState.update { state ->
                    when (result) {
                        is org.shirakawatyu.yamibo.novel.bean.forum.ForumIndex -> state.copy(
                            categories = result.categories,
                            isLoading = false
                        )
                        is org.shirakawatyu.yamibo.novel.bean.forum.ForumThreadPage -> state.copy(
                            selectedForum = result.forum.copy(headImageUrl = result.forum.headImageUrl ?: state.selectedForum?.headImageUrl),
                            threads = result.threads.distinctBy { it.id },
                            page = result.page,
                            totalPages = result.totalPages,
                            hasMore = result.hasMore,
                            isLoading = false,
                            availableTypes = result.availableTypes
                        )
                        else -> state.copy(isLoading = false)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (version != requestVersion) return@onFailure
                _uiState.update {
                    it.copy(isLoading = false, error = friendlyError(error))
                }
            }
        }
    }

    private fun refreshBanners(version: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.getForumBanners() }.onSuccess { banners ->
                if (version == requestVersion) {
                    _uiState.update { it.copy(banners = banners) }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                AppErrorLog.record("首页头图获取失败：${error.message}")
            }
        }
    }

    fun goToPage(targetPage: Int) {
        val state = _uiState.value
        val forum = state.selectedForum ?: return
        val page = targetPage.coerceIn(1, state.totalPages.coerceAtLeast(1))
        if (state.isLoading || (page == state.page && state.threads.isNotEmpty())) return
        val version = ++requestVersion
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val filter = if (state.filterType != null) "typeid" else null
                repository.getThreads(
                    forumId = forum.id,
                    page = page,
                    orderBy = state.sortBy.apiValue,
                    filter = filter,
                    typeId = state.filterType
                )
            }.onSuccess { result ->
                if (version != requestVersion || _uiState.value.selectedForum?.id != forum.id) return@onSuccess
                _uiState.update {
                    it.copy(
                        selectedForum = result.forum.copy(headImageUrl = result.forum.headImageUrl ?: state.selectedForum?.headImageUrl),
                        threads = result.threads.distinctBy { thread -> thread.id },
                        page = result.page,
                        totalPages = result.totalPages,
                        hasMore = result.hasMore,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (version != requestVersion) return@onFailure
                _uiState.update { it.copy(isLoading = false, error = friendlyError(error)) }
            }
        }
    }
    private fun friendlyError(error: Throwable): String {
        AppErrorLog.record("论坛错误：${error.message}")
        if (error is IOException) return "网络不太稳定，下拉重试一下"
        return translateDiscuzError(error.message?.takeIf(String::isNotBlank) ?: "论坛加载失败")
    }
}

private val discuzErrorTranslations = mapOf(
    "viewperm_none_nopermission" to "查无此区，此区已关闭",
    "group_nopermission" to "抱歉，您无权访问",
    "forum_nopermission" to "查无此区，此区已关闭",
)

private fun translateDiscuzError(raw: String): String {
    if (!raw.startsWith("mobile:")) return raw
    val code = raw.removePrefix("mobile:").trim()
    return discuzErrorTranslations[code] ?: raw
}
class ForumThreadVM(
    private val threadId: String,
    private val repository: ForumRepository = ForumRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForumThreadState())
    val uiState = _uiState.asStateFlow()
    private var requestVersion = 0L

    init {
        refresh()
    }

    fun refresh() {
        val version = ++requestVersion
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null, page = 1) }
        viewModelScope.launch(Dispatchers.IO) {
            val authorId = _uiState.value.takeIf { it.onlyOriginalPoster }?.thread?.author?.id
            runCatching { repository.getPosts(threadId, 1, authorId) }
                .onSuccess { result ->
                    if (version != requestVersion) return@onSuccess
                    _uiState.update {
                        it.copy(
                            thread = result.thread,
                            posts = result.posts,
                            page = result.page,
                            totalPages = result.totalPages,
                            hasMore = result.hasMore,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (version != requestVersion) return@onFailure
                    _uiState.update { it.copy(isLoading = false, error = threadError(error)) }
                }
        }
    }

    fun toggleOriginalPosterOnly() {
        val state = _uiState.value
        if (state.thread?.author?.id.isNullOrBlank()) return
        _uiState.update { it.copy(onlyOriginalPoster = !it.onlyOriginalPoster) }
        refresh()
    }

    fun toggleReverseOrder() {
        _uiState.update { it.copy(reverseOrder = !it.reverseOrder) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.page + 1
        val version = requestVersion
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val authorId = state.takeIf { it.onlyOriginalPoster }?.thread?.author?.id
            runCatching { repository.getPosts(threadId, nextPage, authorId) }
                .onSuccess { result ->
                    if (version != requestVersion) return@onSuccess
                    _uiState.update {
                        it.copy(
                            thread = result.thread,
                            posts = (it.posts + result.posts).distinctBy { post -> post.id },
                            page = result.page,
                            totalPages = result.totalPages,
                            hasMore = result.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = threadError(error))
                    }
                }
        }
    }

    private fun threadError(error: Throwable): String {
        AppErrorLog.record("帖子错误：${error.message}")
        return if (error is IOException) "网络不太稳定，下拉重试一下"
        else translateDiscuzError(error.message?.takeIf(String::isNotBlank) ?: "主题加载失败")
    }

    companion object {
        fun factory(threadId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ForumThreadVM(threadId) as T
            }
    }
}

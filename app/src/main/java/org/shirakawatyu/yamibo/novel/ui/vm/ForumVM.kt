package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.repository.ForumRepository
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.state.ForumThreadState
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

    fun refresh() {
        val selectedForum = _uiState.value.selectedForum
        val version = ++requestVersion
        _uiState.update {
            it.copy(isLoading = true, isLoadingMore = false, error = null, page = 1)
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (selectedForum == null) {
                    repository.getForumIndex()
                } else {
                    repository.getThreads(selectedForum.id, 1)
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
                            selectedForum = result.forum,
                            threads = result.threads.distinctBy { it.id },
                            page = result.page,
                            hasMore = result.hasMore,
                            isLoading = false
                        )
                        else -> state.copy(isLoading = false)
                    }
                }
            }.onFailure { error ->
                if (version != requestVersion) return@onFailure
                _uiState.update {
                    it.copy(isLoading = false, error = friendlyError(error))
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val forum = state.selectedForum ?: return
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val version = requestVersion
        val nextPage = state.page + 1
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.getThreads(forum.id, nextPage) }
                .onSuccess { result ->
                    if (version != requestVersion || _uiState.value.selectedForum?.id != forum.id) {
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            threads = (it.threads + result.threads).distinctBy { thread -> thread.id },
                            page = result.page,
                            hasMore = result.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { error ->
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = friendlyError(error))
                    }
                }
        }
    }

    private fun friendlyError(error: Throwable): String {
        if (error is IOException) return "网络不太稳定，下拉重试一下"
        return error.message?.takeIf(String::isNotBlank) ?: "论坛加载失败"
    }
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
            runCatching { repository.getPosts(threadId, 1) }
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.page + 1
        val version = requestVersion
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.getPosts(threadId, nextPage) }
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
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = threadError(error))
                    }
                }
        }
    }

    private fun threadError(error: Throwable): String =
        if (error is IOException) "网络不太稳定，下拉重试一下"
        else error.message?.takeIf(String::isNotBlank) ?: "主题加载失败"

    companion object {
        fun factory(threadId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ForumThreadVM(threadId) as T
            }
    }
}

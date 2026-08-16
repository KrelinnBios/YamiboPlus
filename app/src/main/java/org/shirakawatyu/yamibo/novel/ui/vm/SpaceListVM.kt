package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpaceCategory
import org.shirakawatyu.yamibo.novel.repository.SpaceRepository

class SpaceListVM(
    private val uid: String = "",
    private val repository: SpaceRepository = SpaceRepository()
) : ViewModel() {

    data class TabState(
        val items: List<SpaceListItem> = emptyList(),
        val page: Int = 1,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val hasMore: Boolean = false,
        val previousUrl: String? = null,
        val nextUrl: String? = null,
        val categories: List<SpaceCategory> = emptyList()
    )

    val states = mutableStateMapOf<SpaceListRequest, TabState>()
    private val jobs = HashMap<SpaceListRequest, Job>()
    private val loadMoreJobs = HashMap<SpaceListRequest, Job>()

    fun stateFor(request: SpaceListRequest): TabState {
        return states[requestWithUid(request)] ?: TabState()
    }

    private fun requestWithUid(request: SpaceListRequest): SpaceListRequest =
        request.copy(uid = request.uid.ifBlank { uid })

    fun load(request: SpaceListRequest, refresh: Boolean = false) {
        val baseRequest = requestWithUid(request)
        val current = states[baseRequest] ?: TabState()
        if (!refresh && (current.isLoading || current.items.isNotEmpty())) return
        jobs[baseRequest]?.cancel()
        jobs[baseRequest] = viewModelScope.launch {
            states[baseRequest] = current.copy(
                isLoading = true,
                error = null,
                hasMore = false,
                page = 1,
                previousUrl = null,
                nextUrl = null
            )
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getList(baseRequest, 1)
                }
                val previous = states[baseRequest] ?: current
                states[baseRequest] = previous.copy(
                    items = result.items,
                    page = 1,
                    isLoading = false,
                    hasMore = result.nextUrl != null,
                    previousUrl = result.previousUrl,
                    nextUrl = result.nextUrl,
                    categories = result.categories
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val previous = states[baseRequest] ?: current
                states[baseRequest] = previous.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun loadMore(request: SpaceListRequest) {
        val baseRequest = requestWithUid(request)
        val current = states[baseRequest] ?: return
        if (current.isLoading || current.isLoadingMore || current.nextUrl == null) return
        loadMoreJobs[baseRequest]?.cancel()
        loadMoreJobs[baseRequest] = viewModelScope.launch {
            val nextUrl = current.nextUrl ?: return@launch
            val nextPage = current.page + 1
            states[baseRequest] = current.copy(isLoadingMore = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getListByUrl(baseRequest, nextUrl)
                }
                val now = states[baseRequest] ?: current
                states[baseRequest] = now.copy(
                    items = result.items,
                    page = nextPage,
                    isLoadingMore = false,
                    hasMore = result.nextUrl != null,
                    previousUrl = result.previousUrl,
                    nextUrl = result.nextUrl,
                    categories = result.categories
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val now = states[baseRequest] ?: current
                states[baseRequest] = now.copy(
                    isLoadingMore = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun loadPrevious(request: SpaceListRequest) {
        val baseRequest = requestWithUid(request)
        val current = states[baseRequest] ?: return
        if (current.isLoading || current.isLoadingMore || current.previousUrl == null) return
        loadMoreJobs[baseRequest]?.cancel()
        loadMoreJobs[baseRequest] = viewModelScope.launch {
            val previousUrl = current.previousUrl ?: return@launch
            val previousPage = (current.page - 1).coerceAtLeast(1)
            states[baseRequest] = current.copy(isLoadingMore = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getListByUrl(baseRequest, previousUrl)
                }
                val now = states[baseRequest] ?: current
                states[baseRequest] = now.copy(
                    items = result.items,
                    page = previousPage,
                    isLoadingMore = false,
                    hasMore = result.nextUrl != null,
                    previousUrl = result.previousUrl,
                    nextUrl = result.nextUrl,
                    categories = result.categories
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val now = states[baseRequest] ?: current
                states[baseRequest] = now.copy(
                    isLoadingMore = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    class Factory(private val uid: String = "") : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SpaceListVM::class.java)) {
                return SpaceListVM(uid) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

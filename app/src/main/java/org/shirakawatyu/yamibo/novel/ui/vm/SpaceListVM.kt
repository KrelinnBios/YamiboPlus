package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.BlogBatchOperation
import org.shirakawatyu.yamibo.novel.bean.space.SpaceCategory
import org.shirakawatyu.yamibo.novel.bean.space.SpaceFriendFilter
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
        , val friendFilters: List<SpaceFriendFilter> = emptyList()
    )

    val states = mutableStateMapOf<SpaceListRequest, TabState>()
    var actionBusy by mutableStateOf(false)
        private set
    private val jobs = HashMap<SpaceListRequest, Job>()
    private val loadMoreJobs = HashMap<SpaceListRequest, Job>()
    private val blogCategoryJobs = HashMap<SpaceListRequest, Job>()

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
                    , friendFilters = result.friendFilters
                )
                enrichFriendBlogCategories(baseRequest, result.items)
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

    fun loadMore(request: SpaceListRequest, append: Boolean = false) {
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
                    if (baseRequest.kind == org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind.USER_THREAD &&
                        baseRequest.type == "reply"
                    ) {
                        repository.getList(baseRequest, nextPage)
                    } else {
                        repository.getListByUrl(baseRequest, nextUrl)
                    }
                }
                val now = states[baseRequest] ?: current
                states[baseRequest] = now.copy(
                    items = if (append) now.items + result.items else result.items,
                    page = nextPage,
                    isLoadingMore = false,
                    hasMore = result.nextUrl != null,
                    previousUrl = result.previousUrl,
                    nextUrl = result.nextUrl,
                    categories = result.categories
                    , friendFilters = result.friendFilters
                )
                enrichFriendBlogCategories(baseRequest, result.items)
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
                    if (baseRequest.kind == org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind.USER_THREAD &&
                        baseRequest.type == "reply"
                    ) {
                        repository.getList(baseRequest, previousPage)
                    } else {
                        repository.getListByUrl(baseRequest, previousUrl)
                    }
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
                    , friendFilters = result.friendFilters
                )
                enrichFriendBlogCategories(baseRequest, result.items)
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

    fun submitDoingAction(
        request: SpaceListRequest,
        actionUrl: String,
        message: String? = null,
        onResult: (String, Boolean) -> Unit
    ) {
        val baseRequest = requestWithUid(request)
        val current = states[baseRequest] ?: return
        if (actionBusy || actionUrl.isBlank()) return
        actionBusy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.submitDoingAction(
                        request = baseRequest,
                        page = current.page,
                        actionUrl = actionUrl,
                        message = message
                    )
                }
                val latest = states[baseRequest] ?: current
                states[baseRequest] = latest.copy(
                    items = result.items,
                    error = null,
                    hasMore = result.nextUrl != null,
                    previousUrl = result.previousUrl,
                    nextUrl = result.nextUrl
                )
                onResult(if (message == null) "已删除" else "回复已发表", true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(e.message ?: "记录操作失败，请稍后重试", false)
            } finally {
                actionBusy = false
            }
        }
    }

    fun loadBlogMenuItem(
        item: SpaceListItem.Blog,
        onLoaded: (SpaceListItem.Blog) -> Unit
    ) {
        if (item.url.isBlank()) return
        viewModelScope.launch {
            val detail = try {
                withContext(Dispatchers.IO) { repository.getBlogDetail(item.url) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
            }
            onLoaded(
                item.copy(
                    category = detail.category.ifBlank { item.category },
                    editUrl = item.editUrl.ifBlank { detail.editUrl },
                    deleteUrl = item.deleteUrl.ifBlank { detail.deleteUrl },
                    stickUrl = item.stickUrl.ifBlank { detail.stickUrl },
                    tags = detail.tags
                )
            )
        }
    }

    fun submitBlogBatchAction(
        request: SpaceListRequest,
        items: List<SpaceListItem.Blog>,
        operation: BlogBatchOperation,
        onResult: (String, Boolean) -> Unit
    ) {
        val baseRequest = requestWithUid(request)
        val current = states[baseRequest] ?: return
        if (actionBusy || items.isEmpty()) return
        actionBusy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.performBlogBatchAction(items, operation)
                }
                if (result.succeeded > 0) {
                    val refreshed = withContext(Dispatchers.IO) {
                        repository.getList(baseRequest, current.page)
                    }
                    val latest = states[baseRequest] ?: current
                    states[baseRequest] = latest.copy(
                        items = refreshed.items,
                        isLoading = false,
                        error = null,
                        hasMore = refreshed.nextUrl != null,
                        previousUrl = refreshed.previousUrl,
                        nextUrl = refreshed.nextUrl,
                        categories = refreshed.categories.ifEmpty { latest.categories },
                        friendFilters = refreshed.friendFilters.ifEmpty { latest.friendFilters }
                    )
                }
                val actionName = when (operation) {
                    BlogBatchOperation.PUBLIC -> "设为全站可见"
                    BlogBatchOperation.FRIENDS -> "设为好友可见"
                    BlogBatchOperation.PRIVATE -> "设为自己可见"
                    BlogBatchOperation.PIN -> if (items.firstOrNull()?.isPinned == true) {
                        "取消置顶"
                    } else {
                        "置顶"
                    }
                    BlogBatchOperation.DELETE -> "删除"
                }
                val message = when {
                    result.failed == 0 -> "已${actionName} ${result.succeeded} 篇日志"
                    result.succeeded == 0 -> "${actionName}失败，请稍后重试"
                    else -> "已处理 ${result.succeeded} 篇，${result.failed} 篇失败"
                }
                onResult(message, result.succeeded > 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(e.message ?: "日志操作失败，请稍后重试", false)
            } finally {
                actionBusy = false
            }
        }
    }

    private fun enrichFriendBlogCategories(
        request: SpaceListRequest,
        items: List<SpaceListItem>
    ) {
        if (request.kind != SpacePageKind.BLOG || request.view != "we") return
        val blogs = items.filterIsInstance<SpaceListItem.Blog>()
            .filter { it.category.isBlank() }
        if (blogs.isEmpty()) return

        blogCategoryJobs[request]?.cancel()
        blogCategoryJobs[request] = viewModelScope.launch {
            val categories = withContext(Dispatchers.IO) {
                repository.getMissingBlogCategories(blogs)
            }
            if (categories.values.none(String::isNotBlank)) return@launch
            val current = states[request] ?: return@launch
            states[request] = current.copy(
                items = current.items.map { item ->
                    if (item is SpaceListItem.Blog && item.category.isBlank()) {
                        categories[item.blogId]
                            ?.takeIf(String::isNotBlank)
                            ?.let { item.copy(category = it) }
                            ?: item
                    } else {
                        item
                    }
                }
            )
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

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
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.forum.ForumBoard
import org.shirakawatyu.yamibo.novel.bean.forum.ForumCategory
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPoll
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostRating
import org.shirakawatyu.yamibo.novel.bean.forum.ForumRatePopout
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.repository.ForumRepository
import org.shirakawatyu.yamibo.novel.ui.state.ForumSort
import org.shirakawatyu.yamibo.novel.ui.state.ForumState
import org.shirakawatyu.yamibo.novel.ui.state.ForumThreadState
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import org.shirakawatyu.yamibo.novel.util.browser.ForumVerificationRequiredException
import org.shirakawatyu.yamibo.novel.util.favorite.FavoriteAddUtil
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

    /**
     * 判断板块当前是否已收藏（以首页「我收藏的版块」分类为数据源）
     */
    fun isForumFavorited(forumId: String): Boolean {
        return _uiState.value.categories
            .firstOrNull { it.id == FAVORITE_FORUMS_CATEGORY_ID }
            ?.forums?.any { it.id == forumId } == true
    }

    /**
     * 收藏/取消收藏板块。远端操作成功后直接更新本地 categories 中
     * 「我收藏的版块」分类，菜单与收藏板块列表随之自动刷新，无需重新拉取首页。
     */
    fun toggleForumFavorite(
        forum: ForumBoard,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (GlobalData.currentUid.isBlank()) {
            onResult(false, "请先登录后再收藏")
            return
        }
        val wasFavorited = isForumFavorited(forum.id)
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (wasFavorited) {
                FavoriteAddUtil.removeForumFavorite(forum.id)
            } else {
                FavoriteAddUtil.addForumFavorite(forum.id)
            }
            if (success) {
                _uiState.update { state ->
                    state.copy(categories = updateFavoriteCategory(state.categories, forum, wasFavorited))
                }
            }
            val message = when {
                !success && wasFavorited -> "取消收藏失败，请稍后重试"
                !success -> "收藏失败，请稍后重试"
                wasFavorited -> "已取消收藏"
                else -> "已收藏本版"
            }
            withContext(Dispatchers.Main) { onResult(success, message) }
        }
    }

    /**
     * 远端操作成功后同步「我收藏的版块」分类：取消时移除板块，收藏时插入到分类首位。
     */
    private fun updateFavoriteCategory(
        categories: List<ForumCategory>,
        forum: ForumBoard,
        wasFavorited: Boolean
    ): List<ForumCategory> {
        val index = categories.indexOfFirst { it.id == FAVORITE_FORUMS_CATEGORY_ID }
        if (wasFavorited) {
            if (index < 0) return categories
            val updated = categories[index].copy(
                forums = categories[index].forums.filterNot { it.id == forum.id }
            )
            return if (updated.forums.isEmpty()) {
                categories.filterIndexed { i, _ -> i != index }
            } else {
                categories.toMutableList().apply { set(index, updated) }
            }
        }
        val favoriteCategory = ForumCategory(
            id = FAVORITE_FORUMS_CATEGORY_ID,
            name = "我收藏的版块",
            forums = listOf(forum)
        )
        return if (index < 0) {
            listOf(favoriteCategory) + categories
        } else {
            val updated = categories[index].copy(
                forums = (listOf(forum) + categories[index].forums).distinctBy { it.id }
            )
            categories.toMutableList().apply { set(index, updated) }
        }
    }

    companion object {
        /** 与 [org.shirakawatyu.yamibo.novel.repository.ForumRepository] 生成的收藏板块分类 ID 保持一致 */
        private const val FAVORITE_FORUMS_CATEGORY_ID = "favorite-forums"
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
                            selectedForum = result.forum.copy(
                                headImageUrl = result.forum.headImageUrl ?: state.selectedForum?.headImageUrl,
                                todayPostCount = result.forum.todayPostCount,
                                rank = result.forum.rank ?: state.selectedForum?.rank
                            ),
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
                        selectedForum = result.forum.copy(
                            headImageUrl = result.forum.headImageUrl ?: forum.headImageUrl,
                            todayPostCount = result.forum.todayPostCount.takeIf { count -> count > 0 }
                                ?: forum.todayPostCount,
                            rank = result.forum.rank ?: forum.rank
                        ),
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
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                verificationUrl = null,
                page = 1
            )
        }
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
                            isLoading = false,
                            verificationUrl = null,
                            threadHtml = result.html
                        )
                    }
                }
                .onFailure { error ->
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = threadError(error),
                            verificationUrl = verificationUrl(error)
                        )
                    }
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

    fun goToPage(targetPage: Int) {
        val state = _uiState.value
        val page = targetPage.coerceIn(1, state.totalPages.coerceAtLeast(1))
        if (state.isLoading || (page == state.page && state.posts.isNotEmpty())) return
        val version = ++requestVersion
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                verificationUrl = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val authorId = state.takeIf { it.onlyOriginalPoster }?.thread?.author?.id
            runCatching { repository.getPosts(threadId, page, authorId) }
                .onSuccess { result ->
                    if (version != requestVersion) return@onSuccess
                    _uiState.update {
                        it.copy(
                            thread = result.thread,
                            posts = result.posts.distinctBy { post -> post.id },
                            page = result.page,
                            totalPages = result.totalPages,
                            hasMore = result.hasMore,
                            isLoading = false,
                            verificationUrl = null,
                            threadHtml = result.html
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = threadError(error),
                            verificationUrl = verificationUrl(error)
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.page + 1
        val version = requestVersion
        _uiState.update {
            it.copy(isLoadingMore = true, error = null, verificationUrl = null)
        }
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
                            isLoadingMore = false,
                            verificationUrl = null,
                            threadHtml = result.html.ifBlank { it.threadHtml }
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = threadError(error),
                            verificationUrl = verificationUrl(error)
                        )
                    }
                }
        }
    }

    fun votePoll(poll: ForumPoll, optionIds: List<String>, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.votePoll(poll, optionIds) }
                .onSuccess {
                    onResult("投票成功，正在刷新")
                    refresh()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onResult(error.message ?: "投票失败，请稍后重试")
                }
        }
    }

    fun submitComment(post: ForumPost, message: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.commentPost(post.threadId, post.id, message, post.commentForm) }
                .onSuccess {
                    onResult("点评已发表")
                    refresh()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onResult(error.message ?: "点评失败，请稍后重试")
                }
        }
    }

    /** 拉取评分弹窗（可选分值 / 常用理由 / formhash），供评分对话框使用。 */
    fun loadRatePopout(post: ForumPost, onResult: (Result<ForumRatePopout>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(
                runCatching {
                    repository.getRatePopout(
                        threadId = post.threadId,
                        postId = post.id,
                        fallbackFormHash = post.rateForm?.formHash
                    )
                }
            )
        }
    }

    /** 拉取楼层完整评分列表，供“查看全部评分”对话框使用。 */
    fun loadAllRatings(post: ForumPost, onResult: (Result<List<ForumPostRating>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(
                runCatching { repository.getAllRatings(post.threadId, post.id) }
            )
        }
    }

    fun submitRate(
        post: ForumPost,
        score: Int,
        reason: String,
        formHash: String?,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.ratePost(
                    threadId = post.threadId,
                    postId = post.id,
                    score = score,
                    reason = reason,
                    formHash = formHash ?: post.rateForm?.formHash
                )
            }
                .onSuccess {
                    onResult("评分已提交")
                    refresh()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onResult(error.message ?: "评分失败，请稍后重试")
                }
        }
    }

    fun submitReply(message: String, quotePost: ForumPost?, onResult: (String) -> Unit) {
        // 直接回复主题时提前校验最小字数（论坛普遍要求 21 字符），
        // 避免白跑一次网络请求；引用回复最终会拼上引用内容，交给服务端判定。
        if (quotePost == null && message.trim().length < MIN_REPLY_CHARS) {
            onResult("回复内容至少需要 $MIN_REPLY_CHARS 个字符（当前 ${message.trim().length} 个）")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.replyThread(
                    threadId = threadId,
                    forumId = _uiState.value.thread?.forumId.orEmpty(),
                    message = message,
                    quotePost = quotePost
                )
            }
                .onSuccess { result ->
                    onResult(result)
                    refreshToLastPage()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onResult(error.message ?: "回复失败，请稍后重试")
                }
        }
    }

    /**
     * 回复成功后新楼层位于主题末页，直接跳到末页展示，避免回到首页看不到刚发的回复。
     */
    fun refreshToLastPage() {
        val version = ++requestVersion
        _uiState.update {
            it.copy(isLoading = true, isLoadingMore = false, error = null, verificationUrl = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val authorId = _uiState.value.takeIf { it.onlyOriginalPoster }?.thread?.author?.id
            runCatching { repository.getPosts(threadId, 1, authorId) }
                .onSuccess { firstPage ->
                    if (version != requestVersion) return@onSuccess
                    val lastPage = firstPage.totalPages.coerceAtLeast(1)
                    if (lastPage <= 1) {
                        _uiState.update {
                            it.copy(
                                thread = firstPage.thread,
                                posts = firstPage.posts,
                                page = 1,
                                totalPages = firstPage.totalPages,
                                hasMore = firstPage.hasMore,
                                isLoading = false,
                                verificationUrl = null,
                                threadHtml = firstPage.html
                            )
                        }
                    } else {
                        runCatching { repository.getPosts(threadId, lastPage, authorId) }
                            .onSuccess { last ->
                                if (version != requestVersion) return@onSuccess
                                _uiState.update {
                                    it.copy(
                                        thread = last.thread,
                                        posts = last.posts.distinctBy { post -> post.id },
                                        page = last.page,
                                        totalPages = last.totalPages,
                                        hasMore = last.hasMore,
                                        isLoading = false,
                                        verificationUrl = null,
                                        threadHtml = last.html.ifBlank { it.threadHtml }
                                    )
                                }
                            }
                            .onFailure { error ->
                                if (error is CancellationException) throw error
                                if (version != requestVersion) return@onFailure
                                _uiState.update {
                                    it.copy(isLoading = false, error = threadError(error))
                                }
                            }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@onFailure
                    _uiState.update {
                        it.copy(isLoading = false, error = threadError(error))
                    }
                }
        }
    }

    private fun threadError(error: Throwable): String {
        AppErrorLog.record("帖子错误：${error.message}")
        if (error is ForumVerificationRequiredException) {
            return "论坛要求完成网页验证，验证后将自动重新加载原生页面"
        }
        return if (error is IOException) "网络不太稳定，下拉重试一下"
        else translateDiscuzError(error.message?.takeIf(String::isNotBlank) ?: "主题加载失败")
    }

    private fun verificationUrl(error: Throwable): String? =
        (error as? ForumVerificationRequiredException)?.targetUrl

    companion object {
        /** Discuz 回复最短字数（与论坛 minpostsize 默认一致，服务端仍会兜底校验）。 */
        private const val MIN_REPLY_CHARS = 21

        fun factory(threadId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ForumThreadVM(
                        threadId = threadId,
                        repository = ForumRepository()
                    ) as T
            }
    }
}

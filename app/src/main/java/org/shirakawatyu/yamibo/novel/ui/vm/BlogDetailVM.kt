package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.repository.SpaceRepository
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionRemoteClient
import org.shirakawatyu.yamibo.novel.util.blog.BlogReactionSnapshot

internal class BlogDetailVM(
    private val url: String,
    private val repository: SpaceRepository = SpaceRepository()
) : ViewModel() {
    val detail = mutableStateOf<BlogDetail?>(null)
    val isLoading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val reactionSnapshot = mutableStateOf<BlogReactionSnapshot?>(null)
    val reactionBusy = mutableStateOf(false)
    val reactionMessage = mutableStateOf<String?>(null)
    val commentBusy = mutableStateOf(false)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                detail.value = withContext(Dispatchers.IO) { repository.getBlogDetail(url) }
                loadReactions()
            } catch (e: Exception) {
                error.value = e.message ?: "日志加载失败"
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun loadReactions() {
        val current = detail.value ?: return
        reactionSnapshot.value = withContext(Dispatchers.IO) {
            runCatching {
                BlogReactionRemoteClient.fetchSnapshot(current.ownerUid, current.blogId)
            }.getOrNull()
        }
    }

    fun react(clickId: String) {
        val current = detail.value ?: return
        if (reactionBusy.value) return
        reactionBusy.value = true
        reactionMessage.value = null
        viewModelScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    BlogReactionRemoteClient.addReaction(
                        current.ownerUid,
                        current.blogId,
                        clickId
                    )
                }
                reactionSnapshot.value = update.snapshot
                reactionMessage.value = update.message
            } catch (e: Exception) {
                reactionMessage.value = e.message ?: "表态失败"
            } finally {
                reactionBusy.value = false
            }
        }
    }

    fun submitComment(message: String, onResult: (String, Boolean) -> Unit) {
        val current = detail.value ?: return
        val text = message.trim()
        if (text.isBlank() || commentBusy.value) return
        commentBusy.value = true
        viewModelScope.launch {
            try {
                detail.value = withContext(Dispatchers.IO) {
                    repository.submitBlogComment(url, current, text)
                }
                onResult("评论已发表", true)
            } catch (e: Exception) {
                onResult(e.message ?: "评论失败，请稍后重试", false)
            } finally {
                commentBusy.value = false
            }
        }
    }

    fun replyComment(comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment, message: String, onResult: (String, Boolean) -> Unit) =
        submitCommentAction(comment.replyUrl, message, "回复已发表", onResult)

    fun editComment(comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment, message: String, onResult: (String, Boolean) -> Unit) =
        submitCommentAction(comment.editUrl, message, "评论已修改", onResult)

    fun deleteComment(comment: org.shirakawatyu.yamibo.novel.bean.space.BlogComment, onResult: (String, Boolean) -> Unit) =
        submitCommentAction(comment.deleteUrl, null, "评论已删除", onResult)

    private fun submitCommentAction(
        actionUrl: String,
        message: String?,
        successMessage: String,
        onResult: (String, Boolean) -> Unit
    ) {
        if (actionUrl.isBlank() || commentBusy.value) return
        commentBusy.value = true
        viewModelScope.launch {
            try {
                detail.value = withContext(Dispatchers.IO) {
                    repository.submitBlogCommentAction(url, actionUrl, message)
                }
                onResult(successMessage, true)
            } catch (e: Exception) {
                onResult(e.message ?: "评论操作失败，请稍后重试", false)
            } finally {
                commentBusy.value = false
            }
        }
    }

    class Factory(private val url: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BlogDetailVM::class.java)) {
                return BlogDetailVM(url) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

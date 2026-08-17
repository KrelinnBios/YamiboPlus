package org.shirakawatyu.yamibo.novel.ui.vm

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.space.PrivateMessageConversation
import org.shirakawatyu.yamibo.novel.repository.SpaceRepository

internal class PrivateMessageVM(
    private val url: String,
    private val repository: SpaceRepository = SpaceRepository()
) : ViewModel() {
    val conversation = mutableStateOf<PrivateMessageConversation?>(null)
    val isLoading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val isSending = mutableStateOf(false)

    init {
        load(url)
    }

    fun load(targetUrl: String = url) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                conversation.value = withContext(Dispatchers.IO) {
                    repository.getPrivateMessageConversation(targetUrl)
                }
            } catch (e: Exception) {
                error.value = e.message ?: "私信加载失败"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadPage(pageUrl: String) {
        load(pageUrl)
    }

    fun send(message: String) {
        val current = conversation.value ?: return
        val text = message.trim()
        if (text.isEmpty() || isSending.value) return
        isSending.value = true
        viewModelScope.launch {
            try {
                conversation.value = withContext(Dispatchers.IO) {
                    repository.sendPrivateMessage(current, text)
                }
                error.value = null
            } catch (e: Exception) {
                error.value = e.message ?: "发送失败，请重试"
            } finally {
                isSending.value = false
            }
        }
    }

    class Factory(private val url: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PrivateMessageVM::class.java)) {
                return PrivateMessageVM(url) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

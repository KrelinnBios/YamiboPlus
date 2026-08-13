package org.shirakawatyu.yamibo.novel.ui.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.ProfileApi
import org.shirakawatyu.yamibo.novel.parser.ProfileApiParser
import org.shirakawatyu.yamibo.novel.ui.state.MinePageState
import org.shirakawatyu.yamibo.novel.util.CurrentUserUtil
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil
import java.io.IOException

class NativeMinePageVM(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MinePageState())
    val uiState = _uiState.asStateFlow()
    private val profileApi: ProfileApi = YamiboRetrofit.getInstance().create(ProfileApi::class.java)

    init {
        if (GlobalData.currentUid.isNotBlank()) refreshProfile()
        loadCacheSize()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = withContext(Dispatchers.IO) {
                    profileApi.getUserProfile()
                }
                val json = withContext(Dispatchers.IO) {
                    response.string()
                }
                val profile = withContext(Dispatchers.IO) {
                    ProfileApiParser.parseProfile(json)
                }
                _uiState.update { it.copy(profile = profile, isLoading = false) }
                withContext(Dispatchers.IO) {
                    CurrentUserUtil.saveProfile(profile.uid, profile.username, profile.avatarUrl)
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = "网络不太稳定，下拉重试一下") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun loadCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val imageSize = context.imageLoader.diskCache?.size ?: 0L
                val novelSize = LocalCacheUtil.getInstance(context).index.value
                    .values
                    .sumOf { cache -> cache.pages.values.sumOf { it.fileSize } }
                imageSize + novelSize
            }
            _uiState.update { it.copy(cacheSize = size) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            try {
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    context.imageLoader.diskCache?.clear()
                    LocalCacheUtil.getInstance(context).clearAllCache()
                }
                _uiState.update { it.copy(isClearingCache = false, cacheSize = 0L) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun logout() {
        CookieUtil.saveCookie("")
        GlobalData.currentCookie = ""
        CurrentUserUtil.clear()
        _uiState.update { it.copy(profile = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NativeMinePageVM::class.java)) {
                return NativeMinePageVM(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

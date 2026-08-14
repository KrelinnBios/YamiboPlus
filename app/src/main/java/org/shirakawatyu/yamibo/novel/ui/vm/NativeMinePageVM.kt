package org.shirakawatyu.yamibo.novel.ui.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import org.shirakawatyu.yamibo.novel.util.CurrentUserUtil
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.YamiboSession
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil
import java.io.IOException

class NativeMinePageVM(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MinePageState())
    val uiState = _uiState.asStateFlow()
    private val profileApi: ProfileApi = YamiboRetrofit.getInstance().create(ProfileApi::class.java)
    private var profileJob: Job? = null

    init {
        if (YamiboSession.hasAuthenticationCookie(GlobalData.currentCookie)) refreshProfile()
        loadCacheSize()
    }

    fun refreshProfile() {
        profileJob?.cancel()
        if (!YamiboSession.hasAuthenticationCookie(GlobalData.currentCookie)) {
            _uiState.update {
                it.copy(profile = null, isLoggedIn = false, isLoading = false, error = null)
            }
            return
        }
        profileJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoggedIn = true, isLoading = true, error = null) }
            try {
                val profile = withContext(Dispatchers.IO) {
                    val mobileProfile = runCatching {
                        ProfileApiParser.parseProfile(profileApi.getUserProfile().string())
                    }.getOrNull()
                    val htmlProfile = runCatching {
                        ProfileApiParser.parseProfileHtml(profileApi.getUserProfileHtml().string())
                    }.getOrNull()
                    when {
                        htmlProfile != null && mobileProfile != null ->
                            ProfileApiParser.mergeProfile(htmlProfile, mobileProfile)
                        htmlProfile != null -> htmlProfile
                        mobileProfile != null -> mobileProfile
                        else -> throw IOException("个人资料接口没有返回有效数据")
                    }
                }
                _uiState.update { it.copy(profile = profile, isLoading = false) }
                withContext(Dispatchers.IO) {
                    CurrentUserUtil.saveProfile(profile.uid, profile.username, profile.avatarUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // 已有认证 Cookie 说明登录成功；资料接口临时不可用不应把用户打回未登录页，
                // 也不记录为会反复打扰用户的登录错误。
                _uiState.update { it.copy(isLoggedIn = true, isLoading = false, error = null) }
            } catch (e: Exception) {
                AppErrorLog.record("我的页加载失败：${e.message}")
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
        profileJob?.cancel()
        CookieUtil.saveCookie("")
        GlobalData.currentCookie = ""
        GlobalData.sessionGeneration++
        YamiboSession.clearWebViewSession()
        CurrentUserUtil.clear()
        _uiState.update {
            it.copy(profile = null, isLoggedIn = false, isLoading = false, error = null)
        }
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

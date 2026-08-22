package org.shirakawatyu.yamibo.novel.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.bean.SignPageData
import org.shirakawatyu.yamibo.novel.repository.SignRepository
import org.shirakawatyu.yamibo.novel.util.AutoSignManager

data class SignPageState(
    val data: SignPageData? = null,
    val loading: Boolean = false,
    val signing: Boolean = false,
    val error: String? = null
)

class SignPageVM(
    private val repository: SignRepository = SignRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(SignPageState())
    val state = _state.asStateFlow()

    fun load(context: Context, year: Int? = null, month: Int? = null) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val data = repository.getPage(context, year, month)
                _state.value = _state.value.copy(data = data, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "签到页面加载失败"
                )
            }
        }
    }

    fun sign(context: Context) {
        if (_state.value.signing || _state.value.data?.signedToday == true) return
        viewModelScope.launch {
            _state.value = _state.value.copy(signing = true)
            try {
                AutoSignManager.checkAndSignIfNeeded(context, force = true)
                val current = _state.value.data
                val data = withContext(Dispatchers.IO) {
                    repository.getPage(context, current?.year, current?.month)
                }
                _state.value = _state.value.copy(data = data, signing = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    signing = false,
                    error = e.message ?: "签到失败，请稍后重试"
                )
            }
        }
    }
}

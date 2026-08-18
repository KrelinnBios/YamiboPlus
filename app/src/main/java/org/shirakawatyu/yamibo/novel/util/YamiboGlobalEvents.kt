package org.shirakawatyu.yamibo.novel.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 跨页面全局事件总线：签到被论坛安全验证拦截时发出事件，
 * 由 MainActivity 顶层的 SignBlockedDialogHost 统一弹窗并提供跳转。
 */
object YamiboGlobalEvents {
    private val _signBlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signBlocked = _signBlocked.asSharedFlow()

    fun notifySignBlocked() {
        _signBlocked.tryEmit(Unit)
    }
}

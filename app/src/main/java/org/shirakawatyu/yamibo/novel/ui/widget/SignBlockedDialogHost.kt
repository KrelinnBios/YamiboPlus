package org.shirakawatyu.yamibo.novel.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.util.YamiboGlobalEvents

/**
 * 兼容旧的签到拦截事件。头像已经提供手动签到入口，这里只显示轻量胶囊，
 * 不再用对话框打断当前操作。
 */
@Composable
fun SignBlockedDialogHost(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    LaunchedEffect(Unit) {
        YamiboGlobalEvents.signBlocked.collect {
            YamiboToast.show(message = "自动签到未完成，可点击头像手动签到")
        }
    }
}

package org.shirakawatyu.yamibo.novel.ui.widget

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog
import org.shirakawatyu.yamibo.novel.util.YamiboGlobalEvents

private const val SIGN_PAGE_URL = "https://bbs.yamibo.com/plugin.php?id=zqlj_sign&mobile=2"

/**
 * 签到被 WAF 拦截的全局提示：任何触发签到的地方被拦截时统一弹窗，
 * 并提供前往签到页完成验证的入口。
 */
@Composable
fun SignBlockedDialogHost(navController: NavController) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        YamiboGlobalEvents.signBlocked.collect { visible = true }
    }

    if (visible) {
        YamiboAlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("签到未完成") },
            text = { Text("论坛安全验证拦截了签到请求，前往签到页完成验证后即可继续签到。") },
            confirmButton = {
                TextButton(onClick = {
                    visible = false
                    navController.navigate("OtherWebPage/" + Uri.encode(SIGN_PAGE_URL))
                }) {
                    Text("前往签到页")
                }
            },
            dismissButton = {
                TextButton(onClick = { visible = false }) {
                    Text("取消")
                }
            }
        )
    }
}

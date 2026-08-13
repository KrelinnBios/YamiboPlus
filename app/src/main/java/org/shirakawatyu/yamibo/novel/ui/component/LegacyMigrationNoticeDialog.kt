package org.shirakawatyu.yamibo.novel.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.util.LegacyMigrationNoticeUtil

/** 首次安装后展示一次的 300 Lite 数据迁移提示。 */
@Composable
fun LegacyMigrationNoticeDialog(onOpenBackup: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = withContext(Dispatchers.IO) {
            !LegacyMigrationNoticeUtil.hasShown(context)
        }
    }

    if (!visible) return

    fun close(openBackup: Boolean) {
        if (isClosing) return
        isClosing = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                LegacyMigrationNoticeUtil.markShown(context)
            }
            visible = false
            isClosing = false
            if (openBackup) onOpenBackup()
        }
    }

    AlertDialog(
        onDismissRequest = { close(openBackup = false) },
        title = { Text("迁移 300 Lite 数据") },
        text = {
            Text(
                "300 Plus 使用新的应用包名，300 Lite 的本地数据不会自动迁移。" +
                    "如需保留，请先在 300 Lite 设置页导出备份，再到 300 Plus 的“我的 → 应用设置 → 备份管理”导入。"
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isClosing,
                onClick = { close(openBackup = true) }
            ) {
                Text("前往导入")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isClosing,
                onClick = { close(openBackup = false) }
            ) {
                Text("关闭")
            }
        }
    )
}

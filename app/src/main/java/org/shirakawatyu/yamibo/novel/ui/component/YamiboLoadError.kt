package org.shirakawatyu.yamibo.novel.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.AppErrorLog
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

@Composable
fun YamiboLoadError(
    title: String,
    message: String = "页面加载失败，请检查网络后刷新",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    buttonText: String = "刷新页面"
) {
    var showLogDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = LanguageModeUtil.displayText("加载失败"),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = LanguageModeUtil.displayText(title),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = LanguageModeUtil.displayText(message),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = LanguageModeUtil.displayText("刷新"),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(LanguageModeUtil.displayText(buttonText))
        }
        if (!AppErrorLog.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showLogDialog = true }) {
                Text(LanguageModeUtil.displayText("查看错误日志"))
            }
        }
    }
    if (showLogDialog) {
        ErrorLogDialogVisible(onDismiss = { showLogDialog = false })
    }
}

@Composable
private fun ErrorLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logText = remember { AppErrorLog.snapshot() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageModeUtil.displayText("错误日志")) },
        text = {
            Text(
                text = logText.ifBlank { LanguageModeUtil.displayText("暂无日志") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("yamibo_error_log", logText))
                    YamiboToast.show(message = "日志已复制")
                }) {
                    Text(LanguageModeUtil.displayText("复制"))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(LanguageModeUtil.displayText("关闭"))
                }
            }
        }
    )
}
@Composable
private fun ErrorLogDialogVisible(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logText = remember { AppErrorLog.snapshot() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageModeUtil.displayText(chineseText(38169, 35823, 26085, 24535))) },
        text = {
            Text(
                text = logText.ifBlank { LanguageModeUtil.displayText(chineseText(26242, 26080, 26085, 24535)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, logText))
                    YamiboToast.show(message = chineseText(26085, 24535, 24050, 22797, 21046))
                }) {
                    Text(LanguageModeUtil.displayText(chineseText(22797, 21046)))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(LanguageModeUtil.displayText(chineseText(20851, 38381)))
                }
            }
        }
    )
}
private fun chineseText(vararg codePoints: Int): String =
    codePoints.map(Int::toChar).toCharArray().concatToString()

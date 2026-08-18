package org.shirakawatyu.yamibo.novel.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.ErrorLogStore
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NativeErrorLogPage(
    onBack: () -> Unit
) {
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val context = LocalContext.current

    var days by remember { mutableStateOf<List<ErrorLogStore.DayInfo>>(emptyList()) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<ErrorLogStore.DayInfo?>(null) }
    var selectedContent by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        days = withContext(Dispatchers.IO) { ErrorLogStore.listDays() }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopBar
        Surface(color = headerColor, contentColor = headerContent) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = headerContent)
                }
                Text(
                    LanguageModeUtil.displayText("错误日志"),
                    Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = headerContent
                )
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空全部",
                        tint = headerContent
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (days.isEmpty()) {
                Text(
                    LanguageModeUtil.displayText("暂无错误记录"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                days.forEach { day ->
                    ErrorLogDayItem(
                        day = day,
                        onClick = {
                            selectedDay = day
                            scope.launch {
                                selectedContent = withContext(Dispatchers.IO) {
                                    ErrorLogStore.readDay(day.day)
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 清空全部确认
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(LanguageModeUtil.displayText("清空全部日志"), fontSize = 18.sp) },
            text = {
                Text(LanguageModeUtil.displayText("将删除全部历史错误日志，此操作不可恢复。"))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllDialog = false
                    ErrorLogStore.clearAll()
                    YamiboToast.show(message = "日志已清空")
                    scope.launch { reload() }
                }) {
                    Text(LanguageModeUtil.displayText("确认"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(LanguageModeUtil.displayText("取消"))
                }
            }
        )
    }

    // 某天日志详情
    selectedDay?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    LanguageModeUtil.displayText(dayLabel(day.day)),
                    fontSize = 18.sp
                )
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = selectedContent.ifBlank { LanguageModeUtil.displayText("暂无日志") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        copyToClipboard(context, selectedContent)
                    }) {
                        Text(LanguageModeUtil.displayText("复制"))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { selectedDay = null }) {
                        Text(LanguageModeUtil.displayText("关闭"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ErrorLogStore.deleteDay(day.day)
                    selectedDay = null
                    YamiboToast.show(message = "已删除")
                    scope.launch { reload() }
                }) {
                    Text(LanguageModeUtil.displayText("删除"), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun ErrorLogDayItem(
    day: ErrorLogStore.DayInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                LanguageModeUtil.displayText(dayLabel(day.day)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "共 ${day.lineCount} 条 · WAF 挑战 ${day.waf} · 网络错误 ${day.network} · HTTP 错误 ${day.http}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 今天/昨天显示为文字，其余显示为 MM月dd日（跨年带年份）。 */
private fun dayLabel(day: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
    return when (day) {
        today -> "今天"
        yesterday -> "昨天"
        else -> {
            val parsed = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)
            }.getOrNull() ?: return day
            if (day.startsWith(today.take(4))) {
                SimpleDateFormat("MM月dd日", Locale.getDefault()).format(parsed)
            } else {
                SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(parsed)
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) {
        YamiboToast.show(message = "暂无日志可复制")
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("yamibo_error_log", text))
    YamiboToast.show(message = "日志已复制")
}

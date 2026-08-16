package org.shirakawatyu.yamibo.novel.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.BuildConfig
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.ErrorLogStore
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

private const val GITHUB_ISSUE_URL =
    "https://github.com/KrelinnBios/YamiboPlus/issues/new"

@Composable
fun NativeFeedbackPage(
    onBack: () -> Unit,
    onOpenErrorLog: () -> Unit
) {
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val context = LocalContext.current

    // 统计口径与「错误日志」历史页一致：都从磁盘日志（ErrorLogStore）读取，
    // 避免内存环形缓冲与磁盘历史数量对不上。
    var diskTotal by remember { mutableStateOf<Int?>(null) }
    var diskLogText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val days = ErrorLogStore.listDays()
            diskTotal = days.sumOf { it.lineCount }
            diskLogText = days.joinToString("\n\n") { day ->
                "===== ${day.day} =====\n" + ErrorLogStore.readDay(day.day)
            }
        }
    }

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
                    LanguageModeUtil.displayText("反馈建议"),
                    Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = headerContent
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 错误日志（历史）
            FeedbackActionItem(
                title = "错误日志",
                subtitle = when {
                    diskTotal == null -> LanguageModeUtil.displayText("按天查看最近的错误记录")
                    diskTotal == 0 -> LanguageModeUtil.displayText("最近 7 天暂无错误记录")
                    else -> "最近 7 天共 $diskTotal 条错误记录"
                },
                onClick = onOpenErrorLog
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 前往 GitHub 提交
            FeedbackActionItem(
                title = "前往 GitHub 提交 issue",
                subtitle = "自动附带日志与设备信息",
                onClick = { openGithubIssue(context, diskLogText) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeedbackActionItem(
    title: String,
    subtitle: String,
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
                LanguageModeUtil.displayText(title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                LanguageModeUtil.displayText(subtitle),
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

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) {
        YamiboToast.show(message = "暂无日志可复制")
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("yamibo_error_log", text))
    YamiboToast.show(message = "日志已复制")
}

private fun openGithubIssue(context: Context, logText: String) {
    val deviceInfo = buildString {
        append("应用版本：v").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("系统版本：Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("设备型号：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    }
    val body = buildString {
        append("**问题描述**：\n\n")
        append("**复现步骤**：\n\n")
        append("**设备信息**：\n```\n").append(deviceInfo).append("\n```\n\n")
        if (logText.isNotBlank()) {
            append("**错误日志**：\n```\n").append(logText).append("\n```\n")
        }
    }
    // GitHub 对新建 issue 的 URL 长度有限，完整日志不能放进 query 参数。
    // 先复制完整正文，再打开仅带标题的 issue 页面，避免出现“request URL is too long”。
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("yamibo_github_issue", body))
    val url = GITHUB_ISSUE_URL + "?title=" + Uri.encode("问题反馈")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        YamiboToast.show(message = "日志已复制，打开 GitHub 后粘贴到正文")
    }.onFailure {
        YamiboToast.show(message = "无法打开浏览器，请手动访问 $GITHUB_ISSUE_URL")
    }
}

package org.shirakawatyu.yamibo.novel.ui.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboDangerColor
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.BackupUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeBackupPage(
    onBack: () -> Unit
) {
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDoneDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                BackupUtil.exportBackup(context, uri)
                    .onSuccess {
                        YamiboToast.show(message = "备份导出成功")
                    }
                    .onFailure {
                        YamiboToast.show(message = "备份导出失败: ${it.message}")
                    }
                isExporting = false
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
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
                    "备份管理",
                    Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = headerContent
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "备份与恢复",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "备份包含：收藏、设置、阅读历史、屏蔽列表",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Export Button
            Button(
                onClick = { exportBackupLauncher.launch("yamibo_backup.zip") },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("导出备份")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Import Button
            OutlinedButton(
                onClick = { importBackupLauncher.launch(arrayOf("application/zip")) },
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("导入备份")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "注意事项：",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• 导入会覆盖当前数据，无法撤销\n• 导入完成后需要重启应用\n• 请确保备份文件完整",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Import Confirmation Dialog
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("确认导入", fontSize = 18.sp) },
            text = {
                Text(
                    "导入将覆盖当前所有数据，此操作不可撤销。确定要继续吗？",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    pendingImportUri = null
                    isImporting = true
                    scope.launch {
                        BackupUtil.importBackup(context, uri!!)
                            .onSuccess {
                                showImportDoneDialog = true
                            }
                            .onFailure {
                                YamiboToast.show(message = "导入失败: ${it.message}")
                            }
                        isImporting = false
                    }
                }) {
                    Text(
                        "确认",
                        color = yamiboDangerColor(),
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("取消", fontSize = 15.sp)
                }
            }
        )
    }

    // Import Done Dialog
    if (showImportDoneDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("导入完成", fontSize = 18.sp) },
            text = {
                Text(
                    "数据导入成功，需要重启应用才能生效。",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDoneDialog = false
                    BackupUtil.restartApp(context)
                }) {
                    Text("立即重启", fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDoneDialog = false }) {
                    Text("稍后重启", fontSize = 15.sp)
                }
            }
        )
    }
}

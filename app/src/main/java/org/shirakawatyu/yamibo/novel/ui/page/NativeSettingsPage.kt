package org.shirakawatyu.yamibo.novel.ui.page


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemePreference
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboSwitchColors
import org.shirakawatyu.yamibo.novel.ui.widget.YamiboToast
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.CacheMaintenance
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.forum.ForumBlocklistManager
import org.shirakawatyu.yamibo.novel.util.reader.LocalCacheUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeSettingsPage(
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val themePalette by GlobalData.themePalette.collectAsState()
    val themeMode by GlobalData.themeMode.collectAsState()
    val languageMode by GlobalData.languageMode.collectAsState()
    val isDnsOptimizationEnabled by GlobalData.isDnsOptimizationEnabled.collectAsState()
    val isAutoClearCacheEnabled by GlobalData.isAutoClearCacheEnabled.collectAsState()
    val isCustomDnsEnabled by GlobalData.isCustomDnsEnabled.collectAsState()
    val customDnsUrl by GlobalData.customDnsUrl.collectAsState()
    val isAutoSignInEnabled = GlobalData.isAutoSignInEnabled.value
    val isForumBlocklistEnabled by ForumBlocklistManager.enabled.collectAsState()
    val componentColors = yamiboComponentColors()
    val systemDark = isSystemInDarkTheme()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var customDnsDraft by remember { mutableStateOf(customDnsUrl) }

    fun setLanguageMode(mode: String) {
        val normalized = LanguageModeUtil.normalize(mode)
        if (LanguageModeUtil.normalize(languageMode) == normalized) return
        GlobalData.languageMode.value = normalized
        SettingsUtil.saveLanguageMode(normalized)
        LanguageModeUtil.applyForumCookies(normalized, null)
        LanguageModeUtil.applyLocale(context, normalized)
    }

    fun restoreDefaultSettings() {
        val preference = YamiboThemePreference()
        val resolvedTheme = GlobalData.applyThemePreference(preference, systemDark)
        SettingsUtil.saveThemePreference(preference, resolvedTheme)
        setLanguageMode(LanguageModeUtil.SIMPLIFIED)
        GlobalData.isDnsOptimizationEnabled.value = true
        SettingsUtil.saveDnsOptimizationEnabled(true)
        GlobalData.isCustomDnsEnabled.value = false
        SettingsUtil.saveCustomDnsMode(false)
        GlobalData.dnsOptimizationMode.value = "auto"
        SettingsUtil.saveDnsOptimizationMode("auto")
        GlobalData.customDnsUrl.value = ""
        SettingsUtil.saveCustomDnsUrl("")
        customDnsDraft = ""
        GlobalData.isAutoSignInEnabled.value = true
        SettingsUtil.saveAutoSignInMode(true)
        GlobalData.isAutoVersionUpdateEnabled.value = true
        SettingsUtil.saveAutoVersionUpdateMode(true)
        GlobalData.isAutoClearCacheEnabled.value = true
        SettingsUtil.saveAutoClearCacheMode(true)
        CacheMaintenance.onAutoClearChanged(context, true)
        ForumBlocklistManager.setEnabled(true)
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    LaunchedEffect(Unit) {
        cacheSizeBytes = withContext(Dispatchers.IO) {
            val imageSize = context.imageLoader.diskCache?.size ?: 0L
            val novelSize = LocalCacheUtil.getInstance(context).index.value
                .values
                .sumOf { cache -> cache.pages.values.sumOf { it.fileSize } }
            imageSize + novelSize
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
                    LanguageModeUtil.displayText("设置"),
                    Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = headerContent
                )
                IconButton(onClick = { showResetSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "恢复原始设置",
                        tint = headerContent
                    )
                }
            }
        }

        // Settings Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Theme
            SettingActionItem(
                title = "主题",
                subtitle = "${themeMode.label} · ${themePalette.label}",
                enabled = true,
                onClick = onOpenTheme
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Language
            SettingSwitchItem(
                title = "简繁切换",
                subtitle = "切换简体/繁体中文",
                checked = LanguageModeUtil.normalize(languageMode) == LanguageModeUtil.TRADITIONAL,
                onCheckedChange = { useTraditional ->
                    setLanguageMode(
                        if (useTraditional) LanguageModeUtil.TRADITIONAL
                        else LanguageModeUtil.SIMPLIFIED
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // DNS Optimization
            SettingSwitchItem(
                title = "网络优化",
                subtitle = "优化DNS解析，提升访问速度",
                checked = isDnsOptimizationEnabled,
                onCheckedChange = { enabled ->
                    GlobalData.isDnsOptimizationEnabled.value = enabled
                    SettingsUtil.saveDnsOptimizationEnabled(enabled)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingSwitchItem(
                title = "自定义 DNS",
                subtitle = if (customDnsUrl.isBlank()) "使用系统或应用推荐的解析服务"
                else "当前地址：$customDnsUrl",
                checked = isCustomDnsEnabled,
                onCheckedChange = { enabled ->
                    GlobalData.isCustomDnsEnabled.value = enabled
                    SettingsUtil.saveCustomDnsMode(enabled)
                    GlobalData.dnsOptimizationMode.value = if (enabled) "manual" else "auto"
                    SettingsUtil.saveDnsOptimizationMode(GlobalData.dnsOptimizationMode.value)
                    if (enabled) {
                        customDnsDraft = customDnsUrl
                        showCustomDnsDialog = true
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingActionItem(
                title = "自定义 DNS 地址",
                subtitle = customDnsUrl.ifBlank { "未设置，将使用默认解析" },
                enabled = isCustomDnsEnabled,
                onClick = {
                    customDnsDraft = customDnsUrl
                    showCustomDnsDialog = true
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Forum Blocklist
            SettingSwitchItem(
                title = "帖子屏蔽",
                subtitle = "启用帖子屏蔽功能",
                checked = isForumBlocklistEnabled,
                onCheckedChange = ForumBlocklistManager::setEnabled
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Auto Sign-In
            SettingSwitchItem(
                title = "自动签到",
                subtitle = "每日自动签到获取积分",
                checked = isAutoSignInEnabled,
                onCheckedChange = { enabled ->
                    GlobalData.isAutoSignInEnabled.value = enabled
                    SettingsUtil.saveAutoSignInMode(enabled)
                    if (enabled) {
                        scope.launch(Dispatchers.IO) {
                            AutoSignManager.resetQuota()
                            AutoSignManager.checkAndSignIfNeeded(
                                context,
                                force = true,
                            )
                        }
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Auto Clear Cache
            SettingSwitchItem(
                title = "自动清理缓存",
                subtitle = "每 ${CacheMaintenance.RETENTION_DAYS} 天清理一次 · 当前缓存 ${formatFileSize(cacheSizeBytes)}",
                checked = isAutoClearCacheEnabled,
                onCheckedChange = { enabled ->
                    GlobalData.isAutoClearCacheEnabled.value = enabled
                    SettingsUtil.saveAutoClearCacheMode(enabled)
                    CacheMaintenance.onAutoClearChanged(context, enabled)
                },
                onItemClick = { showClearCacheDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingActionItem(
                title = "备份管理",
                subtitle = "导出与导入应用数据",
                enabled = true,
                onClick = onOpenBackup
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("恢复原始设置") },
            text = { Text("将恢复主题和本页所有设置，收藏、历史记录和备份不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    restoreDefaultSettings()
                    showResetSettingsDialog = false
                    YamiboToast.show(message = "已恢复原始设置")
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("取消") }
            }
        )
    }

    // Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(LanguageModeUtil.displayText("清理缓存"), fontSize = 18.sp) },
            text = {
                Text(
                    LanguageModeUtil.displayText("当前缓存 ${formatFileSize(cacheSizeBytes)}，确定要清除吗？此操作不可恢复。"),
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.diskCache?.clear()
                            LocalCacheUtil.getInstance(context).clearAllCache()
                        }
                        cacheSizeBytes = 0L
                    }
                }) {
                    Text(
                        LanguageModeUtil.displayText("确认"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(LanguageModeUtil.displayText("取消"), fontSize = 15.sp)
                }
            }
        )
    }

    if (showCustomDnsDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDnsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LanguageModeUtil.displayText("自定义 DNS 地址")) },
            text = {
                OutlinedTextField(
                    value = customDnsDraft,
                    onValueChange = { customDnsDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(LanguageModeUtil.displayText("例如 223.5.5.5")) },
                    supportingText = { Text(LanguageModeUtil.displayText("填写 IP 地址或 DoH 地址，留空则恢复默认")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = customDnsDraft.trim()
                    GlobalData.customDnsUrl.value = normalized
                    SettingsUtil.saveCustomDnsUrl(normalized)
                    if (normalized.isBlank()) {
                        GlobalData.isCustomDnsEnabled.value = false
                        SettingsUtil.saveCustomDnsMode(false)
                        GlobalData.dnsOptimizationMode.value = "auto"
                        SettingsUtil.saveDnsOptimizationMode("auto")
                    }
                    showCustomDnsDialog = false
                }) { Text(LanguageModeUtil.displayText("保存")) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDnsDialog = false }) {
                    Text(LanguageModeUtil.displayText("取消"))
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onItemClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onItemClick == null) Modifier else Modifier.clickable(onClick = onItemClick))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(LanguageModeUtil.displayText(title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                LanguageModeUtil.displayText(subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = yamiboSwitchColors()
        )
    }
}

@Composable
private fun SettingActionItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                LanguageModeUtil.displayText(title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                LanguageModeUtil.displayText(subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.45f
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.45f
            )
        )
    }
}

package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemeMode
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemePalette
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemePreference
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboSwitchColors
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil

@Composable
fun NativeThemePage(
    onBack: () -> Unit
) {
    val palette by GlobalData.themePalette.collectAsState()
    val mode by GlobalData.themeMode.collectAsState()
    val pureBlack by GlobalData.pureBlackMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val componentColors = yamiboComponentColors()

    fun updateTheme(preference: YamiboThemePreference) {
        val resolvedTheme = GlobalData.applyThemePreference(preference, systemDark)
        SettingsUtil.saveThemePreference(preference, resolvedTheme)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = componentColors.topBarContainer,
            contentColor = componentColors.topBarContent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = componentColors.topBarContent
                    )
                }
                Text(
                    text = LanguageModeUtil.displayText("主题"),
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = componentColors.topBarContent
                )
                IconButton(
                    onClick = { updateTheme(YamiboThemePreference()) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重置主题",
                        tint = componentColors.topBarContent
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            ThemeSectionTitle(
                icon = Icons.Default.AutoMode,
                title = "主题模式"
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AutoMode,
                    label = "自动",
                    selected = mode == YamiboThemeMode.SYSTEM,
                    onClick = {
                        updateTheme(YamiboThemePreference(palette, YamiboThemeMode.SYSTEM, pureBlack))
                    }
                )
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LightMode,
                    label = "浅色",
                    selected = mode == YamiboThemeMode.LIGHT,
                    onClick = {
                        updateTheme(YamiboThemePreference(palette, YamiboThemeMode.LIGHT, pureBlack))
                    }
                )
                ThemeModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DarkMode,
                    label = "深色",
                    selected = mode == YamiboThemeMode.DARK,
                    onClick = {
                        updateTheme(YamiboThemePreference(palette, YamiboThemeMode.DARK, pureBlack))
                    }
                )
            }

            Spacer(Modifier.height(30.dp))
            ThemeSectionTitle(
                icon = Icons.Default.Palette,
                title = "主题色彩"
            )
            Spacer(Modifier.height(14.dp))
            YamiboThemePalette.entries.chunked(4).forEach { rowPalettes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPalettes.forEach { candidate ->
                        ThemePaletteCard(
                            modifier = Modifier.weight(1f),
                            palette = candidate,
                            selected = candidate == palette,
                            onClick = {
                                updateTheme(YamiboThemePreference(candidate, mode, pureBlack))
                            }
                        )
                    }
                    repeat(4 - rowPalettes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Contrast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = LanguageModeUtil.displayText("纯黑模式"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = LanguageModeUtil.displayText("仅在深色模式下生效"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = pureBlack,
                        onCheckedChange = { enabled ->
                            updateTheme(YamiboThemePreference(palette, mode, enabled))
                        },
                        colors = yamiboSwitchColors()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeSectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = LanguageModeUtil.displayText(title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThemeModeCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1.42f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = LanguageModeUtil.displayText(label),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ThemePaletteCard(
    modifier: Modifier,
    palette: YamiboThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(0.86f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                ) {
                    val halfWidth = size.width / 2f
                    val halfHeight = size.height / 2f
                    drawRect(palette.swatches[0], size = androidx.compose.ui.geometry.Size(halfWidth, halfHeight))
                    drawRect(
                        palette.swatches[1],
                        topLeft = androidx.compose.ui.geometry.Offset(halfWidth, 0f),
                        size = androidx.compose.ui.geometry.Size(halfWidth, halfHeight)
                    )
                    drawRect(
                        palette.swatches[2],
                        topLeft = androidx.compose.ui.geometry.Offset(0f, halfHeight),
                        size = androidx.compose.ui.geometry.Size(halfWidth, halfHeight)
                    )
                    drawRect(
                        palette.swatches[3],
                        topLeft = androidx.compose.ui.geometry.Offset(halfWidth, halfHeight),
                        size = androidx.compose.ui.geometry.Size(halfWidth, halfHeight)
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = LanguageModeUtil.displayText(palette.label),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
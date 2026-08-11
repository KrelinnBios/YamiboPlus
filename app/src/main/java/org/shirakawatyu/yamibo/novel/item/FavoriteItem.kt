package org.shirakawatyu.yamibo.novel.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
import org.shirakawatyu.yamibo.novel.util.darkModeColor
import org.shirakawatyu.yamibo.novel.util.darkThemeColor

private val PREFIX_REGEX = Regex("^(?:[【\\[].*?[】\\]]|[\\s\\u00A0\\u3000])+")
private val TAG_REGEX = Regex("[【\\[].*?[】\\]]")

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * 收藏项组件，用于展示收藏的小说条目信息。
 *
 * @param title 小说标题
 * @param lastView 上次阅读的网页页码
 * @param lastPage 上次阅读的阅读器页码
 * @param lastChapter 上次阅读的章节名称，可能为空
 * @param onClick 点击该项时触发的回调函数
 * @param modifier 组件修饰符，默认为Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteItem(
    title: String,
    lastView: Int,
    lastPage: Int,
    lastChapter: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isManageMode: Boolean = false,
    isSelected: Boolean = false,
    isHidden: Boolean = false,
    type: Int = 0,
    cacheInfo: FavoriteVM.CacheInfo? = null,
    mangaCachedPages: Int = 0,
    mangaCacheBytes: Long = 0,
    hasUpdate: Boolean = false,
    hasUpdateCheckFailure: Boolean = false,
    isCheckingUpdate: Boolean = false,
    isPinned: Boolean = false,
    onRefreshUpdate: (() -> Unit)? = null
) {
    val tagColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

    val displayTitle = remember(title, tagColor) {
        buildAnnotatedString {
            append(title)
            val prefixMatch = PREFIX_REGEX.find(title)

            if (prefixMatch != null) {
                val tags = TAG_REGEX.findAll(prefixMatch.value)
                for (tag in tags) {
                    addStyle(
                        style = SpanStyle(
                            fontSize = 14.sp,
                            color = tagColor
                        ),
                        start = tag.range.first,
                        end = tag.range.last + 1
                    )
                }
            }
        }
    }

    // 管理模式下随选中/隐藏状态平滑切换卡片底色。
    val color by animateColorAsState(
        targetValue = when {
            isManageMode && isSelected -> MaterialTheme.colorScheme.primaryContainer
            isManageMode && isHidden -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.tertiary
        },
        label = "color_animation"
    )

    val typeColor = when (type) {
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
    }

    // 小说标记浅色保持原本棕红，暗黑模式改为青色，和顶部分类菜单样例保持一致。
    val middleColor = if (type == 1) {
        darkModeColor(
            light = YamiboColors.secondary,
            dark = Color(0xFF3FC1B0)
        )
    } else {
        lerp(typeColor.first, typeColor.second, 0.75f)
    }
    val pinColor = darkModeColor(
        light = YamiboColors.primary.copy(alpha = 0.8f),
        dark = Color(0xFF8CCBFF)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 文字层：左侧保留 3dp 主题色竖条；右侧给图钉/刷新留一个不大的固定余量。
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            color = middleColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(3.dp.toPx(), size.height)
                        )
                    }
                    .padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 管理模式下在卡片左侧展示选框（与浏览历史一致）；点击整张卡片切换选中，
                // 选框本身只做状态展示，由父级 onClick 负责切换。
                if (isManageMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Clip,
                        text = displayTitle,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(
                            lineBreak = LineBreak.Simple
                        )
                    )

                    Column(
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        if (!lastChapter.isNullOrBlank()) {
                            Text(
                                modifier = Modifier.padding(0.dp, 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                text = lastChapter,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (type == 1) {
                            Text(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                text = "上次读到第${lastPage + 1}页, 对应网页第${lastView}页"
                            )
                        }
                        if (cacheInfo != null && cacheInfo.totalPages > 0) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cache),
                                    contentDescription = "已缓存",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    text = "已缓存 ${cacheInfo.totalPages} 页 (${
                                        formatFileSize(
                                            cacheInfo.totalSize
                                        )
                                    })"
                                )
                            }
                        }
                        if (type == 2 && mangaCachedPages > 0) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cache),
                                    contentDescription = "已缓存",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    text = if (mangaCacheBytes > 0) {
                                        "已缓存 $mangaCachedPages 页 (${formatFileSize(mangaCacheBytes)})"
                                    } else {
                                        "已缓存 $mangaCachedPages 页"
                                    }
                                )
                            }
                        }
                    }

                    if (isManageMode && isHidden) {
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp,
                            text = "[已隐藏]"
                        )
                    }
                }

                // 右侧固定状态槽（余量不大）：刷新转圈 / 更新胶囊，垂直居中，不压文字。
                UpdateStatusHandle(
                    isCheckingUpdate = isCheckingUpdate,
                    hasUpdate = hasUpdate,
                    hasUpdateCheckFailure = hasUpdateCheckFailure,
                    onRefreshUpdate = onRefreshUpdate
                )
            }

            // 置顶图钉：固定在卡片右上角（位于状态槽上方，不压标题）。
            androidx.compose.animation.AnimatedVisibility(
                visible = isPinned,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 8.dp),
                enter = fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(120))
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = pinColor,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            rotationZ = 18f
                        }
                )
            }
        }
    }
}

@Composable
private fun UpdateStatusHandle(
    isCheckingUpdate: Boolean,
    hasUpdate: Boolean,
    hasUpdateCheckFailure: Boolean,
    onRefreshUpdate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primary = darkThemeColor(YamiboColors.primary) { primary }
    val updateAccent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isCheckingUpdate,
            enter = fadeIn(animationSpec = tween(140, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = primary
            )
        }

        if ((hasUpdate || hasUpdateCheckFailure) && !isCheckingUpdate && onRefreshUpdate != null) {
            IconButton(
                onClick = onRefreshUpdate,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (hasUpdateCheckFailure) "重新检查更新" else "刷新检查更新",
                    tint = updateAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

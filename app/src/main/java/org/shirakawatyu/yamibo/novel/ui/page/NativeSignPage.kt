package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.bean.SignCalendarDay
import org.shirakawatyu.yamibo.novel.bean.SignRecord
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.vm.SignPageVM

@Composable
fun NativeSignPage(
    navController: NavController,
    bottomBarPadding: Dp = 0.dp,
    viewModel: SignPageVM = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = yamiboComponentColors()
    val context = androidx.compose.ui.platform.LocalContext.current
    val returnToMine = {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(NATIVE_MINE_SIGN_RESULT_KEY, true)
        navController.popBackStack()
        Unit
    }

    BackHandler(onBack = returnToMine)

    LaunchedEffect(Unit) {
        viewModel.load(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = bottomBarPadding)
    ) {
        Surface(color = colors.topBarContainer, contentColor = colors.topBarContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = returnToMine) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.topBarContent
                    )
                }
                Text(
                    text = "签到",
                    color = colors.topBarContent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.load(context) }, enabled = !state.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = colors.topBarContent)
                }
            }
        }

        when {
            state.data == null && state.error == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.data == null -> YamiboLoadError(
                title = state.error ?: "签到页面无法打开",
                onRetry = { viewModel.load(context) },
                modifier = Modifier.fillMaxSize()
            )

            else -> {
                val data = state.data ?: return@Column
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Button(
                            onClick = { viewModel.sign(context) },
                            enabled = !data.signedToday && !state.signing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.signing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(
                                when {
                                    state.signing -> "正在签到"
                                    data.signedToday -> "今日已打卡"
                                    else -> "点击打卡"
                                },
                                fontSize = 16.sp
                            )
                        }
                    }
                    if (data.announcement.isNotBlank()) {
                        item {
                            SignSection(title = "打卡公告") {
                                Text(
                                    text = data.announcement,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                    item {
                        SignCalendar(
                            year = data.year,
                            month = data.month,
                            days = data.calendar,
                            onPrevious = {
                                val year = if (data.month == 1) data.year - 1 else data.year
                                val month = if (data.month == 1) 12 else data.month - 1
                                viewModel.load(context, year, month)
                            },
                            onNext = {
                                val year = if (data.month == 12) data.year + 1 else data.year
                                val month = if (data.month == 12) 1 else data.month + 1
                                viewModel.load(context, year, month)
                            }
                        )
                    }
                    if (data.myStats.isNotEmpty()) {
                        item {
                            SignSection(title = "我的打卡动态") {
                                data.myStats.forEach { line ->
                                    Text(
                                        text = line,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = "最近打卡",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(data.records, key = { "${it.uid}-${it.lastSignTime}" }) { record ->
                        SignRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun SignCalendar(
    year: Int,
    month: Int,
    days: List<SignCalendarDay?>,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    SignSection(title = "打卡月历") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious) { Text("上个月") }
            Text(
                text = "${year}年${month}月",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onNext) { Text("下个月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                Text(
                    text = weekday,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) SignCalendarCell(day)
                    }
                }
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun SignCalendarCell(day: SignCalendarDay) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = when {
            day.signed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (day.today) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.day.toString(),
                color = if (day.signed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 13.sp,
                fontWeight = if (day.today) FontWeight.Bold else FontWeight.Normal
            )
            if (day.holiday.isNotBlank()) {
                Text(
                    text = day.holiday,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SignRecordCard(record: SignRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.username,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = record.lastSignTime,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Text(
                text = listOfNotNull(
                    record.level.takeIf(String::isNotBlank),
                    record.totalDays.takeIf(String::isNotBlank)?.let { "累计 ${it} 天" },
                    record.lastReward.takeIf(String::isNotBlank)?.let { "奖励 ${it}" }
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

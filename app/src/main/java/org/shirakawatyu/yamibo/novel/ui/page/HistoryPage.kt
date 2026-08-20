package org.shirakawatyu.yamibo.novel.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import org.shirakawatyu.yamibo.novel.ui.component.YamiboAlertDialog as AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.bean.HistoryEntry
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.openNativeForumPost
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboDangerColor
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingOverlay
import org.shirakawatyu.yamibo.novel.ui.widget.OnboardingStep
import org.shirakawatyu.yamibo.novel.util.OnboardingUtil
import org.shirakawatyu.yamibo.novel.util.history.HistoryUtil
import java.net.URLEncoder
import java.util.Calendar

// 自定义日历图标，用于日期筛选器
private val CalendarIcon: ImageVector = ImageVector.Builder(
        name = "Calendar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 4f)
            lineTo(18f, 4f)
            lineTo(18f, 2f)
            lineTo(16f, 2f)
            lineTo(16f, 4f)
            lineTo(8f, 4f)
            lineTo(8f, 2f)
            lineTo(6f, 2f)
            lineTo(6f, 4f)
            lineTo(5f, 4f)
            curveTo(3.89f, 4f, 3.01f, 4.9f, 3.01f, 6f)
            lineTo(3f, 20f)
            curveTo(3f, 21.1f, 3.89f, 22f, 5f, 22f)
            lineTo(19f, 22f)
            curveTo(20.1f, 22f, 21f, 21.1f, 21f, 20f)
            lineTo(21f, 6f)
            curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f)
            close()
            moveTo(19f, 20f)
            lineTo(5f, 20f)
            lineTo(5f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 20f)
            close()
        }
    }.build()

private data class TimelineGroup(val label: String, val entries: List<HistoryEntry>)

private fun groupByTimeline(entries: List<HistoryEntry>): List<TimelineGroup> {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = cal.timeInMillis
    val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
    val monthAgo = now - 30 * 24 * 60 * 60 * 1000L

    val today = mutableListOf<HistoryEntry>()
    val yesterday = mutableListOf<HistoryEntry>()
    val week = mutableListOf<HistoryEntry>()
    val month = mutableListOf<HistoryEntry>()
    val older = mutableListOf<HistoryEntry>()

    for (entry in entries) {
        when {
            entry.timestamp >= todayStart -> today.add(entry)
            entry.timestamp >= yesterdayStart -> yesterday.add(entry)
            entry.timestamp >= weekAgo -> week.add(entry)
            entry.timestamp >= monthAgo -> month.add(entry)
            else -> older.add(entry)
        }
    }

    return listOfNotNull(
        if (today.isNotEmpty()) TimelineGroup("今天", today) else null,
        if (yesterday.isNotEmpty()) TimelineGroup("昨天", yesterday) else null,
        if (week.isNotEmpty()) TimelineGroup("近一周", week) else null,
        if (month.isNotEmpty()) TimelineGroup("近一月", month) else null,
        if (older.isNotEmpty()) TimelineGroup("更久前", older) else null
    )
}

private fun formatEntryTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000L}分钟前"
        diff < 86400_000L -> "${diff / 3600_000L}小时前"
        else -> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year == currentYear) "${month}月${day}日"
            else "${year}年${month}月${day}日"
        }
    }
}

// 帮助格式化纯日期的辅助函数
private fun formatDateOnly(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.YEAR)}.${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.DAY_OF_MONTH)}"
}

internal data class HistoryMonth(val year: Int, val month0: Int)

internal fun historyMonth(timestamp: Long): HistoryMonth =
    Calendar.getInstance().apply { timeInMillis = timestamp }.let { calendar ->
        HistoryMonth(
            year = calendar.get(Calendar.YEAR),
            month0 = calendar.get(Calendar.MONTH)
        )
    }

internal fun historyMonths(entries: List<HistoryEntry>): List<HistoryMonth> =
    entries.asSequence()
        .map { historyMonth(it.timestamp) }
        .distinct()
        .sortedWith(compareBy(HistoryMonth::year, HistoryMonth::month0))
        .toList()

private fun historyDateKey(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "%04d-%02d-%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

private fun dateAtStartOfDay(year: Int, month0: Int, day: Int): Long =
    Calendar.getInstance().apply {
        clear()
        set(year, month0, day)
    }.timeInMillis

@Composable
private fun HistoryHeatmapPickerDialog(
    historyEntries: List<HistoryEntry>,
    selectedStart: Long?,
    selectedEnd: Long?,
    onConfirm: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val dailyCounts = remember(historyEntries) {
        historyEntries
            .groupingBy { historyDateKey(it.timestamp) }
            .eachCount()
    }
    val availableMonths = remember(historyEntries) {
        historyMonths(historyEntries)
    }
    val initialMonthIndex = remember(availableMonths, selectedStart) {
        val selectedMonthIndex = selectedStart
            ?.let(::historyMonth)
            ?.let { availableMonths.indexOf(it) }
            ?: -1
        selectedMonthIndex.takeIf { it >= 0 }
            ?: availableMonths.lastIndex.coerceAtLeast(0)
    }
    var displayMonthIndex by remember(availableMonths, selectedStart) {
        mutableStateOf(initialMonthIndex)
    }
    val fallbackMonth = remember { historyMonth(System.currentTimeMillis()) }
    val displayMonth = availableMonths.getOrNull(displayMonthIndex) ?: fallbackMonth
    val displayYear = displayMonth.year
    val displayMonth0 = displayMonth.month0
    var draftStart by remember(selectedStart) { mutableStateOf(selectedStart) }
    var draftEnd by remember(selectedEnd) { mutableStateOf(selectedEnd) }

    val monthCalendar = remember(displayYear, displayMonth0) {
        Calendar.getInstance().apply {
            clear()
            set(displayYear, displayMonth0, 1)
        }
    }
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingEmptyCells = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val dayCells = remember(displayYear, displayMonth0, daysInMonth, leadingEmptyCells) {
        buildList<Int?> {
            repeat(leadingEmptyCells) { add(null) }
            for (day in 1..daysInMonth) add(day)
            while (size % 7 != 0) add(null)
        }
    }
    val currentMonthPrefix = "%04d-%02d".format(displayYear, displayMonth0 + 1)
    val maxCount = dailyCounts
        .filterKeys { it.startsWith(currentMonthPrefix) }
        .values
        .maxOrNull()
        ?: 0
    val canMovePrevious = displayMonthIndex > 0
    val canMoveNext = displayMonthIndex < availableMonths.lastIndex
    val primary = MaterialTheme.colorScheme.primary
    val dayShape = RoundedCornerShape(8.dp)

    // 点选交互：先点一天为单日；再点同一天取消；单日状态下点另一天自动成范围；
    // 已是范围时再点任意日期则重新从该天开始选。
    fun selectDay(dayMillis: Long) {
        val start = draftStart
        val end = draftEnd ?: start
        when {
            start == null -> {
                draftStart = dayMillis
                draftEnd = dayMillis
            }
            start == end -> when {
                dayMillis == start -> {
                    draftStart = null
                    draftEnd = null
                }
                dayMillis > start -> draftEnd = dayMillis
                else -> {
                    draftStart = dayMillis
                    draftEnd = start
                }
            }
            else -> {
                draftStart = dayMillis
                draftEnd = dayMillis
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { displayMonthIndex-- },
                    enabled = canMovePrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一个有记录的月份"
                    )
                }
                Text(
                    text = displayYear.toString() + "年" + (displayMonth0 + 1) + "月",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { displayMonthIndex++ },
                    enabled = canMoveNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一个有记录的月份"
                    )
                }
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                        Text(
                            text = weekday,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                dayCells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(modifier = Modifier.weight(1f).height(38.dp))
                            } else {
                                val dayMillis = dateAtStartOfDay(displayYear, displayMonth0, day)
                                val count = dailyCounts[historyDateKey(dayMillis)] ?: 0
                                val selected = draftStart != null &&
                                        dayMillis in draftStart!!..(draftEnd ?: draftStart!!)
                                val intensity = if (count == 0) 0f
                                else (0.28f + 0.72f * count / maxCount.coerceAtLeast(1).toFloat())
                                    .coerceIn(0f, 1f)
                                val containerColor = when {
                                    count > 0 -> primary.copy(alpha = intensity)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                                // 选中标识用「描边 + 与填充留缝」而不是换填充色：
                                // 热力最深的格子本身就是纯 primary，换色区分不出选中。
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .padding(2.dp)
                                        .clip(dayShape)
                                        .then(
                                            if (selected) Modifier.border(2.dp, primary, dayShape)
                                            else Modifier
                                        )
                                        .clickable(enabled = count > 0) { selectDay(dayMillis) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (selected) 4.dp else 0.dp)
                                            .clip(dayShape)
                                            .background(containerColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else null,
                                            color = if (intensity > 0.55f) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                val start = draftStart
                val end = draftEnd ?: start
                val selectionText = when {
                    start == null -> "未选择日期"
                    end == start -> "已选择 " + formatDateOnly(start)
                    else -> "已选择 " + formatDateOnly(start) + " - " + formatDateOnly(end!!)
                }
                Text(
                    text = selectionText,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = if (start != null) FontWeight.Medium else null,
                    color = if (start != null) primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            // 允许在未选任何日期时确定：取消全部选择后点确定即清除日期筛选。
            TextButton(
                onClick = {
                    onConfirm(draftStart, if (draftStart != null) draftEnd ?: draftStart else null)
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryPage(navController: NavController) {
    var isExiting by remember { mutableStateOf(false) }

    // 系统返回键拦截
    BackHandler(enabled = !isExiting) {
        isExiting = true
        navController.popBackStack()
    }


    val historyList by HistoryUtil.getHistoryFlow().collectAsState(initial = emptyList())

    // 搜索与日期筛选状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedEndDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isManageMode by remember { mutableStateOf(false) }
    var selectedUrls by remember { mutableStateOf(setOf<String>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val componentColors = yamiboComponentColors()
    val pageBackground = componentColors.contentBackground
    val topBarColor = componentColors.topBarContainer
    val topBarContentColor = componentColors.topBarContent
    // 使用主题容器色，避免未定义 tertiary 时回退成默认紫红色。
    val searchBoxContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // 将搜索词按空格分词，实现组合搜索
    val searchTerms = remember(searchQuery) {
        searchQuery.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // 核心筛选逻辑：组合过滤搜索词 + 日期/范围
    val filteredEntries =
        remember(historyList, searchTerms, selectedStartDateMillis, selectedEndDateMillis) {
            historyList.filter { entry ->
                // 1. 日期匹配 (精确计算到当天的 0点到24点)
                val matchesDate = if (selectedStartDateMillis != null) {
                    val startOfDay = Calendar.getInstance().apply {
                        timeInMillis = selectedStartDateMillis!!
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val endOfDay = Calendar.getInstance().apply {
                        timeInMillis = selectedEndDateMillis ?: selectedStartDateMillis!!
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis

                    entry.timestamp in startOfDay..endOfDay
                } else {
                    true
                }

                // 2. 关键词组合匹配
                val matchesSearch = if (searchTerms.isEmpty()) {
                    true
                } else {
                    searchTerms.all { term ->
                        entry.title.contains(term, ignoreCase = true) ||
                                entry.author.contains(term, ignoreCase = true) ||
                                entry.section.contains(term, ignoreCase = true)
                    }
                }

                matchesDate && matchesSearch
            }
        }

    val grouped = remember(filteredEntries) {
        groupByTimeline(filteredEntries)
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lazyListState = rememberLazyListState()

    OnboardingOverlay(
        page = OnboardingUtil.Page.HISTORY,
        enabled = GlobalData.currentUid.isNotBlank(),
        steps = listOf(
            OnboardingStep(
                title = "浏览历史小提示",
                description = "搜索框支持组合查询：多个关键词用空格隔开，标题、作者或版块里包含全部关键词的记录才会显示。"
            ),
            OnboardingStep(
                title = "浏览历史小提示",
                description = "点击搜索框右侧的日历图标可以按日期筛选：左右箭头只切换到有浏览记录的月份，颜色越深表示当天记录越多，只能点选有记录的日期。点一个日期筛选当天，再点另一个日期即为范围，重复点击已选日期可以取消。筛选生效后上方会出现日期标签，点击标签可以清除。"
            ),
            OnboardingStep(
                title = "浏览历史小提示",
                description = "点击右上角「管理」图标进入多选模式，可以全选并批量删除；右上角垃圾桶图标可以一键清空全部历史（有二次确认）。"
            )
        )
    )

    // 组合式日期选择器 Dialog
    if (showDatePicker) {
        HistoryHeatmapPickerDialog(
            historyEntries = historyList,
            selectedStart = selectedStartDateMillis,
            selectedEnd = selectedEndDateMillis,
            onConfirm = { start, end ->
                selectedStartDateMillis = start
                selectedEndDateMillis = end
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("清空浏览历史", fontSize = 18.sp) },
            text = {
                Text(
                    "确定要清空所有浏览历史吗？此操作不可撤销。",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { HistoryUtil.clearHistory() }
                    showClearDialog = false
                    isManageMode = false
                }) {
                    Text(
                        "确认",
                        color = yamiboDangerColor(),
                        fontSize = 15.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", fontSize = 15.sp)
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "浏览历史",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = topBarContentColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isExiting) {
                            isExiting = true
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = topBarContentColor
                        )
                    }
                },
                actions = {
                    if (isManageMode) {
                        TextButton(onClick = {
                            isManageMode = false
                            selectedUrls = emptySet()
                        }) {
                            Text(
                                "完成",
                                color = topBarContentColor,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        if (historyList.isNotEmpty()) {
                            IconButton(onClick = { isManageMode = true }) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = "管理",
                                    tint = topBarContentColor,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "清空",
                                    tint = topBarContentColor,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    titleContentColor = topBarContentColor,
                    navigationIconContentColor = topBarContentColor,
                    actionIconContentColor = topBarContentColor
                )
            )
        },
        bottomBar = {
            if (isManageMode) {
                val allSelected =
                    filteredEntries.isNotEmpty() && selectedUrls.size == filteredEntries.size
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = pageBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .padding(bottom = navBarPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            selectedUrls = if (allSelected) emptySet()
                            else filteredEntries.map { it.url }.toSet()
                        }) {
                            Text(
                                if (allSelected) "取消全选" else "全选",
                                fontSize = 15.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已选 ${selectedUrls.size} 项",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        HistoryUtil.batchDelete(selectedUrls.toList())
                                        selectedUrls = emptySet()
                                        isManageMode = false
                                    }
                                },
                                enabled = selectedUrls.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selectedUrls.isNotEmpty()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "删除",
                                    color = if (selectedUrls.isNotEmpty()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = pageBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                placeholder = { Text("支持组合查询", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { showDatePicker = true },
                            enabled = historyList.isNotEmpty()
                        ) {
                            Icon(
                                CalendarIcon,
                                contentDescription = "按日期筛选",
                                tint = if (selectedStartDateMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    // 搜索框使用当前主题的容器色，和历史卡片保持同一视觉层级。
                    focusedContainerColor = searchBoxContainerColor,
                    unfocusedContainerColor = searchBoxContainerColor,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // 日期过滤标签
            androidx.compose.animation.AnimatedVisibility(visible = selectedStartDateMillis != null) {
                selectedStartDateMillis?.let { startMillis ->
                    val endMillis = selectedEndDateMillis ?: startMillis
                    val dateStr = if (startMillis == endMillis) {
                        formatDateOnly(startMillis)
                    } else {
                        "${formatDateOnly(startMillis)} - ${formatDateOnly(endMillis)}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                selectedStartDateMillis = null
                                selectedEndDateMillis = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "已筛选日期: $dateStr",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank() || selectedStartDateMillis != null) "没有匹配的记录" else "暂无浏览记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = navBarPadding + 16.dp)
                ) {
                    grouped.forEach { group ->
                        stickyHeader(key = group.label) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = pageBackground
                            ) {
                                Text(
                                    text = group.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 10.dp
                                    )
                                )
                            }
                        }

                        items(
                            items = group.entries,
                            key = { it.url }
                        ) { entry ->
                            val isSelected = entry.url in selectedUrls

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 0.dp,
                                shadowElevation = 1.dp
                            ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isManageMode) {
                                            selectedUrls = if (isSelected) {
                                                selectedUrls - entry.url
                                            } else {
                                                selectedUrls + entry.url
                                            }
                                        } else {
                                            scope.launch {
                                                if (!openNativeForumPost(navController, entry.url)) {
                                                    val encodedUrl =
                                                        URLEncoder.encode(entry.url, "utf-8")
                                                    navController.navigate(
                                                        "MineHistoryPostPage?url=$encodedUrl"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isManageMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedUrls = if (isSelected) {
                                                selectedUrls - entry.url
                                            } else {
                                                selectedUrls + entry.url
                                            }
                                        },
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val metaText = buildString {
                                            if (entry.author.isNotBlank()) append(entry.author)
                                            if (entry.author.isNotBlank() && entry.section.isNotBlank()) append(
                                                " · "
                                            )
                                            if (entry.section.isNotBlank()) append(entry.section)
                                        }
                                        if (metaText.isNotBlank()) {
                                            Text(
                                                text = metaText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatEntryTime(entry.timestamp),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            )
                                        )
                                    }
                                }

                                if (!isManageMode) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                HistoryUtil.deleteEntry(entry.url)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

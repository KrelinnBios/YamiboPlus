package org.shirakawatyu.yamibo.novel.ui.page

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.bean.forum.UserProfile
import org.shirakawatyu.yamibo.novel.ui.component.YamiboLoadError
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors
import org.shirakawatyu.yamibo.novel.ui.state.MinePageState
import org.shirakawatyu.yamibo.novel.ui.vm.NativeMinePageVM
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.TodaySignStatus
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

@Composable
fun NativeMinePage(
    navController: NavController,
    onOpenLogin: () -> Unit,
    bottomNavBarVM: BottomNavBarVM,
    viewModel: NativeMinePageVM = viewModel(
        factory = NativeMinePageVM.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncedTodaySignStatus by AutoSignManager.todaySignStatus.collectAsState()
    var todaySignStatus by remember { mutableStateOf<TodaySignStatus?>(null) }
    var isSigning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val lastSignStatusRefreshUid = rememberSaveable { mutableStateOf<String?>(null) }
    val lastSignStatusRefreshDate = rememberSaveable { mutableStateOf("") }

    LaunchedEffect(syncedTodaySignStatus) {
        syncedTodaySignStatus?.let { todaySignStatus = it }
    }

    LaunchedEffect(state.profile?.uid) {
        val uid = state.profile?.uid
        if (uid.isNullOrBlank()) {
            todaySignStatus = null
            lastSignStatusRefreshUid.value = null
            return@LaunchedEffect
        }
        val today = AutoSignManager.getServerTodayPublic()
        // 同一天、同一个 UID，只读缓存，不再请求插件页
        if (lastSignStatusRefreshUid.value == uid && lastSignStatusRefreshDate.value == today) {
            return@LaunchedEffect
        }
        lastSignStatusRefreshUid.value = uid
        lastSignStatusRefreshDate.value = today
        todaySignStatus = AutoSignManager.getTodaySignStatus()
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.scrollToTopEvent.collect { index ->
            if (index == 3) scrollState.animateScrollTo(0)
        }
    }
    LaunchedEffect(bottomNavBarVM) {
        bottomNavBarVM.goHomeEvent.collect { route ->
            if (route == "MinePage") scrollState.animateScrollTo(0)
        }
    }
    val isBlockingContent =
        (state.isLoading && state.profile == null) ||
                (state.error != null && state.profile == null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopBar
        Surface(color = headerColor, contentColor = headerContent) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(15.dp, 10.dp)
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = headerContent,
                    fontSize = 18.sp,
                    text = LanguageModeUtil.displayText("我的"),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isBlockingContent) Modifier else Modifier.verticalScroll(scrollState))
                .padding(bottom = 80.dp)
        ) {
            when {
                state.isLoading && state.profile == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.profile == null -> {
                    YamiboLoadError(
                        title = "个人中心无法打开",
                        onRetry = viewModel::refreshProfile
                    )
                }
                else -> {
                    // Profile header remains compact so the useful actions stay above the fold.
                    state.profile?.let { profile ->
                        UserProfileCard(
                            profile = profile,
                            state = state,
                            signStatus = todaySignStatus,
                            isSigning = isSigning,
                            onOpenProfile = {
                                val profileUrl =
                                    "https://bbs.yamibo.com/home.php?mod=space" +
                                        "&uid=${profile.uid}&do=profile&mobile=2"
                                navController.navigate("OtherWebPage/${Uri.encode(profileUrl)}")
                            },
                            onSignIn = {
                                if (!isSigning) {
                                    isSigning = true
                                    scope.launch {
                                        AutoSignManager.checkAndSignIfNeeded(context, force = true)
                                         todaySignStatus = AutoSignManager.getTodaySignStatus(forceRefresh = true)
                                        // 手动打卡后让下次进入页面再主动刷新一次缓存
                                        lastSignStatusRefreshDate.value = ""
                                        isSigning = false
                                    }
                                }
                            },
                            onOpenSpaceSection = { section ->
                                navController.navigate(
                                    "OtherWebPage/${Uri.encode(ForumActionUrls.userSpace(profile.uid, section))}"
                                )
                            }
                        )
                    } ?: NotLoggedInCard()

                    if (state.profile == null) {
                        Button(
                            onClick = onOpenLogin,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(LanguageModeUtil.displayText("登录百合会论坛"))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        LanguageModeUtil.displayText("常用功能"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    MenuItem(
                        icon = Icons.Default.History,
                        title = "历史记录",
                        subtitle = "查看阅读记录与最近浏览内容",
                        onClick = { navController.navigate("HistoryPage") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Block,
                        title = "屏蔽管理",
                        subtitle = "管理屏蔽的帖子与用户",
                        onClick = { navController.navigate("NativeBlocklistPage") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Settings,
                        title = "应用设置",
                        subtitle = "主题、语言、网络与阅读偏好",
                        onClick = { navController.navigate("NativeSettingsPage") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "检查更新",
                        subtitle = "检查新版本",
                        onClick = { navController.navigate("NativeUpdatePage") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Info,
                        title = "反馈建议",
                        subtitle = "前往 GitHub 提交 issue",
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KrelinnBios/YamiboPlus/issues/new"))
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    if (state.profile != null) {
                        Button(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(LanguageModeUtil.displayText("退出登录"))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun UserProfileCard(
    profile: UserProfile,
    state: MinePageState,
    signStatus: TodaySignStatus?,
    isSigning: Boolean,
    onOpenProfile: () -> Unit,
    onSignIn: () -> Unit,
    onOpenSpaceSection: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profile.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "打开个人资料",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .clickable(onClick = onOpenProfile)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Username
            Text(
                text = LanguageModeUtil.displayText(profile.username.ifEmpty { "未登录" }),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Group
            if (!profile.groupTitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = LanguageModeUtil.displayText(profile.groupTitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSignIn,
                enabled = !isSigning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (isSigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    when (signStatus) {
                        TodaySignStatus.SIGNED -> LanguageModeUtil.displayText("今日已签到")
                        TodaySignStatus.NOT_SIGNED -> LanguageModeUtil.displayText("今日尚未签到")
                        TodaySignStatus.UNKNOWN, null -> LanguageModeUtil.displayText("\u4eca\u65e5\u672a\u7b7e\u5230")
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(LanguageModeUtil.displayText("\u79ef\u5206"), profile.credits.toString())
                StatItem(
                    label = LanguageModeUtil.displayText("\u4e3b\u9898"),
                    value = profile.threads.toString(),
                    onClick = { onOpenSpaceSection("thread") }
                )
                StatItem(
                    label = LanguageModeUtil.displayText("\u56de\u590d"),
                    value = profile.posts.toString(),
                    onClick = { onOpenSpaceSection("reply") }
                )
                StatItem(
                    label = LanguageModeUtil.displayText("\u65e5\u5fd7"),
                    value = "\u67e5\u770b",
                    onClick = { onOpenSpaceSection("blog") }
                )
                StatItem(
                    label = LanguageModeUtil.displayText("\u8bb0\u5f55"),
                    value = "\u67e5\u770b",
                    onClick = { onOpenSpaceSection("doing") }
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotLoggedInCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = LanguageModeUtil.displayText("未登录"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = LanguageModeUtil.displayText("请先登录账号"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = LanguageModeUtil.displayText(title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = LanguageModeUtil.displayText(subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

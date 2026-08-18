package org.shirakawatyu.yamibo.novel.ui.page

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
import org.shirakawatyu.yamibo.novel.ui.widget.ObserveBottomBarScrollState
import org.shirakawatyu.yamibo.novel.util.AutoSignManager
import org.shirakawatyu.yamibo.novel.util.TodaySignStatus
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

internal const val NATIVE_MINE_LOGIN_RESULT_KEY = "native_mine_login_succeeded"
internal const val NATIVE_MINE_SIGN_RESULT_KEY = "native_mine_sign_returned"
internal const val NATIVE_MINE_MESSAGE_RESULT_KEY = "native_mine_message_returned"

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginSucceeded by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(NATIVE_MINE_LOGIN_RESULT_KEY, false)
        ?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val signPageReturned by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(NATIVE_MINE_SIGN_RESULT_KEY, false)
        ?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val messagePageReturned by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(NATIVE_MINE_MESSAGE_RESULT_KEY, false)
        ?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val componentColors = yamiboComponentColors()
    val headerColor = componentColors.topBarContainer
    val headerContent = componentColors.topBarContent
    val syncedTodaySignStatus by AutoSignManager.todaySignStatus.collectAsState()
    var todaySignStatus by rememberSaveable { mutableStateOf<TodaySignStatus?>(null) }
    val scrollState = rememberScrollState()
    ObserveBottomBarScrollState(scrollState, bottomNavBarVM)
    val lastSignStatusRefreshUid = rememberSaveable { mutableStateOf<String?>(null) }
    val lastSignStatusRefreshDate = rememberSaveable { mutableStateOf("") }

    LaunchedEffect(syncedTodaySignStatus) {
        syncedTodaySignStatus?.let { todaySignStatus = it }
    }
    LaunchedEffect(loginSucceeded) {
        if (loginSucceeded) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(NATIVE_MINE_LOGIN_RESULT_KEY, false)
            viewModel.refreshProfile()
        }
    }
    LaunchedEffect(signPageReturned) {
        if (signPageReturned) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(NATIVE_MINE_SIGN_RESULT_KEY, false)
            todaySignStatus = AutoSignManager.getTodaySignStatus(forceRefresh = true)
        }
    }
    LaunchedEffect(messagePageReturned) {
        if (messagePageReturned) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(NATIVE_MINE_MESSAGE_RESULT_KEY, false)
            viewModel.refreshProfile()
        }
    }

    LaunchedEffect(state.profile?.uid) {
        val uid = state.profile?.uid ?: return@LaunchedEffect
        val today = AutoSignManager.getServerTodayPublic()
        // 同一天、同一个 UID 只读本地缓存，不再请求插件页，避免每次打开/切主题闪变。
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
                            signStatus = todaySignStatus,
                            onOpenProfile = {
                                val profileUrl =
                                    "https://bbs.yamibo.com/home.php?mod=space" +
                                        "&uid=${profile.uid}&do=profile&mobile=2"
                                navController.navigate("OtherWebPage/${Uri.encode(profileUrl)}")
                            },
                            onManualSign = {
                                scope.launch {
                                    AutoSignManager.checkAndSignIfNeeded(context, force = true)
                                }
                            },
                            onOpenSpaceSection = { section ->
                                when (section) {
                                    "friend" -> navController.navigate("NativeFriendPage")
                                    "doing" -> navController.navigate("NativeDoingPage")
                                    "blog" -> navController.navigate("NativeBlogPage")
                                    "thread" -> navController.navigate(
                                        "NativeUserThreadsPage?tab=thread"
                                    )
                                    "reply" -> navController.navigate(
                                        "NativeUserThreadsPage?tab=reply"
                                    )
                                    else -> navController.navigate(
                                        "OtherWebPage/${Uri.encode(ForumActionUrls.userSpace(profile.uid, section))}"
                                    )
                                }
                            },
                            onOpenCredits = {
                                navController.navigate("OtherWebPage/${Uri.encode(ForumActionUrls.creditLog)}")
                            },
                            onOpenMessages = {
                                navController.navigate("NativeMessageCenterPage")
                            },
                            onOpenUserGroup = {
                                val userGroupUrl = "https://bbs.yamibo.com/home.php?mod=spacecp&ac=usergroup&mobile=no"
                                navController.navigate("OtherWebPage/${Uri.encode(userGroupUrl)}")
                            }
                        )
                    } ?: if (state.isLoggedIn) {
                        LoggedInProfilePendingCard(onRetry = viewModel::refreshProfile)
                    } else {
                        NotLoggedInCard(onOpenLogin)
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
                        subtitle = "错误日志与提交 GitHub issue",
                        onClick = { navController.navigate("NativeFeedbackPage") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    if (state.isLoggedIn) {
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
    signStatus: TodaySignStatus?,
    onOpenProfile: () -> Unit,
    onManualSign: () -> Unit,
    onOpenSpaceSection: (String) -> Unit,
    onOpenCredits: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenUserGroup: () -> Unit
) {
    val totalCredits = profile.totalCredits.takeIf { it > 0 } ?: profile.credits
    val avatarShape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(120.dp).clickable(onClick = onManualSign),
                    shape = avatarShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (signStatus == TodaySignStatus.SIGNED) 3.dp else 2.dp,
                        if (signStatus == TodaySignStatus.SIGNED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(profile.avatarUrl).crossfade(true).build(),
                        contentDescription = "手动签到",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(avatarShape)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileActionButton(
                            text = "消息",
                            onClick = onOpenMessages,
                            modifier = Modifier.weight(1f),
                            showNewBadge = profile.hasNewMessage
                        )
                        ProfileActionButton("好友", { onOpenSpaceSection("friend") }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileActionButton("主题", { onOpenSpaceSection("thread") }, Modifier.weight(1f))
                        ProfileActionButton("回复", { onOpenSpaceSection("reply") }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileActionButton("日志", { onOpenSpaceSection("blog") }, Modifier.weight(1f))
                        ProfileActionButton("记录", { onOpenSpaceSection("doing") }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .alignBy(LastBaseline),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfileTextLink(
                        text = profile.username.ifEmpty { "未登录" },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        bold = true,
                        onClick = onOpenProfile
                    )
                    Spacer(Modifier.height(4.dp))
                    ProfileTextLink(
                        text = profile.groupTitle?.ifEmpty { "未设置" } ?: "未设置",
                        color = MaterialTheme.colorScheme.secondary,
                        bold = true,
                        onClick = onOpenUserGroup
                    )
                }
                Spacer(Modifier.width(14.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .alignBy(LastBaseline)
                ) {
                    ProfileStatText(
                        totalCredits.toString(),
                        "总积分",
                        onOpenCredits,
                        Modifier.weight(1f).alignBy(LastBaseline)
                    )
                    ProfileStatText(
                        profile.credits.toString(),
                        "积分",
                        onOpenCredits,
                        Modifier.weight(1f).alignBy(LastBaseline)
                    )
                    ProfileStatText(
                        profile.partner.toString(),
                        "对象",
                        onOpenCredits,
                        Modifier.weight(1f).alignBy(LastBaseline)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTextLink(
    text: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit = 14.sp,
    bold: Boolean = false
) {
    Text(
        text = LanguageModeUtil.displayText(text),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = color,
        fontSize = fontSize,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun ProfileStatText(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = LanguageModeUtil.displayText(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showNewBadge: Boolean = false
) {
    Box(modifier = modifier.height(34.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = LanguageModeUtil.displayText(text),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
        if (showNewBadge) {
            Text(
                text = "NEW",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NotLoggedInCard(onOpenLogin: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(onClick = onOpenLogin),
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
                text = LanguageModeUtil.displayText("点击登录百合会论坛"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoggedInProfilePendingCard(onRetry: () -> Unit) {
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
                text = LanguageModeUtil.displayText("已登录"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = LanguageModeUtil.displayText("个人资料暂未同步"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(LanguageModeUtil.displayText("重新同步"))
            }
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

package org.shirakawatyu.yamibo.novel.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.util.darkThemeColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String?,
    navBarVM: BottomNavBarVM,
    selectedRoute: String? = currentRoute
) {
    if (!navBarVM.showBottomNavBar) return

    val uiState by navBarVM.uiState.collectAsState()
    val webProgress by GlobalData.webProgress.collectAsState()
    val animatedProgress = remember { Animatable(0f) }
    val pageList = listOf("MangaHomePage", "FavoritePage", "BBSPage", "MinePage")
    val baseRoute =
        if (selectedRoute?.startsWith("MineHistoryPostPage") == true) "MinePage" else selectedRoute

    LaunchedEffect(webProgress) {
        val target = webProgress.toFloat() / 100f
        if (target < animatedProgress.value || target == 0f) {
            animatedProgress.snapTo(target)
        } else {
            animatedProgress.animateTo(
                target,
                tween(durationMillis = 250, easing = LinearEasing)
            )
        }
    }

    val containerColor = darkThemeColor(YamiboColors.onSurface) { navBar }
    val selectedColor = darkThemeColor(YamiboColors.primary) { onPrimary }
    val unselectedColor = darkThemeColor(YamiboColors.primary.copy(alpha = 0.62f)) {
        onPrimary.copy(alpha = 0.68f)
    }
    val indicatorColor = darkThemeColor(YamiboColors.tertiary) {
        onPrimary.copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(containerColor)
    ) {
        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(50.dp),
            windowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = containerColor
        ) {
            uiState.icons.forEachIndexed { index, icon ->
                val targetRoute = pageList[index]
                val selected = baseRoute == targetRoute
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            // 单击：仅切换板块；已在本板块内时不做任何事，避免误触重载。
                            onClick = {
                                navBarVM.returnToHome(
                                    index, currentRoute, navController, notifyHome = false
                                )
                            },
                            // 长按：回到该板块主页（论坛首页 / 个人资料 / 漫画首页 / 收藏首页）。
                            onLongClick = {
                                navBarVM.returnToHome(
                                    index, currentRoute, navController, notifyHome = true
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(32.dp)
                                .background(indicatorColor, RoundedCornerShape(999.dp))
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) selectedColor else unselectedColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = webProgress in 1..99 &&
                    (baseRoute == "BBSPage" || baseRoute == "MinePage"),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = darkThemeColor(YamiboColors.primary) { primary },
                trackColor = darkThemeColor(YamiboColors.primary.copy(alpha = 0.1f)) {
                    primary.copy(alpha = 0.1f)
                },
                strokeCap = StrokeCap.Round
            )
        }
    }
}

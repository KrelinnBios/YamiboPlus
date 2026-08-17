package org.shirakawatyu.yamibo.novel.ui.vm

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.ui.state.BottomNavBarState

class BottomNavBarVM : ViewModel() {
    private val _uiState = MutableStateFlow(BottomNavBarState())
    val uiState = _uiState.asStateFlow()
    private val pageList = listOf("MangaHomePage", "FavoritePage", "BBSPage", "MinePage")
    var isBbsAtRoot by mutableStateOf(true)
    var isMineAtRoot by mutableStateOf(true)

    /**
     * 底栏可见性 = 路由许可（由 [org.shirakawatyu.yamibo.novel.ui.state.BottomBarPolicy]
     * 在路由变化时统一设置）&& 无瞬时抑制（页面内全屏、输入法弹出等场景，
     * 由页面通过 [setBottomBarSuppressed] 声明，页面离开时必须清除）。
     * 页面不要再直接改这个结果，避免和集中式策略互相覆盖。
     */
    var routeAllowsBottomBar by mutableStateOf(true)
        private set
    var bottomBarSuppressed by mutableStateOf(false)
        private set
    var bottomBarScrollSuppressed by mutableStateOf(false)
        private set
    val showBottomNavBar: Boolean
        get() = routeAllowsBottomBar && !bottomBarSuppressed && !bottomBarScrollSuppressed

    private val _goHomeEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val goHomeEvent = _goHomeEvent.asSharedFlow()
    private val _categoryHomeEvent = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val categoryHomeEvent = _categoryHomeEvent.asSharedFlow()
    private val _scrollToTopEvent = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    fun returnToHome(
        index: Int,
        currentRoute: String?,
        navController: NavController,
        notifyHome: Boolean = true
    ) {
        if (index < 0 || index >= pageList.size) {
            Log.e("BottomNavBarVM", "Invalid navigation index $index")
            return
        }
        val targetRoute = pageList[index]
        val routeChanged = currentRoute != targetRoute
        if (routeChanged) {
            routeAllowsBottomBar = true
            val isTransientWebRoute =
                currentRoute?.startsWith("MangaWebPage") == true ||
                        currentRoute?.startsWith("ReaderWebPage") == true ||
                        currentRoute?.startsWith("OtherWebPage") == true
            val returnedToExistingDestination =
                navController.popBackStack(targetRoute, inclusive = false)

            if (!returnedToExistingDestination) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = !isTransientWebRoute
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        if (!notifyHome) return
        if (routeChanged) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(64L)
                _goHomeEvent.emit(targetRoute)
            }
        } else {
            _goHomeEvent.tryEmit(targetRoute)
        }
    }

    fun requestScrollToTop(index: Int) {
        if (index !in pageList.indices) return
        _scrollToTopEvent.tryEmit(index)
    }

    fun requestCategoryHome(
        index: Int,
        currentRoute: String?,
        navController: NavController
    ) {
        if (index !in pageList.indices) return
        val targetRoute = pageList[index]
        returnToHome(index, currentRoute, navController, notifyHome = false)
        viewModelScope.launch {
            kotlinx.coroutines.delay(64L)
            _categoryHomeEvent.emit(index)
        }
    }

    /** 路由变化时由集中式策略（BottomBarPolicy）调用，页面不要直接调用。 */
    fun applyRouteBottomBarPolicy(allows: Boolean) {
        routeAllowsBottomBar = allows
        // 页面销毁时可能留下瞬时抑制状态（例如登录/网页页返回主页面），
        // 进入新的普通路由后必须清除，不能让底栏永久消失。
        if (allows) {
            bottomBarSuppressed = false
            bottomBarScrollSuppressed = false
        }
    }

    /**
     * 页面内瞬时抑制（全屏、输入法弹出等）。抑制只在该页面存活期间有效，
     * 页面 onDispose 时必须调用 updateBottomBarSuppressed(false) 清除。
     */
    fun updateBottomBarSuppressed(suppressed: Boolean) {
        bottomBarSuppressed = suppressed
    }

    /** 普通内容页随滚动方向临时隐藏/显示底栏。 */
    fun updateBottomBarScrollSuppressed(suppressed: Boolean) {
        bottomBarScrollSuppressed = suppressed
    }
}

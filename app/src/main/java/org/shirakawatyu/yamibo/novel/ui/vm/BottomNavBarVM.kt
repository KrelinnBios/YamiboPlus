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
    var showBottomNavBar by mutableStateOf(true)
        private set
    private val _goHomeEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val goHomeEvent = _goHomeEvent.asSharedFlow()

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
            showBottomNavBar = true
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

    fun setBottomNavBarVisibility(visible: Boolean) {
        showBottomNavBar = visible
    }
}

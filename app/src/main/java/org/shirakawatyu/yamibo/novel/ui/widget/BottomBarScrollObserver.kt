package org.shirakawatyu.yamibo.novel.ui.widget

import android.webkit.WebView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import kotlin.math.abs

/** 普通原生列表下滑时收起底栏，上滑或回到顶部时恢复。 */
@Composable
fun ObserveBottomBarLazyListScroll(
    listState: LazyListState,
    bottomNavBarVM: BottomNavBarVM
) {
    LaunchedEffect(listState, bottomNavBarVM) {
        var previous: Pair<Int, Int>? = null
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { current ->
            val old = previous
            if (old != null) {
                val movingDown = current.first > old.first ||
                    current.first == old.first && current.second > old.second
                val movingUp = current.first < old.first ||
                    current.first == old.first && current.second < old.second
                when {
                    current.first == 0 && current.second == 0 ->
                        bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                    movingDown -> bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                    movingUp -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                }
            }
            previous = current
        }
    }
    DisposableEffect(listState, bottomNavBarVM) {
        onDispose { bottomNavBarVM.updateBottomBarScrollSuppressed(false) }
    }
}

/** 普通原生滚动容器下滑时收起底栏，上滑或回到顶部时恢复。 */
@Composable
fun ObserveBottomBarScrollState(
    scrollState: ScrollState,
    bottomNavBarVM: BottomNavBarVM
) {
    LaunchedEffect(scrollState, bottomNavBarVM) {
        var previous: Int? = null
        snapshotFlow { scrollState.value }.collect { current ->
            val old = previous
            if (old != null) {
                when {
                    current == 0 -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                    current > old -> bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                    current < old -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                }
            }
            previous = current
        }
    }
    DisposableEffect(scrollState, bottomNavBarVM) {
        onDispose { bottomNavBarVM.updateBottomBarScrollSuppressed(false) }
    }
}

/** WebView 页面使用原生滚动回调实现同样的底栏行为。 */
@Composable
fun ObserveBottomBarWebViewScroll(
    webView: WebView,
    bottomNavBarVM: BottomNavBarVM
) {
    DisposableEffect(webView, bottomNavBarVM) {
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            when {
                scrollY <= 0 -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                abs(delta) >= 3 && delta > 0 -> bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                abs(delta) >= 3 && delta < 0 -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
            }
        }
        onDispose {
            webView.setOnScrollChangeListener(null)
            bottomNavBarVM.updateBottomBarScrollSuppressed(false)
        }
    }
}

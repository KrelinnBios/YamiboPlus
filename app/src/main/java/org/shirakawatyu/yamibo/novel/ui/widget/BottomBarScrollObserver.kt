package org.shirakawatyu.yamibo.novel.ui.widget

import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import kotlin.math.abs

/**
 * 普通原生列表：只有用户手动拖拽才改变底栏显隐。
 * 程序滚动（点击下一页后列表回顶、回到板块首页等）不产生拖拽交互，
 * 因此不再触发「隐藏后又弹出来」的抖动。
 */
@Composable
fun ObserveBottomBarLazyListScroll(
    listState: LazyListState,
    bottomNavBarVM: BottomNavBarVM
) {
    LaunchedEffect(listState, bottomNavBarVM) {
        var dragStart: Pair<Int, Int>? = null
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragStart =
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }

                is DragInteraction.Stop -> {
                    val start = dragStart
                    dragStart = null
                    if (start == null) return@collect
                    val current =
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    val movedDown = current.first > start.first ||
                            (current.first == start.first && current.second > start.second)
                    val movedUp = current.first < start.first ||
                            (current.first == start.first && current.second < start.second)
                    when {
                        // 手指上滑看下面的内容：收起底栏
                        movedDown -> bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                        // 手指下滑往回看：恢复底栏
                        movedUp -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                    }
                }

                is DragInteraction.Cancel -> dragStart = null
                else -> Unit
            }
        }
    }
    DisposableEffect(listState, bottomNavBarVM) {
        onDispose { bottomNavBarVM.updateBottomBarScrollSuppressed(false) }
    }
}

/** 普通原生滚动容器：同样只在用户手动拖拽时改变底栏显隐。 */
@Composable
fun ObserveBottomBarScrollState(
    scrollState: ScrollState,
    bottomNavBarVM: BottomNavBarVM
) {
    LaunchedEffect(scrollState, bottomNavBarVM) {
        var dragStart: Int? = null
        scrollState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragStart = scrollState.value

                is DragInteraction.Stop -> {
                    val start = dragStart
                    dragStart = null
                    if (start == null) return@collect
                    val current = scrollState.value
                    when {
                        current > start ->
                            bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                        current < start ->
                            bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                    }
                }

                is DragInteraction.Cancel -> dragStart = null
                else -> Unit
            }
        }
    }
    DisposableEffect(scrollState, bottomNavBarVM) {
        onDispose { bottomNavBarVM.updateBottomBarScrollSuppressed(false) }
    }
}

/**
 * WebView 页面：程序滚动（JS 滚动、整页跳转后回顶）不会触发方向判定，
 * 只有用户手指按住屏幕产生的滚动才改变底栏显隐。
 */
@Composable
fun ObserveBottomBarWebViewScroll(
    webView: WebView,
    bottomNavBarVM: BottomNavBarVM
) {
    DisposableEffect(webView, bottomNavBarVM) {
        var userTouching = false
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> userTouching = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> userTouching = false
            }
            false
        }
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (!userTouching) return@setOnScrollChangeListener
            val delta = scrollY - oldScrollY
            when {
                scrollY <= 0 -> bottomNavBarVM.updateBottomBarScrollSuppressed(false)
                abs(delta) >= 3 && delta > 0 ->
                    bottomNavBarVM.updateBottomBarScrollSuppressed(true)
                abs(delta) >= 3 && delta < 0 ->
                    bottomNavBarVM.updateBottomBarScrollSuppressed(false)
            }
        }
        onDispose {
            webView.setOnTouchListener(null)
            webView.setOnScrollChangeListener(null)
            bottomNavBarVM.updateBottomBarScrollSuppressed(false)
        }
    }
}

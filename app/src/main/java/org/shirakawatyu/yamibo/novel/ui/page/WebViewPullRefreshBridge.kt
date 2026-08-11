package org.shirakawatyu.yamibo.novel.ui.page

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.atomic.AtomicBoolean

class WebViewPullRefreshBridge {
    private val editableFocused = AtomicBoolean(false)

    @JavascriptInterface
    fun setEditableFocused(focused: Boolean) {
        editableFocused.set(focused)
    }

    fun isEditableFocused(): Boolean = editableFocused.get()

    fun reset() {
        editableFocused.set(false)
    }
}

fun SwipeRefreshLayout.guardWebViewPullRefresh(
    webView: WebView,
    bridge: WebViewPullRefreshBridge
) {
    setOnChildScrollUpCallback { _, _ ->
        bridge.isEditableFocused() || webView.canScrollVertically(-1)
    }
}

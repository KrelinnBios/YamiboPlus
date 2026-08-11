package org.shirakawatyu.yamibo.novel.ui.vm

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import org.shirakawatyu.yamibo.novel.util.WebViewPool

@SuppressLint("StaticFieldLeak")
class MinePageVM : ViewModel() {
    var cachedWebView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var releaseRunnable: Runnable? = null

    fun getOrAcquireWebView(context: Context): WebView {
        cancelRelease()
        if (cachedWebView == null) {
            cachedWebView = WebViewPool.acquire(context)
        } else {
            (cachedWebView?.context as? MutableContextWrapper)?.baseContext = context
        }
        return cachedWebView!!
    }

    // 延迟5分钟销毁。必须绑定到调用方持有的实例，避免旧页面退出时误释放刚重建的新 WebView。
    fun scheduleRelease(webView: WebView, delayMs: Long = 300000L) {
        if (cachedWebView !== webView) return
        cancelRelease()
        releaseRunnable = Runnable {
            if (cachedWebView === webView) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                WebViewPool.release(webView)
                cachedWebView = null
            }
            releaseRunnable = null
        }
        handler.postDelayed(releaseRunnable!!, delayMs)
    }

    /** 渲染进程退出后立刻丢弃失效实例，下一次组合会从池中获取新的 WebView。 */
    fun discard(webView: WebView) {
        if (cachedWebView === webView) {
            cancelRelease()
            cachedWebView = null
        }
        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            WebViewPool.discard(webView)
        }.onFailure {
            // onRenderProcessGone 回调本身不能再抛异常，否则恢复路径会变成主线程闪退。
            Log.e("MinePageVM", "Discard crashed WebView failed", it)
        }
    }

    fun cancelRelease() {
        releaseRunnable?.let { handler.removeCallbacks(it) }
        releaseRunnable = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelRelease()
        cachedWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            WebViewPool.release(webView)
        }
        cachedWebView = null
    }
}

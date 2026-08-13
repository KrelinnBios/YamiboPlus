package org.shirakawatyu.yamibo.novel.global

import android.content.Context
import android.util.DisplayMetrics
import android.webkit.WebChromeClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.shirakawatyu.yamibo.novel.module.YamiboWebViewClient
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboAppTheme
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemeMode
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemePalette
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboThemePreference
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.LanguageModeUtil

class GlobalData {

    companion object {
        val webViewClient = YamiboWebViewClient()
        val webChromeClient = WebChromeClient()
        var dataStore: DataStore<Preferences>? = null
        var applicationContext: Context? = null
        var displayMetrics: DisplayMetrics? = null
        var currentCookie: String = ""
        // 当前登录用户 uid，用于屏蔽功能排除自己发布的帖子/楼层。登录后本地持久化。
        var currentUid by mutableStateOf("")
        // 当前登录用户名和头像
        var currentUserName by mutableStateOf("")
        var currentUserAvatar by mutableStateOf<String?>(null)
        var isAppInitialized by mutableStateOf(false)
        val cookieFlow: Flow<String> by lazy {
            CookieUtil.getCookieFlow()
        }
        var tempMangaUrls: List<String> = emptyList()
        var tempMangaIndex: Int = 0
        var tempHtml: String = ""
        var tempTitle: String = ""

        val isAutoSignInEnabled = mutableStateOf(true)
        val webProgress = MutableStateFlow(0)
        val homePageRoute = MutableStateFlow("BBSPage")
        val isDarkMode = MutableStateFlow(false)
        val appTheme = MutableStateFlow(YamiboAppTheme.CLASSIC_LIGHT)
        val themeMode = MutableStateFlow(YamiboThemeMode.SYSTEM)
        val themePalette = MutableStateFlow(YamiboThemePalette.DEFAULT)
        val pureBlackMode = MutableStateFlow(false)
        val darkModeTheme = MutableStateFlow(0)
        val lightModeTheme = MutableStateFlow(0)
        val isCustomDnsEnabled = MutableStateFlow(false)
        val isAutoVersionUpdateEnabled = MutableStateFlow(true)
        val isAutoClearCacheEnabled = MutableStateFlow(true)
        val isDnsOptimizationEnabled = MutableStateFlow(true)
        val dnsOptimizationMode = MutableStateFlow("auto")
        val customDnsUrl = MutableStateFlow("")
        val languageMode = MutableStateFlow(LanguageModeUtil.SIMPLIFIED)

        val pendingClipboardUrl = MutableStateFlow<String?>(null)
        var lastClipboardUrl: String? = null
        val pendingDeepLinkUrl = MutableStateFlow<String?>(null)

        fun applyThemePreference(
            preference: YamiboThemePreference,
            systemDark: Boolean
        ): YamiboAppTheme {
            themeMode.value = preference.mode
            themePalette.value = preference.palette
            pureBlackMode.value = preference.pureBlack
            return preference.resolve(systemDark).also { resolvedTheme ->
                appTheme.value = resolvedTheme
                isDarkMode.value = resolvedTheme.isDark
            }
        }
    }
}

package org.shirakawatyu.yamibo.novel.ui.state

/**
 * 底栏（底部导航栏）全局可见性策略：集中式路由注册表。
 *
 * 普通内容页显示底栏，只有沉浸式阅读页面固定隐藏底栏。
 *
 * 判定规则：
 * 1. 路由为 null（导航图未就绪）时默认显示；
 * 2. 命中 [HIDDEN_ROUTE_PREFIXES] 中任一前缀则隐藏；
 * 3. 其余路由一律显示（白名单式兜底：新增普通页面默认带底栏）。
 *
 * 注意：路由使用前缀匹配，登记时写路由名（如 "ReaderPage"），
 * 带参数的路由（如 "ReaderPage/https%3A..."）也会命中。
 */
object BottomBarPolicy {

    /** 固定隐藏底栏的路由前缀。普通页面由滚动方向临时隐藏。 */
    private val hiddenRouteGroups: LinkedHashMap<String, List<String>> = linkedMapOf(
        "沉浸式阅读" to listOf(
            // 小说原生阅读器
            "ReaderPage",
            // 漫画原生阅读器
            "NativeMangaPage",
            // 漫画 WebView 兜底阅读器（识别失败/WebView 模式看漫画，同样全屏翻页阅读）
            "MangaWebPage",
            // 小说原帖 WebView 阅读（阅读器「查看原帖」入口的 WebView 阅读场景）
            "ReaderWebPage"
        )
    )

    private val hiddenRoutePrefixes: List<String> = hiddenRouteGroups.values.flatten()

    /** 当前路由是否显示底栏。null 路由（导航未就绪）默认显示。 */
    fun shouldShowBottomBar(route: String?): Boolean {
        if (route == null) return true
        return hiddenRoutePrefixes.none { route.startsWith(it) }
    }

    /** 调试用：路由所属的隐藏分组；显示底栏的页面返回 null。 */
    fun hiddenGroupOf(route: String?): String? {
        if (route == null) return null
        return hiddenRouteGroups.entries
            .firstOrNull { (_, prefixes) -> prefixes.any { route.startsWith(it) } }
            ?.key
    }
}

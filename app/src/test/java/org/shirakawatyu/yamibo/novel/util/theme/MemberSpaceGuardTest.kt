package org.shirakawatyu.yamibo.novel.util.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.util.PageJsScripts

class MemberSpaceGuardTest {
    @Test
    fun customDiySpacesAreExcludedFromThemeInjection() {
        // body#space 且用了 data/attachment 自定义背景图 → 自定义 DIY 空间，排除暗黑
        assertTrue(
            MemberSpaceGuard.isMemberSpaceHtml(
                "<body id=\"space\"><style>.a{background-image:url('https://bbs.yamibo.com/data/attachment/album/x.jpg')}</style></body>"
            )
        )
        assertTrue(
            MemberSpaceGuard.isMemberSpaceHtml(
                "<body id='space'><div style=\"background:url(https://bbs.yamibo.com/data/attachment/album/y.png)\"></div></body>"
            )
        )
    }

    @Test
    fun plainSpacesAndOtherPagesReceiveTheme() {
        // body#space 但只有 static 默认背景（无自定义）→ 照常暗黑
        assertFalse(
            MemberSpaceGuard.isMemberSpaceHtml(
                "<body id=\"space\"><style>.a{background-image:url(https://bbs.yamibo.com/static/image/feed/blog_b.png)}</style></body>"
            )
        )
        // body#space 但完全没有背景图 → 照常暗黑
        assertFalse(MemberSpaceGuard.isMemberSpaceHtml("<body id=\"space\"></body>"))
        // 非空间页 → 照常暗黑
        assertFalse(MemberSpaceGuard.isMemberSpaceHtml("<body id=\"nv_home\" class=\"pg_space\">"))
    }

    @Test
    fun runtimeJsUsesTheSharedRuleSource() {
        assertTrue(
            PageJsScripts.getDarkModeSetJs(enable = true)
                .contains(MemberSpaceGuard.jsExpression())
        )
    }

    @Test
    fun desktopViewportIsReplacedOrInsertedForDesktopPages() {
        val withViewport = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body><div id="toptb"></div></body></html>
        """.trimIndent()

        val replaced = PageJsScripts.applyDesktopViewportForWebView(withViewport, 0.375)
        assertTrue(
            replaced.contains(
                "<meta name=\"viewport\" content=\"width=1200, initial-scale=0.375, user-scalable=yes\">"
            )
        )
        assertFalse(replaced.contains("width=device-width"))

        val missingViewport = "<html><head><title>x</title></head><body><div id='toptb'></div></body></html>"
        val inserted = PageJsScripts.applyDesktopViewportForWebView(missingViewport, 0.3)
        assertTrue(
            inserted.contains(
                "<head><meta name=\"viewport\" content=\"width=1200, initial-scale=0.300, user-scalable=yes\"><title>x</title>"
            )
        )
    }

    @Test
    fun plainSpacePagesUseResponsiveViewport() {
        val plainSpace = """
            <html><head></head><body id="space"><div id="toptb"></div></body></html>
        """.trimIndent()
        // 空间页用 width=device-width 但不写死 initial-scale，交给 loadWithOverviewMode 缩放，
        // 避免 1200px 版面 1:1 溢出（暗黑电脑版主页显示异常的根因）。
        val responsiveSpace = PageJsScripts.applyDesktopViewportForWebView(plainSpace, 0.3)
        assertTrue(
            responsiveSpace.contains(
                "<meta name=\"viewport\" content=\"width=device-width, user-scalable=yes\">"
            )
        )
        assertFalse(responsiveSpace.contains("initial-scale"))

        // spacecp / BLOG 个人主页（#ct.ct3_a）是定宽多栏布局：device-width 下 .mn 溢出、侧栏摞到主内容上
        // （暗黑与原色不一致），改用 width=1200 + initial-scale 缩放，两栏并排、与原色一致。
        val blogSpacecp = """
            <html><head><meta name="viewport" content="width=device-width"></head>
            <body id="nv_home" class="pg_space"><div id="toptb"></div>
            <div id="ct" class="ct3_a wp cl"><div class="appl"></div><div class="mn"></div><div class="sd"></div></div></body></html>
        """.trimIndent()
        val fixedBlogSpacecp = PageJsScripts.applyDesktopViewportForWebView(blogSpacecp, 0.3)
        assertTrue(fixedBlogSpacecp.contains("width=1200, initial-scale=0.300, user-scalable=yes"))
        assertFalse(fixedBlogSpacecp.contains("width=device-width"))

        val blogThreadList = """
            <html><head><meta name="viewport" content="width=device-width"></head>
            <body id="nv_home" class="pg_space"><div id="toptb"></div>
            <div id="ct" class="ct2_a wp cl"><div class="appl"></div><div class="mn"><div class="tl"></div></div></div></body></html>
        """.trimIndent()
        val fixedBlogThreadList = PageJsScripts.applyDesktopViewportForWebView(blogThreadList, 0.3)
        assertTrue(fixedBlogThreadList.contains("width=1200, initial-scale=0.300, user-scalable=yes"))
        assertFalse(fixedBlogThreadList.contains("width=device-width"))

        val diySpace = """
            <html><head></head><body id="space"><div id="toptb"></div>
            <style>.a{background-image:url(https://bbs.yamibo.com/data/attachment/album/x.jpg)}</style></body></html>
        """.trimIndent()
        val fixedDiy = PageJsScripts.applyDesktopViewportForWebView(diySpace, 0.3)
        assertTrue(fixedDiy.contains("width=1200, initial-scale=0.300, user-scalable=yes"))
    }

    @Test
    fun nonDesktopPagesKeepTheirViewport() {
        val mobile = "<html><head><meta name=\"viewport\" content=\"width=device-width\"></head><body id=\"nv_forum\"></body></html>"
        assertEquals(mobile, PageJsScripts.applyDesktopViewportForWebView(mobile, 0.3))
    }

    @Test
    fun desktopFitScaleUsesDensityIndependentWidth() {
        assertEquals(0.3, PageJsScripts.calculateDesktopFitScale(widthPx = 1080, density = 3f), 0.0001)
        assertEquals(0.0, PageJsScripts.calculateDesktopFitScale(widthPx = 0, density = 3f), 0.0001)
        assertEquals(0.0, PageJsScripts.calculateDesktopFitScale(widthPx = 1080, density = 0f), 0.0001)
    }
}

package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboPostLinkUtilTest {
    @Test
    fun extractsLinkFromClipboardShareText() {
        assertEquals(
            "https://bbs.yamibo.com/thread-572320-3-1.html?mobile=2",
            YamiboPostLinkUtil.extractPostUrl(
                "分享帖子：[是否应该稍微放开对AI的禁令]（https://bbs.yamibo.com/thread-572320-3-1.html）。"
            )
        )
    }

    @Test
    fun extractsViewThreadLinkRegardlessOfQueryOrder() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?tid=572320&mod=viewthread&mobile=2",
            YamiboPostLinkUtil.extractPostUrl(
                "看看 https://m.yamibo.com/forum.php?tid=572320&mod=viewthread&highlight=test 。"
            )
        )
    }

    @Test
    fun normalizesSeoThreadLinkAndKeepsPage() {
        assertEquals(
            "https://bbs.yamibo.com/thread-572320-3-1.html?mobile=2",
            YamiboPostLinkUtil.normalizePostUrl(
                "yamibo.com/thread-572320-3-1.html"
            )
        )
    }

    @Test
    fun acceptsDirectFloorRedirectLink() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=redirect&goto=findpost&ptid=572320&pid=41559541&mobile=2",
            YamiboPostLinkUtil.extractPostUrl(
                "https://bbs.yamibo.com/forum.php?mod=redirect&goto=findpost&ptid=572320&pid=41559541"
            )
        )
    }

    @Test
    fun rejectsNonPostAndImageLinks() {
        assertNull(YamiboPostLinkUtil.extractPostUrl("https://bbs.yamibo.com/forum.php"))
        assertNull(
            YamiboPostLinkUtil.extractPostUrl(
                "https://bbs.yamibo.com/data/attachment/forum/example.jpg"
            )
        )
    }

    @Test
    fun rewritesTagPageLinkToDesktopTemplate() {
        // 标签页是电脑版专属页，手机版会话下必须强制 mobile=no
        assertEquals(
            "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=no",
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://bbs.yamibo.com/misc.php?mod=tag&id=20563"
            )
        )
        // 已带 mobile=2 的也要改成 mobile=no
        assertEquals(
            "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=no",
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=2"
            )
        )
    }

    @Test
    fun normalizesNonPostForumPagesForMobileTemplate() {
        // 版块下一页补参数，m 子域名顺便归一到主论坛域名。
        assertEquals(
            "https://bbs.yamibo.com/forum-30-2.html?mobile=2",
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://m.yamibo.com/forum-30-2.html",
                desktopSession = false
            )
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&page=2&mobile=2",
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&page=2",
                desktopSession = false
            )
        )
        // 帖子 URL 由页面脚本或 WebView 原生导航独占处理，不能在共享拦截层二次 loadUrl。
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://m.yamibo.com/thread-520058-2-1.html",
                desktopSession = false
            )
        )
    }

    @Test
    fun detectsDesktopSessionFromMobileCookie() {
        // 点过论坛底部「电脑版」后 Discuz 写入带站点前缀的 mobile=no cookie
        assertTrue(
            YamiboPostLinkUtil.isDesktopSessionCookie(
                "EeqY_2132_saltkey=abc; EeqY_2132_mobile=no; EeqY_2132_sid=xyz"
            )
        )
        assertTrue(YamiboPostLinkUtil.isDesktopSessionCookie("mobile=no"))
        // 手机版会话（mobile=2 或无 mobile cookie）不是电脑版
        assertFalse(YamiboPostLinkUtil.isDesktopSessionCookie("EeqY_2132_mobile=2"))
        assertFalse(YamiboPostLinkUtil.isDesktopSessionCookie("EeqY_2132_saltkey=abc"))
        assertFalse(YamiboPostLinkUtil.isDesktopSessionCookie(null))
        // 名字只是碰巧含 mobile 的 cookie 不误判
        assertFalse(YamiboPostLinkUtil.isDesktopSessionCookie("automobile=no"))
    }

    @Test
    fun recognizesOnlyExplicitForumTemplateSwitches() {
        assertEquals(
            true,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mobile=no"
            )
        )
        assertEquals(
            false,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mobile=2"
            )
        )
        assertEquals(
            false,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/?mobile=yes"
            )
        )
        assertEquals(
            false,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?showmobile=yes"
            )
        )
        assertNull(
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2"
            )
        )
        assertEquals(
            true,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=no",
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2"
            )
        )
        assertEquals(
            true,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/thread-573162-2-1.html?mobile=no#pid41559541",
                "https://bbs.yamibo.com/thread-573162-2-1.html?mobile=2#pid41559541"
            )
        )
        assertEquals(
            true,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=no",
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162"
            )
        )
        assertEquals(
            false,
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2",
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=no"
            )
        )
        // 分页、标签页携带 mobile 参数不代表用户切换了全局模板
        assertNull(
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&page=2&mobile=no"
            )
        )
        assertNull(
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=no"
            )
        )
        assertNull(
            YamiboPostLinkUtil.explicitDesktopTemplateSelection(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573163&mobile=no",
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2"
            )
        )
    }

    @Test
    fun normalizesForumLinksForSelectedTemplate() {
        // 当前模板已经匹配时不再改写
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2",
                desktopSession = false
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mobile=no",
                desktopSession = false
            )
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&page=2&mobile=2",
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&page=2&mobile=no",
                desktopSession = false
            )
        )
        // 用户已经点过电脑版后，非帖子页面继续保持电脑版。
        assertEquals(
            "https://bbs.yamibo.com/forum-30-2.html?mobile=no",
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum-30-2.html",
                desktopSession = true
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&mobile=2",
                desktopSession = true,
                currentUrl = "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&mobile=no"
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/thread-520058-2-1.html?mobile=2",
                desktopSession = true,
                currentUrl = "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&mobile=no"
            )
        )
        // 同页 mobile=2 才是明确点击「手机版」，不拦截改写
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&mobile=2",
                desktopSession = false,
                currentUrl = "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=30&mobile=no"
            )
        )
        // 真实电脑版页脚使用 showmobile=yes 切回手机版，同样不得补回 mobile=no
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/forum.php?showmobile=yes",
                desktopSession = true,
                currentUrl = "https://bbs.yamibo.com/forum-33-1.html"
            )
        )
        // 电脑版专属标签页、明确进入的电脑版空间页仍保留电脑版
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=no",
                desktopSession = false
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/home.php?mod=space&uid=399468&mobile=no",
                desktopSession = false
            )
        )
        // 非常规论坛 HTML 页面、附件和站外链接不改写
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/search.php?mod=forum&mobile=no",
                desktopSession = false
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/data/attachment/forum/example.jpg",
                desktopSession = false
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://example.com/forum.php?mod=viewthread&tid=1",
                desktopSession = false
            )
        )
    }

    @Test
    fun everyPostNavigationBypassesWebViewTemplateRewrite() {
        val postUrls = listOf(
            // 帖子底部分页选择器会生成这种无 mobile 参数的 viewthread URL。
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&page=2",
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162&page=3&mobile=2",
            // 实际页面选择“第2页”后生成的无 mobile SEO 地址。
            "https://bbs.yamibo.com/thread-572407-2-1.html",
            "https://bbs.yamibo.com/thread-520058-2-1.html",
            "https://bbs.yamibo.com/thread-520058-2-1.html?mobile=no",
            "https://bbs.yamibo.com/forum.php?mod=redirect&goto=findpost&ptid=572320&pid=41559541"
        )

        postUrls.forEach { url ->
            assertNull(
                YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                    url,
                    desktopSession = false
                )
            )
            assertNull(
                YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                    url,
                    desktopSession = true
                )
            )
        }
    }

    @Test
    fun postTemplateBridgeProducesOneFinalTarget() {
        val actualPaginationUrl =
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=572407&extra=page%3D1&page=2&mobile=2#pid41559541"

        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=572407&extra=page%3D1&page=2&mobile=no#pid41559541",
            YamiboPostLinkUtil.normalizePostUrlForTemplate(
                actualPaginationUrl,
                desktopTemplate = true
            )
        )
        assertEquals(
            actualPaginationUrl,
            YamiboPostLinkUtil.normalizePostUrlForTemplate(
                actualPaginationUrl.replace("mobile=2", "mobile=no"),
                desktopTemplate = false
            )
        )
    }

    @Test
    fun desktopMhtListThreadLinksStayDesktop() {
        // 「海域區」真实样本：列表和帖子都是无 mobile 参数的 SEO URL。
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/thread-574592-1-1.html",
                desktopSession = true,
                currentUrl = "https://bbs.yamibo.com/forum-33-1.html"
            )
        )
        // 「遊戲區」真实样本：列表显式 mobile=no，帖子链接仍没有版本参数。
        assertNull(
            YamiboPostLinkUtil.normalizeForumPageTemplateUrl(
                "https://bbs.yamibo.com/thread-574285-1-1.html",
                desktopSession = true,
                currentUrl = "https://bbs.yamibo.com/forum.php?mod=forumdisplay&fid=44&mobile=no"
            )
        )
        // 列表点击实际先走 AndroidSearchNav；桥接层独立生成唯一的电脑版目标 URL。
        val bridgedUrl = YamiboPostLinkUtil.normalizePostUrlForTemplate(
            "https://bbs.yamibo.com/thread-574592-1-1.html",
            desktopTemplate = true
        )
        assertEquals(
            "https://bbs.yamibo.com/thread-574592-1-1.html?mobile=no",
            bridgedUrl
        )
        assertEquals(bridgedUrl, YamiboPostLinkUtil.normalizePostUrl(bridgedUrl))
        assertEquals(
            "https://bbs.yamibo.com/thread-574592-1-1.html?mobile=2",
            YamiboPostLinkUtil.normalizePostUrlForTemplate(
                "https://bbs.yamibo.com/thread-574592-1-1.html",
                desktopTemplate = false
            )
        )
    }

    @Test
    fun tagPageRewriteSkipsAlreadyDesktopAndNonTagLinks() {
        // 已经是 mobile=no 时返回 null，避免 loadUrl 重写循环
        assertNull(
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://bbs.yamibo.com/misc.php?mod=tag&id=20563&mobile=no"
            )
        )
        // 非标签页 misc 链接与普通帖子链接不重写
        assertNull(
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://bbs.yamibo.com/misc.php?mod=seccode"
            )
        )
        assertNull(
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573162"
            )
        )
        // 非百合会域名不重写
        assertNull(
            YamiboPostLinkUtil.normalizePcOnlyPageUrl(
                "https://example.com/misc.php?mod=tag&id=1"
            )
        )
    }

    @Test
    fun extractsThreadIdForNativeNavigation() {
        assertEquals(
            "572320",
            YamiboPostLinkUtil.extractThreadId(
                "https://bbs.yamibo.com/thread-572320-3-1.html"
            )
        )
        assertEquals(
            "572320",
            YamiboPostLinkUtil.extractThreadId(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=572320"
            )
        )
        assertEquals(
            "572320",
            YamiboPostLinkUtil.extractThreadId(
                "https://bbs.yamibo.com/forum.php?mod=redirect&goto=findpost&ptid=572320&pid=41559541"
            )
        )
        assertNull(
            YamiboPostLinkUtil.extractThreadId(
                "https://example.com/forum.php?mod=viewthread&tid=572320"
            )
        )
    }
}

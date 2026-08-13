package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageJsScriptsTest {
    @Test
    fun forumChromeKeepsPrivateMessageComposerVisible() {
        val css = PageJsScripts.getForumChromeHideCss()

        assertTrue(css.contains(".foot.flex-box:not(.foot_reply):not(.msg_post)"))
        assertTrue(css.contains("#pmform .foot_height { display: block !important; }"))
        assertTrue(css.contains("#pmform .foot.msg_post { display: flex !important; }"))
        assertFalse(css.contains(".foot.flex-box:not(.foot_reply) {"))
    }

    @Test
    fun bbsNavigationInterceptsThreadLinksBeforeLegacyListHandler() {
        val script = PageJsScripts.BBS_COMMIT_BOOTSTRAP_JS
        val nativeNavigationIndex = script.indexOf("BBS_THREAD_NAVIGATION_JS")
        val legacyListHandlerIndex = script.indexOf("THREAD_LIST_CLICK_FIX_JS")

        assertTrue(nativeNavigationIndex >= 0)
        assertTrue(legacyListHandlerIndex > nativeNavigationIndex)
        assertTrue(script.contains("document.getElementById('toptb')"))
        assertTrue(script.contains("if (kind === 'viewthread')"))
        assertTrue(script.contains("window.location.assign(navigationTarget(link))"))
        assertTrue(script.contains("} else if (window.AndroidSearchNav && window.AndroidSearchNav.navigateToPost)"))
        assertTrue(script.contains("navigateToPostWithTemplate(link.href, desktopTemplate)"))
        assertTrue(script.contains("navigateToPost(link.href)"))
        assertTrue(script.contains("event.stopImmediatePropagation()"))
    }

    @Test
    fun bbsThreadNavigationIsNotInjectedIntoOtherWebViews() {
        val marker = "__yamiboBbsThreadNavigationV6"

        assertTrue(PageJsScripts.BBS_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertTrue(PageJsScripts.BBS_MANGA_REINJECT_JS.contains(marker))
        assertFalse(PageJsScripts.MINE_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertFalse(PageJsScripts.OTHER_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertFalse(PageJsScripts.MANGA_BOOTSTRAP_JS.contains(marker))
    }

    @Test
    fun forumBlockerCoversAuxiliaryUserContent() {
        val script = PageJsScripts.getForumBlockerJs(
            enabled = true,
            itemsJson = "[]",
            isDark = false
        )

        assertTrue(script.contains("syncAuxiliaryUserContent(map)"))
        assertTrue(script.contains("div.pstl.xs1.cl a.xi2.xw1"))
        assertTrue(script.contains("[id^=\"comment_\"] > [id^=\"commentdetail_\"] .authi"))
        assertTrue(script.contains("container: '[id^=\"commentdetail_\"]'"))
        assertTrue(script.contains("tr[id] td > a[target=_blank]"))
        assertTrue(script.contains("[id^=\"ratelog_\"] .post_box > li a[href*=\"mod=space\"][href*=\"uid=\"]"))
        assertTrue(script.contains("#floatlayout_topicadmin #return_rate ~ .post_box > li"))
        assertTrue(script.contains("container: 'li'"))
        assertTrue(script.contains("div.quote > blockquote > font:first-child"))
        assertTrue(script.contains("quoteHeader.querySelector('a[target=_blank]') || quoteHeader"))
        assertTrue(script.contains("syncBlogUserContent(map)"))
        assertFalse(script.contains("if (/home\\.php/i.test(location.href))"))
        assertTrue(script.contains("#comment_ul > dl[id^=\"comment_\"]"))
        assertTrue(script.contains(".doing_list_box.threadlist > ul > li[id^=\"comment_\"]"))
        assertTrue(script.contains("a[id^=\"author_\"][href*=\"space-uid-\"]"))
        assertTrue(script.contains("a[id^=\"author_\"][href*=\"mod=space\"][href*=\"uid=\"]"))
        assertTrue(script.contains("hideAuxiliaryContent(comment, commentUser, '该用户的评论')"))
        assertTrue(script.contains("#ct .yamibo-blocked-message a.yamibo-unblock-action"))
        assertTrue(script.contains("#feed_div dl.bbda.cl"))
        assertTrue(script.contains("#feed_div > ul.el > li[id^=\"feed_\"]"))
        assertTrue(script.contains("hideAuxiliaryContent(group, groupUser, '该用户的 BLOG ')"))
        assertTrue(script.contains("td.by > cite > a[c]"))
        assertTrue(script.contains("document.querySelectorAll('p > em')"))
        assertTrue(script.contains("getBlockedUser(map, authorUid, authorName)"))
    }

    @Test
    fun forumBlockerUsesLongPressTargetsWithoutVisibleBlockButtons() {
        val script = PageJsScripts.getForumBlockerJs(
            enabled = true,
            itemsJson = "[]",
            isDark = true
        )

        assertTrue(script.contains("data-yamibo-block-target"))
        assertTrue(script.contains("document.addEventListener('touchstart'"))
        assertTrue(script.contains("document.addEventListener('contextmenu'"))
        assertTrue(script.contains("showBlockChoiceMenu(selected)"))
        assertFalse(script.contains("function makeAction(type, id, title, blocked"))
    }

    @Test
    fun simpleForumUiKeepsDailyActionsAndCollapsesLaterSections() {
        val script = PageJsScripts.FORUM_SIMPLE_UI_JS

        assertTrue(script.contains("index < 2"))
        assertTrue(script.contains("data-yamibo-section-header"))
        assertTrue(script.contains("action=postreview"))
        assertTrue(script.contains("action=reply"))
        assertTrue(script.contains("action=rate"))
        assertTrue(PageJsScripts.BBS_COMMIT_BOOTSTRAP_JS.contains("FORUM_SIMPLE_UI_JS"))
        assertTrue(PageJsScripts.BBS_MANGA_REINJECT_JS.contains("FORUM_SIMPLE_UI_JS"))
    }

    @Test
    fun compactsHugeMobileForumPageSelectorBeforeRendering() {
        val options = (1..3_638).joinToString("") { page ->
            val selected = if (page == 1_800) " selected=\"selected\"" else ""
            "<option value=\"forum.php?page=$page\"$selected>第${page}页</option>"
        }
        val html = "<html><body><select id=\"dumppage\">$options</select></body></html>"

        val compacted = PageJsScripts.compactMobileForumPageSelector(html)
        val optionCount = Regex("<option\\b", RegexOption.IGNORE_CASE).findAll(compacted).count()

        assertEquals(83, optionCount)
        assertTrue(compacted.contains("page=1\""))
        assertTrue(compacted.contains("page=1800\" selected=\"selected\""))
        assertTrue(compacted.contains("page=3638\""))
        assertFalse(compacted.contains("page=1000\""))
    }

    @Test
    fun leavesShortMobileForumPageSelectorUnchanged() {
        val options = (1..20).joinToString("") { page ->
            "<option value=\"forum.php?page=$page\">第${page}页</option>"
        }
        val html = "<select id='dumppage'>$options</select>"

        assertEquals(html, PageJsScripts.compactMobileForumPageSelector(html))
    }

    @Test
    fun ratingReturnPreservesThreadPageAndRatedPost() {
        val script = PageJsScripts.PRESERVE_RATE_POSITION_JS

        assertTrue(script.contains("yamibo:rate-context:v1"))
        assertTrue(script.contains("yamibo:pending-rate-return:v1"))
        assertTrue(script.contains("form.id === 'rateform'"))
        assertTrue(script.contains("input[name=\"referer\"]"))
        assertTrue(script.contains("window.succeedhandle_rate"))
        assertTrue(script.contains("url.hash = 'pid'"))
        assertTrue(script.contains("window.location.replace(target)"))
        assertTrue(script.contains("var result = original.apply(this, args)"))
        assertTrue(script.contains("scrollToPost(pending.pid, function()"))
        assertTrue(script.contains("document.getElementById('pid' + normalizedPid)"))
        assertTrue(script.contains("document.getElementById('post_' + normalizedPid)"))
        assertTrue(script.contains("document.getElementById('postmessage_' + normalizedPid)"))
        assertTrue(script.contains("post.scrollIntoView"))
    }

    @Test
    fun ratingReturnIsInjectedIntoEveryForumCapableWebView() {
        val marker = "__yamiboPreserveRatePositionV1"

        assertTrue(PageJsScripts.BBS_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertTrue(PageJsScripts.BBS_MANGA_REINJECT_JS.contains(marker))
        assertTrue(PageJsScripts.MINE_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertTrue(PageJsScripts.MINE_MANGA_REINJECT_JS.contains(marker))
        assertTrue(PageJsScripts.OTHER_COMMIT_BOOTSTRAP_JS.contains(marker))
        assertTrue(PageJsScripts.MANGA_BOOTSTRAP_JS.contains(marker))
    }
}

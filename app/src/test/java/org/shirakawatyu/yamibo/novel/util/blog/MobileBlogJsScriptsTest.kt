package org.shirakawatyu.yamibo.novel.util.blog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileBlogJsScriptsTest {
    @Test
    fun enhancementIsGuardedToMobileBlogPages() {
        val script = MobileBlogJsScripts.ENHANCEMENTS_JS

        assertTrue(script.contains("body.id !== 'home'"))
        assertTrue(script.contains("body.classList.contains('pg_space')"))
        assertTrue(script.contains("searchParams.get('do')"))
        assertTrue(script.contains("!== 'blog'"))
        assertTrue(script.contains("searchParams.get('id')"))
    }

    @Test
    fun invitationUsesDirectMobileNavigation() {
        val script = MobileBlogJsScripts.ENHANCEMENTS_JS

        assertTrue(script.contains("closest('#a_invite[href]')"))
        assertTrue(script.contains("event.stopImmediatePropagation()"))
        assertTrue(script.contains("inviteUrl.searchParams.set('mobile', '2')"))
        assertTrue(script.contains("location.assign(inviteUrl.href)"))
    }

    @Test
    fun reactionAreaLoadsAndSubmitsThroughNativeBridge() {
        val script = MobileBlogJsScripts.ENHANCEMENTS_JS

        assertTrue(script.contains("window.AndroidBlogReaction.load(ownerUid, blogId, activeRequest)"))
        assertTrue(script.contains("window.AndroidBlogReaction.react(ownerUid, blogId, clickId, activeRequest)"))
        assertTrue(script.contains("window.__yamiboBlogReactionReceive"))
        assertTrue(script.contains("document.querySelector('.threadlist_foot')"))
        assertTrue(script.contains("foot.parentNode.insertBefore(section, foot)"))
    }

    @Test
    fun ownBlogShowsReactionCountsButDisablesSubmission() {
        val script = MobileBlogJsScripts.enhancementsJs("615797")

        assertTrue(script.contains("var currentUid = '615797';"))
        assertTrue(script.contains("var isOwnBlog = Boolean(currentUid && ownerUid === currentUid);"))
        assertTrue(script.contains("button.disabled = isOwnBlog || busy;"))
        assertTrue(script.contains("if (!button || busy || isOwnBlog) return;"))
        assertTrue(script.contains("isOwnBlog ? '自己的日志仅可查看表态'"))
        assertTrue(script.contains("window.AndroidBlogReaction.load(ownerUid, blogId, activeRequest)"))
        assertFalse(MobileBlogJsScripts.enhancementsJs("615797';alert(1)").contains("alert(1)"))
    }

    @Test
    fun reactionAreaUsesFixedTextBarsWithoutImagesOrUserList() {
        val script = MobileBlogJsScripts.ENHANCEMENTS_JS

        assertTrue(script.contains("{ clickId: '1', label: '路过' }"))
        assertTrue(script.contains("{ clickId: '5', label: '鸡蛋' }"))
        assertTrue(script.contains("renderOptions();"))
        assertTrue(script.contains("background:var(--dz-BG-color,#551200)!important"))
        assertTrue(script.contains("background:transparent!important"))
        assertTrue(script.contains(".ybr-option:disabled{opacity:1;cursor:default}"))
        assertTrue(script.contains("meter.appendChild(countNode)"))
        assertTrue(script.contains("flex-direction:column;align-items:center;justify-content:flex-end"))
        assertTrue(script.contains(".ybr-count{display:block;margin-bottom:3px"))
        assertTrue(script.contains("margin:36px 0 10px"))
        assertFalse(script.contains(".ybr-count{position:absolute"))
        assertFalse(script.contains("<div class=\"ybr-title\">"))
        assertFalse(script.contains("border:1px solid var(--dz-BG-6"))
        assertFalse(script.contains("bar.appendChild(countNode)"))
        assertFalse(script.contains("ybr-icon"))
        assertFalse(script.contains("ybr-users"))
        assertFalse(script.contains("ybr-avatar"))
        assertFalse(script.contains("avatarUrl"))
    }
}

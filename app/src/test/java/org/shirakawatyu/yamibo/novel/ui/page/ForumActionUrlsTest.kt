package org.shirakawatyu.yamibo.novel.ui.page

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumActionUrlsTest {
    @Test
    fun buildsAuthenticatedForumActionUrls() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=post&action=reply&fid=30&tid=572320&repquote=41559541&page=2&mobile=no",
            ForumActionUrls.reply("572320", "30", "41559541", 2)
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=misc&action=postreview&tid=572320&pid=41559541&mobile=no",
            ForumActionUrls.comment("572320", "41559541")
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=misc&action=rate&tid=572320&pid=41559541&mobile=no",
            ForumActionUrls.rate("572320", "41559541")
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&uid=10086&do=blog&view=me&mobile=no",
            ForumActionUrls.userSpace("10086", "blog")
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&uid=10086&do=doing&view=me&mobile=no",
            ForumActionUrls.userSpace("10086", "doing")
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=spacecp&ac=credit&op=log&mobile=no",
            ForumActionUrls.creditLog
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&do=pm&page=1&mobile=no",
            ForumActionUrls.messages
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&do=notice&mobile=no",
            ForumActionUrls.reminders
        )
        assertEquals(ForumActionUrls.messages, ForumActionUrls.messageCenter(hasNewPrompt = false))
        assertEquals(ForumActionUrls.reminders, ForumActionUrls.messageCenter(hasNewPrompt = true))
    }
}

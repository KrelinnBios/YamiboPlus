package org.shirakawatyu.yamibo.novel.ui.page

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumActionUrlsTest {
    @Test
    fun buildsAuthenticatedForumActionUrls() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=post&action=reply&fid=30&tid=572320&repquote=41559541&page=2&mobile=2",
            ForumActionUrls.reply("572320", "30", "41559541", 2)
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=misc&action=postreview&tid=572320&pid=41559541&mobile=2",
            ForumActionUrls.comment("572320", "41559541")
        )
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=misc&action=rate&tid=572320&pid=41559541&mobile=2",
            ForumActionUrls.rate("572320", "41559541")
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&uid=10086&do=blog&view=me&mobile=2",
            ForumActionUrls.userSpace("10086", "blog")
        )
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&uid=10086&do=doing&view=me&mobile=2",
            ForumActionUrls.userSpace("10086", "doing")
        )
    }
}

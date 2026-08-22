package org.shirakawatyu.yamibo.novel.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumPostTargetTest {
    @Test
    fun extractsPageFromDesktopFindPostDestinations() {
        assertEquals(
            7,
            extractPostPage("https://bbs.yamibo.com/forum.php?mod=viewthread&tid=123&page=7#pid456")
        )
        assertEquals(3, extractPostPage("https://bbs.yamibo.com/thread-123-3-1.html#pid456"))
        assertNull(extractPostPage("https://bbs.yamibo.com/thread-123-1-1.html".substringBefore("-1-1")))
    }
}

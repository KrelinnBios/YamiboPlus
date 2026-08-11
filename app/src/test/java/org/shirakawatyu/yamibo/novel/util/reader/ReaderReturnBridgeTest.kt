package org.shirakawatyu.yamibo.novel.util.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReturnBridgeTest {
    @Test
    fun replacesDesktopMobileParameter() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=123&mobile=2#post",
            ReaderReturnBridge.forceMobileTemplate(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=123&mobile=no#post"
            )
        )
    }

    @Test
    fun appendsMobileParameterWhenMissing() {
        assertEquals(
            "https://bbs.yamibo.com/thread-123-1-1.html?mobile=2",
            ReaderReturnBridge.forceMobileTemplate(
                "https://bbs.yamibo.com/thread-123-1-1.html"
            )
        )
    }

    @Test
    fun preservesPostHashWhenAppendingMobileParameter() {
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573092&page=2&mobile=2#pid987654",
            ReaderReturnBridge.forceMobileTemplate(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573092&page=2#pid987654"
            )
        )
    }

    @Test
    fun sameUrlComparisonIgnoresPostHashAndTrailingSlash() {
        assertTrue(
            ReaderReturnBridge.sameUrlIgnoringHashAndTrailingSlash(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573092&mobile=2#pid987654",
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=573092&mobile=2/"
            )
        )
    }
}

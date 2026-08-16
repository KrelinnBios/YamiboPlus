package org.shirakawatyu.yamibo.novel.util.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadPageExtractorTest {
    @Test
    fun extractorSupportsObservedMobileThreadStructure() {
        val script = ThreadPageExtractor.SCRIPT

        assertTrue(script.contains(".plc[id^=\"pid\"]"))
        assertTrue(script.contains(".view_tit"))
        assertTrue(script.contains(".authi .mtit .y"))
        assertTrue(script.contains(".authi .mtime"))
        assertTrue(script.contains(".txtlist .mtit em"))
        assertTrue(script.contains(".page option"))
        assertTrue(script.contains("[id^=\"ratelog_\"], form#poll"))
        assertFalse(script.contains("document.documentElement.outerHTML"))
    }

    @Test
    fun browserEngineOnlyAllowsHttpsYamiboHosts() {
        assertTrue(
            YamiboBrowserEngine.isAllowedForumUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1"
            )
        )
        assertTrue(YamiboBrowserEngine.isAllowedForumUrl("https://m.yamibo.com/thread-1-1-1.html"))
        assertFalse(YamiboBrowserEngine.isAllowedForumUrl("http://bbs.yamibo.com/"))
        assertFalse(YamiboBrowserEngine.isAllowedForumUrl("https://yamibo.com.example.com/"))
        assertFalse(YamiboBrowserEngine.isAllowedForumUrl("https://example.com/"))
    }
}

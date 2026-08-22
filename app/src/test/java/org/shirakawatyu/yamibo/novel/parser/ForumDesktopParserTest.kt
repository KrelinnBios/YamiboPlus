package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumDesktopParserTest {
    @Test
    fun parsesDesktopForumDisplayAndLimitsNativePageToTwentyItems() {
        val normalRows = (1..21).joinToString("\n") { index ->
            """
            <tbody id="normalthread_${575400 + index}">
              <tr>
                <th><em>[<a href="forum.php?mod=forumdisplay&amp;fid=33&amp;filter=typeid&amp;typeid=411">水库科研</a>]</em>
                  <a class="s xst" href="thread-${575400 + index}-1-1.html">普通主题 $index</a>
                </th>
                <td class="by"><cite><a href="space-uid-${1000 + index}.html">作者$index</a></cite><em>2026-8-22 04:38</em></td>
                <td class="num"><a>12</a><em>169</em></td>
                <td class="by"><cite><a>最后回复者$index</a></cite><em><a>2026-8-22 23:14</a></em></td>
              </tr>
            </tbody>
            """.trimIndent()
        }
        val html = """
            <html><body id="nv_forum" class="pg_forumdisplay">
              <div class="bm_h"><h1><a href="forum-33-1.html">海域區</a>
                <span>今日: <strong>261</strong> | 主题: <strong>72858</strong> | 排名: <strong>2</strong></span>
              </h1></div>
              <div id="forum_rules_33">电脑版版规</div>
              <div id="fd_page_top"><div class="pg"><strong>2</strong>
                <a class="last" href="forum-33-1215.html">... 1215</a>
                <label><span title="共 1215 页"> / 1215 页</span></label>
              </div></div>
              <ul id="thread_types"><li><a href="forum.php?mod=forumdisplay&amp;fid=33&amp;filter=typeid&amp;typeid=411">水库科研<span>229</span></a></li></ul>
              <table id="threadlisttableid">
                <tbody id="stickthread_533721"><tr>
                  <th><a class="s xst" href="thread-533721-1-1.html">电脑版置顶主题</a></th>
                  <td class="by"><cite><a href="space-uid-8.html">筱林透</a></cite><em>2026-5-1 10:00</em></td>
                  <td class="num"><a>5</a><em>77592</em></td>
                  <td class="by"><cite><a>最后回复者</a></cite><em><a>2026-5-9 10:54</a></em></td>
                </tr></tbody>
                $normalRows
              </table>
            </body></html>
        """.trimIndent()

        val result = ForumApiParser.parseDesktopThreadPage(html, forumId = "33", requestedPage = 2)

        assertEquals("海域區", result.forum.name)
        assertEquals(261, result.forum.todayPostCount)
        assertEquals(72858, result.forum.threadCount)
        assertEquals(2, result.forum.rank)
        assertEquals(2, result.page)
        assertEquals(1215, result.totalPages)
        assertTrue(result.hasMore)
        assertEquals("水库科研", result.availableTypes["411"])
        assertEquals(20, result.threads.size)
        assertTrue(result.threads.first().isSticky)
        assertEquals("533721", result.threads.first().id)
        assertEquals("575419", result.threads.last().id)
        assertEquals("1019", result.threads.last().authorId)
        assertEquals(12, result.threads.last().replyCount)
        assertEquals(169, result.threads.last().viewCount)
        assertFalse(result.threads.last().isSticky)
    }
}

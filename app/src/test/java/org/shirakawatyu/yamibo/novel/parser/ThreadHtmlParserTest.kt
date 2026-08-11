package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadHtmlParserTest {
    @Test
    fun parseReaderPage_keepsAuthorizedLockedContentAndFiltersReplies() {
        val html = """
            <html>
            <head><title>测试小说 - 文學區 - 百合会</title></head>
            <body>
              <h1 id="thread_subject">测试小说</h1>
              <div id="post_1">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_1">
                    第三章
                    <div class="locked-content"><span class="locked-tip">30</span>有权限正文</div>
                </td></tr></tbody></table>
              </div>
              <div id="post_2">
                <a href="forum.php?mod=viewthread&tid=1&authorid=200">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_2">普通回复</td></tr></tbody></table>
              </div>
              <div class="pg"><a href="forum.php?mod=viewthread&tid=1&page=3">3</a></div>
            </body>
            </html>
        """.trimIndent()

        val result = ThreadHtmlParser.parseReaderPage(
            html = html,
            baseUrl = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1",
            expectedAuthorId = "100"
        )

        assertEquals("测试小说", result.title)
        assertEquals("100", result.authorId)
        assertEquals(3, result.maxPage)
        assertTrue(result.combinedHtml.contains("有权限正文"))
        assertFalse(result.combinedHtml.contains("普通回复"))
        assertFalse(result.combinedHtml.contains("locked-tip"))
    }

    @Test
    fun parseReaderPage_filtersAuthorChatterAndQuotesFromStructuredAnthology() {
        val chapterBody = "正文段落。".repeat(80)
        val html = """
            <html>
            <body>
              <h1 id="thread_subject">短篇合集</h1>
              <div id="post_1">
                <a id="postnum1"><em>1</em></a>
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_1">
                  合集说明，会包含数个短篇故事和权限内容。
                </td></tr></tbody></table>
              </div>
              <div id="post_2">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_2">
                  <i class="pstatus">本帖最后由 作者 编辑</i>
                  第一章 造化弄人<br>$chapterBody
                </td></tr></tbody></table>
              </div>
              <div id="post_3">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_3">
                  今天写了很久怎么还没写完，这是作者闲聊。
                </td></tr></tbody></table>
              </div>
              <div id="post_4">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_4">
                  <div class="quote"><blockquote>读者问：这是测试权限吗？</blockquote></div>
                  是的，只是测试。
                </td></tr></tbody></table>
              </div>
              <div id="post_5">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_5">
                  第三章 新故事<br>
                  <div class="locked-content"><span class="locked-tip">30</span>$chapterBody</div>
                </td></tr></tbody></table>
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = ThreadHtmlParser.parseReaderPage(
            html = html,
            baseUrl = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1",
            expectedAuthorId = "100"
        )

        assertTrue(result.combinedHtml.contains("合集说明"))
        assertTrue(result.combinedHtml.contains("第一章 造化弄人"))
        assertTrue(result.combinedHtml.contains("第三章 新故事"))
        assertFalse(result.combinedHtml.contains("作者闲聊"))
        assertFalse(result.combinedHtml.contains("读者问"))
        assertFalse(result.combinedHtml.contains("只是测试"))
        assertFalse(result.combinedHtml.contains("本帖最后由"))
        assertFalse(result.combinedHtml.contains("locked-tip"))
    }

    @Test
    fun parseMangaPage_usesOriginalAttachmentUrl() {
        val html = """
            <html>
            <body>
              <h1 id="thread_subject">权限漫画</h1>
              <div id="post_1">
                <a href="forum.php?mod=viewthread&tid=1&authorid=100">只看该作者</a>
                <table><tbody><tr><td class="t_f" id="postmessage_1">
                  <img src="thumb.png"
                       zoomfile="data/attachment/forum/202606/original.png">
                  <img src="static/image/smiley/smile.gif">
                </td></tr></tbody></table>
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = ThreadHtmlParser.parseMangaPage(
            html = html,
            baseUrl = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1"
        )

        assertEquals(
            listOf("https://bbs.yamibo.com/data/attachment/forum/202606/original.png"),
            result.imageUrls
        )
        assertEquals("权限漫画", result.title)
        assertTrue(result.compatibleHtml.contains("class=\"message\""))
    }

    @Test
    fun parseMangaPage_reportsPermissionPageWithoutCrashing() {
        val html = """
            <html>
            <body>
              <div id="messagetext" class="showmessage">
                抱歉，您的阅读权限不足，无法查看本帖。
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = ThreadHtmlParser.parseMangaPage(
            html = html,
            baseUrl = "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=20"
        )

        assertTrue(result.imageUrls.isEmpty())
        assertTrue(result.compatibleHtml.isEmpty())
        assertTrue(result.accessDeniedReason?.contains("权限不足") == true)
    }
}

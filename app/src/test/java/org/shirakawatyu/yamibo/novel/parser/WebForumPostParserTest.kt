package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock

class WebForumPostParserTest {
    @Test
    fun parseWebPostPage_keepsStructuredPostPollRatingAndPagination() {
        val rawJson = """
            {
              "status": "ready",
              "page": 1,
              "totalPages": 10,
              "extrasHtml": "<form id='poll'><div class='poll_txt'>单选投票, 共有 12 人参与投票</div><div class='poll_txt'>距结束还有: 2 天</div><div class='poll_box'><p><label>选项一</label><em>75.00% (9票)</em></p><span class='xi1'>已投票</span></div></form><div id='ratelog_101'><a href='forum.php?mod=misc&amp;action=viewratings&amp;pid=101'>参与人数 1</a><ul><li class='flex-box mli p0'><div>参与人数 1</div><div>积分 +1</div><div>理由</div></li><li class='flex-box mli p0'><div><a href='home.php?mod=space&amp;uid=2'>评分者</a></div><div>+1</div><div>支持</div></li></ul></div>",
              "Variables": {
                "page": 1,
                "ppp": 20,
                "forum": {"fid": "5", "name": "动漫区"},
                "thread": {
                  "tid": "570276",
                  "fid": "5",
                  "forumname": "动漫区",
                  "subject": "匿名测试主题",
                  "authorid": "100",
                  "author": "楼主",
                  "replies": 198,
                  "views": 43871,
                  "closed": 0
                },
                "postlist": [
                  {
                    "pid": "101",
                    "tid": "570276",
                    "authorid": "100",
                    "author": "楼主",
                    "anonymous": 0,
                    "dateline": "2026-4-24 21:06",
                    "number": 1,
                    "position": 1,
                    "first": 1,
                    "message": "<i class='pstatus'>本帖最后由 楼主 于 2026-4-25 09:44 编辑</i><p>正文 <a href='forum.php?mod=viewthread&amp;tid=2'>链接</a><img src='/data/attachment/forum/test.jpg'></p>",
                    "attachments": [
                      {
                        "aid": "9",
                        "filename": "附件.txt",
                        "attachment": "https://bbs.yamibo.com/forum.php?mod=attachment&amp;aid=9",
                        "isimage": 0
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val page = ForumApiParser.parseWebPostPage(rawJson, requestedPage = 1)

        assertEquals("570276", page.thread.id)
        assertEquals("匿名测试主题", page.thread.subject)
        assertEquals(198, page.thread.replyCount)
        assertEquals(43871, page.thread.viewCount)
        assertEquals(10, page.totalPages)
        assertTrue(page.hasMore)
        assertEquals(1, page.posts.size)
        val post = page.posts.single()
        assertEquals("2026-4-25 09:44", post.editedAt)
        assertEquals("附件.txt", post.attachments.single().filename)
        assertNotNull(post.poll)
        assertEquals(12, post.poll?.participantCount)
        assertNotNull(post.ratingSummary)
        assertEquals("评分者", post.ratingSummary?.ratings?.single()?.userName)
        assertTrue(post.blocks.any { it is ForumPostBlock.Image })
        assertTrue(
            post.blocks.filterIsInstance<ForumPostBlock.Text>()
                .flatMap(ForumPostBlock.Text::parts)
                .any { it.url?.contains("tid=2") == true }
        )
        assertFalse(page.thread.isClosed)
    }
}

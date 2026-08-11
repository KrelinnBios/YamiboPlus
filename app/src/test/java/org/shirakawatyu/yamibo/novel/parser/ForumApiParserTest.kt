package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock

class ForumApiParserTest {
    @Test
    fun parseForumIndex_groupsBoardsAndKeepsSubforums() {
        val result = ForumApiParser.parseForumIndex(
            """
            {
              "Variables": {
                "forumlist": [
                  {
                    "fid": "10",
                    "name": "&lt;b&gt;交流区&lt;/b&gt;",
                    "description": "日常 &amp; 讨论",
                    "threads": "12",
                    "posts": 34,
                    "todayposts": "5",
                    "sublist": [
                      {"fid": "11", "fup": "10", "name": "新人区", "threads": 2, "posts": 3}
                    ]
                  }
                ],
                "catlist": [
                  {"fid": "1", "name": "论坛分区", "forums": ["10"]}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.categories.size)
        assertEquals("论坛分区", result.categories.single().name)
        val board = result.categories.single().forums.single()
        assertEquals("交流区", board.name)
        assertEquals("日常 & 讨论", board.description)
        assertEquals(5, board.todayPostCount)
        assertEquals("新人区", board.subforums.single().name)
    }

    @Test
    fun parseThreadPage_decodesTypesAndPagination() {
        val result = ForumApiParser.parseThreadPage(
            """
            {
              "Variables": {
                "page": "1",
                "tpp": "20",
                "forum": {
                  "fid": "49",
                  "name": "文學區",
                  "threadcount": "21",
                  "posts": "80"
                },
                "threadtypes": {
                  "types": {"3": "连载 &amp; 小说"}
                },
                "forum_threadlist": [
                  {
                    "tid": "100",
                    "subject": "测试 &lt;主题&gt;",
                    "authorid": "7",
                    "author": "作者",
                    "dateline": "昨天",
                    "lastpost": "今天",
                    "lastposter": "读者",
                    "replies": "4",
                    "views": 30,
                    "displayorder": "1",
                    "typeid": "3"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals("文學區", result.forum.name)
        assertTrue(result.hasMore)
        val thread = result.threads.single()
        assertEquals("测试 <主题>", thread.subject)
        assertEquals("连载 & 小说", thread.typeName)
        assertTrue(thread.isSticky)
        assertEquals(4, thread.replyCount)
    }

    @Test
    fun parseThreadPage_usesRegularThreadCountWhenTotalIsMissing() {
        val threads = (1..20).joinToString(",") { index ->
            """{"tid":"$index","subject":"主题 $index","author":"作者","displayorder":"0"}"""
        }
        val result = ForumApiParser.parseThreadPage(
            """
            {
              "Variables": {
                "page": 2,
                "tpp": 20,
                "forum": {"fid": "30", "name": "漫画区"},
                "forum_threadlist": [$threads]
              }
            }
            """.trimIndent()
        )

        assertTrue(result.hasMore)
        assertFalse(result.threads.any { it.isSticky })
    }

    @Test(expected = IllegalStateException::class)
    fun parseForumIndex_reportsDiscuzError() {
        ForumApiParser.parseForumIndex(
            """
            {
              "Message": {
                "messageval": "group_nopermission",
                "messagestr": "抱歉，您无权访问"
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun parsePostPage_decodesNativePostContentAndPagination() {
        val result = ForumApiParser.parsePostPage(
            """
            {
              "Variables": {
                "ppp": "2",
                "thread": {
                  "tid": "572320",
                  "fid": "49",
                  "subject": "原生主题 &amp; 测试",
                  "authorid": "7",
                  "author": "楼主",
                  "replies": "3",
                  "views": "42",
                  "closed": "0"
                },
                "postlist": [
                  {
                    "pid": "41559541",
                    "tid": "572320",
                    "authorid": "7",
                    "author": "楼主",
                    "dateline": "2026-08-11",
                    "position": "1",
                    "number": "1",
                    "first": "1",
                    "message": "<p>Hello <a href='forum.php?mod=viewthread&amp;tid=572321'>下一主题</a><br><img src='/data/attachment/forum/example.jpg' alt='插图'></p>",
                    "attachments": {
                      "9": {
                        "aid": "9",
                        "filename": "资料.pdf",
                        "url": "data/attachment/forum",
                        "attachment": "2026/资料.pdf",
                        "isimage": "0"
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
            requestedPage = 1
        )

        assertEquals("原生主题 & 测试", result.thread.subject)
        assertEquals(2, result.totalPages)
        assertTrue(result.hasMore)

        val post = result.posts.single()
        assertTrue(post.isOriginalPost)
        assertEquals(1, post.floor)
        val textBlock = post.blocks.filterIsInstance<ForumPostBlock.Text>().single()
        assertEquals(
            "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=572321",
            textBlock.parts.single { it.url != null }.url
        )
        assertEquals(
            "https://bbs.yamibo.com/data/attachment/forum/example.jpg",
            post.blocks.filterIsInstance<ForumPostBlock.Image>().single().url
        )
        assertEquals("资料.pdf", post.attachments.single().filename)
        assertFalse(post.attachments.single().isImage)
    }
}

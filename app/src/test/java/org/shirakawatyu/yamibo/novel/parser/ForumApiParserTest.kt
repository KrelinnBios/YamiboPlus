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
        assertEquals(30, thread.viewCount)
        assertEquals(mapOf("3" to "连载 & 小说"), result.availableTypes)
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

    @Test
    fun parseForumPostRatingSummaries_readsMobileRatingLog() {
        val result = ForumApiParser.parseForumPostRatingSummaries(
            """
            <div id="ratelog_415">
              <a href="forum.php?mod=misc&amp;action=viewratings&amp;tid=1&amp;pid=415">参与人数 2</a>
              <a href="forum.php?mod=misc&amp;action=viewratings&amp;tid=1&amp;pid=415">查看全部评分</a>
              <ul>
                <li class="flex-box mli p0"><div>参与人数</div><div>积分 +7</div></li>
                <li class="flex-box mli p0">
                  <a href="home.php?mod=space&amp;uid=7">甲</a>
                  <div>+5</div><div>支持</div>
                </li>
                <li class="flex-box mli p0"><a href="#">查看全部评分</a></li>
              </ul>
            </div>
            """.trimIndent()
        )

        val summary = result.getValue("415")
        assertEquals("参与人数 2", summary.participantText)
        assertEquals("积分 +7", summary.scoreText)
        assertEquals("https://bbs.yamibo.com/forum.php?mod=misc&action=viewratings&tid=1&pid=415", summary.viewAllUrl)
        assertEquals("甲", summary.ratings.single().userName)
        assertEquals("+5", summary.ratings.single().score)
        assertEquals("支持", summary.ratings.single().reason)
    }

    @Test
    fun parseForumPostRatingSummaries_readsDesktopRatingLog() {
        val result = ForumApiParser.parseForumPostRatingSummaries(
            """
            <div id="ratelog_416">
              <a title="查看全部评分" href="forum.php?mod=misc&amp;action=viewratings&amp;tid=1&amp;pid=416">参与人数 1</a>
              <table class="ratl"><tr><th>参与人数</th><th>积分 +2</th></tr></table>
              <table class="ratl_l">
                <tr id="rate_1">
                  <td><a href="home.php?mod=space&amp;uid=8">乙</a></td>
                  <td>+2</td><td>赞同</td>
                </tr>
              </table>
            </div>
            """.trimIndent()
        )

        val summary = result.getValue("416")
        assertEquals("参与人数 1", summary.participantText)
        assertEquals("积分 +2", summary.scoreText)
        assertEquals("乙", summary.ratings.single().userName)
        assertEquals("+2", summary.ratings.single().score)
    }

    @Test
    fun parseForumBanners_readsForumSwiperImagesAndThreadLinks() {
        val result = ForumApiParser.parseForumBanners(
            """
            <div class="swiper-wrapper">
              <div class="swiper-slide">
                <a href="forum.php?mod=viewthread&amp;tid=573162">
                  <img src="/data/attachment/portal/banner-one.jpg">
                </a>
              </div>
              <div class="swiper-slide">
                <a href="thread-572320-1-1.html">
                  <img src="//cdn.example.com/banner-two.webp">
                </a>
              </div>
              <div class="swiper-slide"><span>无图片</span></div>
            </div>
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(
            "https://bbs.yamibo.com/data/attachment/portal/banner-one.jpg",
            result[0].imageUrl
        )
        assertEquals("573162", result[0].threadId)
        assertEquals("https://cdn.example.com/banner-two.webp", result[1].imageUrl)
        assertEquals("572320", result[1].threadId)
    }

    @Test
    fun parseForumBanners_readsCurrentYamiSwiperStructure() {
        val result = ForumApiParser.parseForumBanners(
            """
            <div id="forum">
              <div class="index-top-wrapper">
                <div class="yami-swiper">
                  <div class="swiper-slide">
                    <a href="forum.php?mod=viewthread&amp;tid=573200">
                      <img src="/data/attachment/portal/current-banner.jpg">
                    </a>
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals(
            "https://bbs.yamibo.com/data/attachment/portal/current-banner.jpg",
            result.single().imageUrl
        )
        assertEquals("573200", result.single().threadId)
    }

    @Test
    fun parseForumBanners_readsSlideboxCarouselStructure() {
        val result = ForumApiParser.parseForumBanners(
            """
            <div id="forum">
              <div class="slidebox">
                <div class="swiper-wrapper">
                  <div class="swiper-slide">
                    <a href="forum.php?mod=viewthread&amp;tid=573201">
                      <img src="/data/attachment/portal/slidebox-banner.jpg">
                    </a>
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals(
            "https://bbs.yamibo.com/data/attachment/portal/slidebox-banner.jpg",
            result.single().imageUrl
        )
        assertEquals("573201", result.single().threadId)
    }

    @Test
    fun parseForumHeadImage_readsForumHeadImage() {
        val result = ForumApiParser.parseForumHeadImage(
            """
            <div id="forum">
              <div class="forum-headimg">
                <img src="//cdn.example.com/forum-head.webp">
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals("https://cdn.example.com/forum-head.webp", result)
    }

}

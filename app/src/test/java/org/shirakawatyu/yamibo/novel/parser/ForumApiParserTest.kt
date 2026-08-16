package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPost
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostActionForm
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostAuthor
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostBlock
import org.shirakawatyu.yamibo.novel.bean.forum.ForumPostTextPart

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
    fun parseFavoriteForums_keepsAccountOrderAndBoardCounts() {
        val result = ForumApiParser.parseFavoriteForums(
            """
            {
              "Variables": {
                "list": [
                  {"id":"30","title":"中文百合漫画区","threads":"20","posts":"80","todayposts":"19"},
                  {"id":"55","title":"轻小说/译文区","threads":"10","posts":"40","todayposts":"13"}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(listOf("30", "55"), result.map { it.id })
        assertEquals("中文百合漫画区", result.first().name)
        assertEquals(19, result.first().todayPostCount)
        assertEquals(13, result.last().todayPostCount)
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
                    "message": "<i class='pstatus'>本帖最后由 楼主 于 2026-08-12 09:30 编辑</i><p>Hello <a href='forum.php?mod=viewthread&amp;tid=572321'>下一主题</a><br><img src='/data/attachment/forum/example.jpg' alt='插图'></p>",
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
        assertEquals("2026-08-12 09:30", post.editedAt)
        val textBlock = post.blocks.filterIsInstance<ForumPostBlock.Text>().single()
        assertFalse(textBlock.parts.any { it.text.contains("本帖最后由") })
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
    fun parseForumPoll_readsVotedSingleChoiceResults() {
        val poll = requireNotNull(
            ForumApiParser.parseForumPoll(
                """
                <form id="poll">
                  <input type="hidden" name="formhash" value="baa2824d">
                  <div class="poll_txt">单选投票, 共有 1437 人参与投票</div>
                  <div class="poll_txt">距结束还有: 252 天 11 小时 11 分钟</div>
                  <div class="poll_box">
                    <p>
                      <label for="option_1">1.不能接受，这很不百合</label>
                      <em>30.97% (445票)</em>
                    </p>
                    <p>
                      <label for="option_2">2.可以接受，这不是雷点/文笔好就行</label>
                      <em>57.20% (822票)</em>
                    </p>
                    <p>
                      <label for="option_3">3.其他看法</label>
                      <em>11.83% (170票)</em>
                    </p>
                    <span class="xi1">您已经投过票，谢谢您的参与</span>
                  </div>
                </form>
                """.trimIndent()
            )
        )

        assertEquals("单选投票", poll.typeText)
        assertEquals(1437, poll.participantCount)
        assertEquals("距结束还有: 252 天 11 小时 11 分钟", poll.remainingText)
        assertEquals(3, poll.options.size)
        assertEquals(57.20f, poll.options[1].percent)
        assertEquals(822, poll.options[1].voteCount)
        assertEquals("您已经投过票，谢谢您的参与", poll.statusText)
    }

    @Test
    fun parseForumPoll_keepsOptionsBeforeVoting() {
        val poll = requireNotNull(
            ForumApiParser.parseForumPoll(
                """
                <form id="poll">
                  <div class="poll_txt">单选投票, 投票后结果可见, 共有 649 人参与投票</div>
                  <div class="poll_txt">距结束还有: 165 天 10 小时 52 分钟</div>
                  <div class="poll_box">
                    <p><input type="radio" name="pollanswers[]" value="33166"><label>1.断更</label></p>
                    <p><input type="radio" name="pollanswers[]" value="33167"><label>2.烂尾</label></p>
                  </div>
                </form>
                """.trimIndent()
            )
        )

        assertEquals(2, poll.options.size)
        assertEquals("1.断更", poll.options[0].text)
        assertEquals(null, poll.options[0].percent)
        assertEquals(null, poll.options[0].voteCount)
    }

    @Test
    fun parseViewRatings_readsDesktopAndAjaxMobileTimes() {
        val desktop = ForumApiParser.parseViewRatings(
            """
            <table class="list">
              <tr><td>积分</td><td>用户名</td><td>时间</td><td>理由</td></tr>
              <tr><td>积分 +1 点</td><td><a href="space-uid-7.html">甲</a></td><td>2026-8-10 00:41</td><td></td></tr>
            </table>
            """.trimIndent()
        )
        assertEquals(listOf("甲" to "2026-8-10 00:41"), desktop)

        val mobile = ForumApiParser.parseViewRatings(
            """
            <root><![CDATA[
              <li class="flex-box mli">
                <div class="flex-2 xs1 xg1">
                  <span class="z">积分 +2 点</span><span class="z">乙</span>
                </div>
                <div class="flex-3 xs1 xg1"><span class="y">2026-8-11 12:30</span></div>
              </li>
            ]]></root>
            """.trimIndent()
        )
        assertEquals(listOf("乙" to "2026-8-11 12:30"), mobile)
    }

    @Test
    fun parseAllRatings_readsFullDesktopTable() {
        val result = ForumApiParser.parseAllRatings(
            """
            <root><![CDATA[
              <table class="list">
                <tr><th>积分</th><th>用户名</th><th>时间</th><th>理由</th></tr>
                <tr><td>积分 +1 点</td><td><a href="space-uid-7.html">甲</a></td><td>2026-8-10 00:41</td><td>支持</td></tr>
                <tr><td>积分 -2 点</td><td><a href="space-uid-8.html">乙</a></td><td>2026-8-11 12:30</td><td></td></tr>
              </table>
            ]]></root>
            """.trimIndent()
        )
        assertEquals(2, result.size)
        assertEquals("甲", result[0].userName)
        assertEquals("+1", result[0].score)
        assertEquals("支持", result[0].reason)
        assertEquals("2026-8-10 00:41", result[0].createdAt)
        assertEquals("乙", result[1].userName)
        assertEquals("-2", result[1].score)
        assertEquals("2026-8-11 12:30", result[1].createdAt)
    }

    @Test
    fun parseAllRatings_readsMobileReasonWhenPresent() {
        val result = ForumApiParser.parseAllRatings(
            """
            <root><![CDATA[
              <ul class="post_box cl">
              <li class="flex-box mli">
                <div class="flex-2 xs1 xg1"><span class="z">积分</span></div>
                <div class="flex-2 xs1 xg1"><span class="z">用户名</span></div>
                <div class="flex-3 xs1 xg1"><span class="y">时间</span></div>
              </li><li class="flex-box mli">
                <div class="flex-2 xs1 xg1"><span class="z">积分 +10 点</span></div>
                <div class="flex-2 xs1 xg1"><span class="z">slovic</span></div>
                <div class="flex-3 xs1 xg1"><span class="y">2026-7-30 19:31</span></div>
              </li>
              <li class="flex-box mli">
                <div class="flex xs1 xg1"><span class="z">太强了！</span></div>
              </li>
              <li class="flex-box mli">
                <div class="flex-2 xs1 xg1"><span class="z">积分 +99 点</span></div>
                <div class="flex-2 xs1 xg1"><span class="z">筱林透</span></div>
                <div class="flex-3 xs1 xg1"><span class="y">2026-7-25 19:26</span></div>
              </li>
              <li class="flex-box mli">
                <div class="flex-2 xs1 xg1"><span class="z">积分 +5 点</span></div>
                <div class="flex-2 xs1 xg1"><span class="z">愿世异似了</span></div>
                <div class="flex-3 xs1 xg1"><span class="y">2026-7-7 16:31</span></div>
              </li>
              <li class="flex-box mli">
                <div class="flex xs1 xg1"><span class="z">精品文章</span></div>
              </li>
              </ul>
            ]]></root>
            """.trimIndent()
        )
        assertEquals(3, result.size)
        assertEquals("slovic", result[0].userName)
        assertEquals("+10", result[0].score)
        assertEquals("太强了！", result[0].reason)
        assertEquals("2026-7-30 19:31", result[0].createdAt)
        assertEquals("筱林透", result[1].userName)
        assertEquals("", result[1].reason)
        assertEquals("愿世异似了", result[2].userName)
        assertEquals("精品文章", result[2].reason)
    }

    @Test
    fun parseForumPostRatingSummaries_readsRealDesktopRatingLogWithAvatarAnchors() {
        val result = ForumApiParser.parseForumPostRatingSummaries(
            """
            <h3>评分</h3>
            <dl id="ratelog_41558951" class="rate">
              <table class="ratl">
                <tbody><tr>
                  <th class="xw1" width="120"><a href="forum.php?mod=misc&amp;action=viewratings&amp;tid=572320&amp;pid=41558951"> 参与人数 <span class="xi1">21</span></a></th><th class="xw1" width="80">积分 <i><span class="xi1">+279</span></i></th>
                  <th><a href="javascript:;">收起</a><i class="txt_h">理由</i></th>
                </tr></tbody>
                <tbody class="ratl_l">
                  <tr id="rate_41558951_407870">
                    <td>
                      <a href="space-uid-407870.html" target="_blank"><img src="avatar.jpg" class="user_avatar"></a> <a href="space-uid-407870.html" target="_blank">slovic</a>
                    </td><td class="xi1"> + 10</td>
                    <td class="xg1">太强了！</td>
                  </tr>
                  <tr id="rate_41558951_8">
                    <td>
                      <a href="space-uid-8.html" target="_blank"><img src="avatar2.jpg" class="user_avatar"></a> <a href="space-uid-8.html" target="_blank">筱林透</a>
                    </td><td class="xi1"> + 99</td>
                    <td class="xg1"></td>
                  </tr>
                </tbody>
              </table>
              <p class="ratc"><a href="forum.php?mod=misc&amp;action=viewratings&amp;tid=572320&amp;pid=41558951" class="xi2">查看全部评分</a></p>
            </dl>
            """.trimIndent()
        )

        val summary = result.getValue("41558951")
        assertEquals("参与人数 21", summary.participantText)
        assertEquals("积分 +279", summary.scoreText)
        assertEquals(2, summary.ratings.size)
        assertEquals("slovic", summary.ratings[0].userName)
        assertEquals("+ 10", summary.ratings[0].score)
        assertEquals("太强了！", summary.ratings[0].reason)
        assertEquals("筱林透", summary.ratings[1].userName)
        assertEquals("", summary.ratings[1].reason)
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
    fun parseForumPostRatingSummaries_readsRealMobileRatingLog2026() {
        val result = ForumApiParser.parseForumPostRatingSummaries(
            """
            <h3 class="psth xs1"><span class="icon_ring vm"></span>评分</h3>
            <div id="ratelog_40496908">
            <dd style="margin:0">
            <div id="post_rate_40496908"></div>
            <ul class="post_box cl">
            <li class="flex-box mli p0">
            <div class="flex-2 xs1 xg1 xw1"><span class="z"><a href="https://bbs.yamibo.com/forum.php?mod=misc&amp;action=viewratings&amp;tid=529212&amp;pid=40496908&amp;mobile=2" class="dialog" title="查看全部评分"> 参与人数 <span class="xi1">25</span></a></span></div><div class="flex-2 xs1 xg1 xw1">积分 <i><span class="xi1">+44</span></i></div>
            <div class="flex-3 xs1 xg1 xw1">理由</div>
            </li><li class="flex-box mli p0">
            <div class="flex-2 xs1 xg1"><span class="z"><a href="https://bbs.yamibo.com/home.php?mod=space&amp;uid=614340&amp;mobile=2" target="_blank">bucheon</a></span></div><div class="flex-2 xs1 xi1 xw1"> + 1</div>
            <div class="flex-3 xs1 xg1">感觉自己选的时候好草率</div>
            </li>
            <li class="flex-box mli p0">
            <div class="flex-2 xs1 xg1"><span class="z"><a href="https://bbs.yamibo.com/home.php?mod=space&amp;uid=695728&amp;mobile=2" target="_blank">ygren</a></span></div><div class="flex-2 xs1 xi1 xw1"> + 2</div>
            <div class="flex-3 xs1 xg1"></div>
            </li>
            <li class="flex-box mli p0"><div class="flex xs2 xg1 xw1"><a href="https://bbs.yamibo.com/forum.php?mod=misc&amp;action=viewratings&amp;tid=529212&amp;pid=40496908&amp;mobile=2" title="查看全部评分" class="dialog">查看全部评分</a></div></li>
            </ul>
            </dd>
            </div>
            """.trimIndent()
        )

        val summary = result.getValue("40496908")
        assertEquals("参与人数 25", summary.participantText)
        assertEquals("积分 +44", summary.scoreText)
        assertEquals(2, summary.ratings.size)
        assertEquals("bucheon", summary.ratings[0].userName)
        assertEquals("+ 1", summary.ratings[0].score)
        assertEquals("感觉自己选的时候好草率", summary.ratings[0].reason)
        assertEquals("ygren", summary.ratings[1].userName)
        assertEquals("", summary.ratings[1].reason)
    }

    @Test
    fun parseForumPostComments_readsRealMobileComment2026() {
        val html = """
            <div id="comment_40497020">
            <h3 class="psth xs1"><span class="icon_ring vm"></span>点评</h3>
            <div class="plc p0 cl" id="commentdetail_60644">
            <div class="avatar l0"><img src="avatar.jpg" class="user_avatar"></div>
            <div class="display pi">
            <ul class="authi">
            <li class="mtit">
            <span class="y"></span>
            <span class="z">
            <a href="https://bbs.yamibo.com/home.php?mod=space&amp;uid=284358&amp;mobile=2" class="xi2 xw1">改变如期而至</a>
            </span>
            </li>
            <li class="mtime">
            <em class="mgl"></em>
            发表于 2022-9-16 01:55</li>
            <li class="mtxt mt5">这人，纯度太低了...</li>
            </ul>
            </div>
            </div>
            </div>
        """.trimIndent()

        val result = ForumApiParser.parseComments(html, "40497020")
        assertEquals(1, result.size)
        val comment = result.single()
        assertEquals("60644", comment.id)
        assertEquals("改变如期而至", comment.authorName)
        assertEquals("284358", comment.authorUid)
        assertEquals("这人，纯度太低了...", comment.message)
    }

    @Test
    fun parseComments_readsDesktopCommentLog() {
        val html = """
            <div id="comment_41332619" class="cm">
              <div class="pstl xs1 cl">
                <div class="psta vm">
                  <a href="https://bbs.yamibo.com/space-uid-488072.html"><img src="https://bbs.yamibo.com/uc_server/avatar.php?uid=488072" class="user_avatar"></a>
                  <a href="https://bbs.yamibo.com/space-uid-488072.html" class="xi2 xw1">tushiting</a>
                </div>
                <div class="psti">
                  感觉都没区别
                  <span class="xg1">发表于 2025-12-26 10:17</span>
                </div>
              </div>
            </div>
            <div id="comment_41332620" class="cm"></div>
        """.trimIndent()

        val result = ForumApiParser.parseComments(html, "41332619")
        assertEquals(1, result.size)
        val comment = result.single()
        assertEquals("tushiting", comment.authorName)
        assertEquals("488072", comment.authorUid)
        assertEquals("感觉都没区别", comment.message)
        assertEquals("2025-12-26 10:17", comment.createdAt)
        assertEquals(true, comment.authorAvatarUrl!!.contains("avatar.php?uid=488072"))

        // 空容器不产生点评
        assertTrue(ForumApiParser.parseComments(html, "41332620").isEmpty())
    }

    @Test
    fun parseComments_readsMobileCommentDetail() {
        val html = """
            <div id="comment_415">
              <div id="commentdetail_9001">
                <div class="authi"><div class="z"><a href="home.php?mod=space&amp;uid=42">甲</a></div></div>
                <div class="mtxt">写得很棒</div>
                <div class="mtime">2026-8-10 09:00</div>
              </div>
            </div>
        """.trimIndent()

        val result = ForumApiParser.parseComments(html, "415")
        assertEquals(1, result.size)
        val comment = result.single()
        assertEquals("9001", comment.id)
        assertEquals("甲", comment.authorName)
        assertEquals("42", comment.authorUid)
        assertEquals("写得很棒", comment.message)
        assertEquals("2026-8-10 09:00", comment.createdAt)
    }

    @Test
    fun parseForumPostComments_mapsAllPosts() {
        val html = """
            <div id="comment_1" class="cm">
              <div class="pstl xs1 cl">
                <div class="psta vm"><a href="space-uid-7.html" class="xi2 xw1">甲</a></div>
                <div class="psti">第一条<span class="xg1">发表于 2026-1-1 08:00</span></div>
              </div>
            </div>
            <div id="comment_2" class="cm">
              <div id="commentdetail_33">
                <div class="authi"><div class="z"><a href="home.php?mod=space&amp;uid=8">乙</a></div></div>
                <div class="mtxt">第二条</div>
              </div>
            </div>
            <div id="comment_3" class="cm"></div>
        """.trimIndent()

        val result = ForumApiParser.parseForumPostComments(html)
        assertEquals(setOf("1", "2"), result.keys)
        assertEquals("甲", result.getValue("1").single().authorName)
        assertEquals("第一条", result.getValue("1").single().message)
        assertEquals("乙", result.getValue("2").single().authorName)
    }

    @Test
    fun parseAllPostActionForms_mapsRateAndCommentFormsByPostId() {
        val html = """
            <form id="rateform_101" method="post" action="forum.php?mod=misc&amp;action=rate&amp;tid=1&amp;pid=101">
              <input type="hidden" name="formhash" value="abc123">
              <input type="hidden" name="pid" value="101">
            </form>
            <form id="commentform_102" method="post" action="forum.php?mod=post&amp;action=reply&amp;comment=yes&amp;tid=1&amp;pid=102">
              <input type="hidden" name="formhash" value="def456">
              <input type="hidden" name="pid" value="102">
            </form>
        """.trimIndent()

        val result = ForumApiParser.parseAllPostActionForms(html)
        assertEquals(setOf("101", "102"), result.keys)
        assertEquals("abc123", result.getValue("101").first!!.formHash)
        assertEquals(ForumPostActionForm.Type.RATE, result.getValue("101").first!!.type)
        assertEquals("def456", result.getValue("102").second!!.formHash)
        assertEquals(ForumPostActionForm.Type.COMMENT, result.getValue("102").second!!.type)
    }

    @Test
    fun parseSendReplyResponse_detectsSuccessAndFailure() {
        val success = """
            {"Version":"4","Variables":{"tid":"561837","pid":"41523001"},
             "Message":{"messageval":"post_reply_succeed","messagestr":"非常感谢,回复发布成功"}}
        """.trimIndent()
        assertEquals(ForumReplyResult.Posted, ForumApiParser.parseSendReplyResponse(success))

        val moderation = """
            {"Version":"4","Variables":{"tid":"561837","pid":"0"},
             "Message":{"messageval":"post_reply_mod_succeed","messagestr":"回复发布成功，等待审核"}}
        """.trimIndent()
        assertEquals(
            ForumReplyResult.PendingModeration("回复发布成功，等待审核"),
            ForumApiParser.parseSendReplyResponse(moderation)
        )

        val failure = """
            {"Version":"4","Variables":{"tid":"561837","pid":"0"},
             "Message":{"messageval":"post_sm_isnull","messagestr":"抱歉,您尚未输入标题或内容"}}
        """.trimIndent()
        assertEquals(
            ForumReplyResult.Failed("抱歉,您尚未输入标题或内容"),
            ForumApiParser.parseSendReplyResponse(failure)
        )
    }

    @Test
    fun parseFormHash_readsHashFromThreadHtml() {
        assertEquals(
            "abc123",
            ForumApiParser.parseFormHash(
                "<form id='postform'><input type='hidden' name='formhash' value='abc123'></form>"
            )
        )
    }

    @Test
    fun buildReplyMessageWithQuote_wrapsQuotedContentWithBbcode() {
        val quotePost = ForumPost(
            id = "41332699",
            threadId = "561837",
            author = ForumPostAuthor(id = "488072", name = "tushiting"),
            createdAt = "2026-8-1 12:00",
            floor = 2,
            isOriginalPost = false,
            blocks = listOf(
                ForumPostBlock.Text(listOf(ForumPostTextPart("第一段"))),
                ForumPostBlock.Image(url = "https://example.com/a.jpg"),
                ForumPostBlock.Text(listOf(ForumPostTextPart("第二段")))
            )
        )

        val result = ForumApiParser.buildReplyMessageWithQuote(quotePost, "我的回复")
        assertTrue(result.startsWith("[quote][size=2][color=#999999][url=forum.php?mod=redirect&goto=findpost&pid=41332699]tushiting 发表于 2026-8-1 12:00[/url][/color][/size]"))
        assertTrue(result.contains("第一段\n第二段"))
        assertTrue(result.contains("[/quote]\n我的回复"))
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
    fun parseForumBanners_keepsAllSlidesAndTheirThreadLinks() {
        val result = ForumApiParser.parseForumBanners(
            """
            <body id="forum">
              <div class="index-top-wrapper">
                <div class="yami-swiper">
                  <div class="swiper-slide">
                    <a href="forum.php?mod=viewthread&amp;tid=573210">
                      <img src="/banner-one.jpg">
                    </a>
                  </div>
                  <div class="swiper-slide">
                    <a href="thread-573211-1-1.html">
                      <img src="/banner-two.jpg">
                    </a>
                  </div>
                  <div class="swiper-slide swiper-slide-duplicate">
                    <a href="forum.php?mod=viewthread&amp;tid=573210">
                      <img src="/banner-one.jpg">
                    </a>
                  </div>
                </div>
              </div>
            </body>
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals("573210", result[0].threadId)
        assertEquals("573211", result[1].threadId)
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

    @Test
    fun parseForumPageMetadata_readsTodayThemeAndRankFromMobileHeader() {
        val result = ForumApiParser.parseForumPageMetadata(
            """
            <div class="forumdisplay-top">
              <h2>中文百合漫画区</h2>
              <p>今日: <span>12</span> | 主题: <span>53777</span> | 排名: <span>2</span></p>
            </div>
            """.trimIndent()
        )

        assertEquals(12, result.todayPostCount)
        assertEquals(53777, result.threadCount)
        assertEquals(2, result.rank)
    }

}

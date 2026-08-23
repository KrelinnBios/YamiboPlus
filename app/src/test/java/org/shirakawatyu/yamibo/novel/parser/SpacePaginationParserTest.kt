package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind

class SpacePaginationParserTest {
    @Test
    fun desktopThreadRecognizesVoteIcon() {
        val html = """
            <html><body><div class="tl"><table><tbody>
              <tr>
                <td class="icn"><i class="fico-vote fic6 fc-n" alt="投票"></i></td>
                <th><a href="thread-543354-1-1.html">关于各平台头像的选择</a></th>
                <td><a href="forum-33-1.html">海域區</a></td>
                <td class="num"><a>142</a><em>2074</em></td>
                <td class="by"><em>2025-4-3 22:46</em></td>
              </tr>
            </tbody></table></div></body></html>
        """.trimIndent()

        val item = SpaceDesktopParser.parseListPage(SpacePageKind.USER_THREAD, html)
            .items.single() as SpaceListItem.UserThread

        assertTrue(item.isPoll)
        assertEquals("海域區", item.forumName)
    }

    @Test
    fun desktopBlogPageTwoCanReturnToCanonicalFirstPage() {
        val html = """
            <html><body>
              <div class="xld">
                <dl class="bbda">
                  <dt><a href="blog-456-789.html">好友日志</a></dt>
                  <dd>
                    <a href="home.php?mod=space&amp;uid=456">好友甲</a>
                    <span class="xg1">2026-08-21 08:00</span>
                    <span class="xg1">仅好友可见</span>
                    <a href="home.php?mod=space&amp;do=blog&amp;classid=9">争议</a>
                  </dd>
                  <dd id="blog_article_789">日志摘要</dd>
                </dl>
              </div>
              <div class="pg">
                <a href="home.php?mod=space&amp;do=blog&amp;view=we">1</a>
                <strong>2</strong>
                <a href="home.php?mod=space&amp;do=blog&amp;view=we&amp;page=3">3</a>
                <a class="nxt" href="home.php?mod=space&amp;do=blog&amp;view=we&amp;page=3">下一页</a>
              </div>
            </body></html>
        """.trimIndent()

        val page = SpaceDesktopParser.parseListPage(SpacePageKind.BLOG, html)
        val blog = page.items.single() as SpaceListItem.Blog

        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=space&do=blog&view=we",
            page.previousUrl
        )
        assertTrue(page.nextUrl.orEmpty().contains("page=3"))
        assertEquals("好友甲", blog.authorName)
        assertEquals("456", blog.authorUid)
        assertTrue(blog.authorAvatarUrl.orEmpty().contains("uid=456"))
        assertEquals("2026-08-21 08:00", blog.time)
        assertEquals("仅好友可见", blog.visibilityText)
        assertEquals("争议", blog.category)
    }

    @Test
    fun desktopFriendBlogSkipsEmptyAvatarLinkWhenReadingAuthorName() {
        val html = """
            <html><body>
              <div class="xld">
                <dl class="bbda">
                  <dd class="m avt">
                    <a href="space-uid-695001.html"><img src="avatar.jpg"></a>
                  </dd>
                  <dt><a href="blog-695001-117864.html">好友日志</a></dt>
                  <dd>
                    <a href="space-uid-695001.html">tmzqd</a>
                    <span class="xg1">2026-8-13 15:32</span>
                  </dd>
                  <dd id="blog_article_117864">日志摘要</dd>
                </dl>
              </div>
            </body></html>
        """.trimIndent()

        val blog = SpaceDesktopParser.parseListPage(SpacePageKind.BLOG, html)
            .items.single() as SpaceListItem.Blog

        assertEquals("tmzqd", blog.authorName)
        assertEquals("695001", blog.authorUid)
        assertTrue(blog.authorAvatarUrl.orEmpty().endsWith("avatar.jpg"))
    }

    @Test
    fun mobileBlogRecognizesPreviousLinkOnParentElement() {
        val html = """
            <html><body>
              <div class="threadlist"><ul>
                <li>
                  <a href="home.php?mod=space&amp;do=blog&amp;id=789">
                    <div class="threadlist_tit">好友日志</div>
                  </a>
                  <a class="mmc" href="home.php?mod=space&amp;uid=456">好友甲</a>
                  <span class="xg1">仅好友可见</span>
                </li>
              </ul></div>
              <div class="page">
                <span class="prev">
                  <a href="home.php?mod=space&amp;do=blog&amp;view=we" aria-label="上一页">‹</a>
                </span>
                <a class="nxt" href="home.php?mod=space&amp;do=blog&amp;view=we&amp;page=3">下一页</a>
              </div>
            </body></html>
        """.trimIndent()

        val page = SpaceMobileParser.parsePage(SpacePageKind.BLOG, html)
        val blog = page.items.single() as SpaceListItem.Blog

        assertTrue(page.previousUrl.orEmpty().contains("view=we"))
        assertTrue(page.nextUrl.orEmpty().contains("page=3"))
        assertEquals("好友甲", blog.authorName)
        assertEquals("456", blog.authorUid)
        assertEquals("仅好友可见", blog.visibilityText)
    }

    @Test
    fun desktopBlogDetailSeparatesVisibilityFromCategory() {
        val html = """
            <html><body>
              <div class="vw">
                <div class="h">
                  <h1 class="ph">议题归纳</h1>
                  <p class="xg2">
                    <span class="y">仅好友可见</span>
                    <span class="xg1">已有 5 次阅读</span>
                    <span class="xg1">2025-4-17 01:21</span>
                    <span class="xg1">
                      个人分类:
                      <a href="home.php?mod=space&amp;uid=615797&amp;do=blog&amp;classid=4549">争议</a>
                    </span>
                  </p>
                </div>
                <div id="blog_article"><p>正文</p></div>
              </div>
              <div id="pcd"><div class="hm">
                <a href="space-uid-615797.html"><img src="avatar.jpg"></a>
                <h2><a href="space-uid-615797.html">krelinnbios</a></h2>
              </div></div>
            </body></html>
        """.trimIndent()

        val detail = SpaceDesktopParser.parseBlogDetail(
            html,
            "https://bbs.yamibo.com/blog-615797-115691.html"
        )

        assertEquals("仅好友可见", detail.visibilityText)
        assertEquals("争议", detail.category)
        assertEquals("2025-4-17 01:21", detail.time)
    }

    @Test
    fun desktopBlogCommentKeepsQuotedReplyAndParagraphBreaks() {
        val html = """
            <html><body>
              <div class="vw">
                <div class="h"><h1 class="ph">日志标题</h1></div>
                <div id="blog_article"><p>正文</p></div>
              </div>
              <div id="pcd"><div class="hm">
                <a href="space-uid-615797.html"><img src="owner.jpg"></a>
                <h2><a href="space-uid-615797.html">krelinnbios</a></h2>
              </div></div>
              <div id="comment_ul">
                <dl id="comment_647310_li">
                  <dd class="m avt"><img src="commenter.jpg"></dd>
                  <dt>
                    <a href="space-uid-615797.html" id="author_647310">krelinnbios</a>
                    <span class="xg1">2026-7-28 19:29</span>
                  </dt>
                  <dd id="comment_647310">
                    <div class="quote"><blockquote><b>AlmeasqViolet</b>: 被回复的内容</blockquote></div>
                    第一段<br><br>第二段
                  </dd>
                </dl>
              </div>
            </body></html>
        """.trimIndent()

        val detail = SpaceDesktopParser.parseBlogDetail(
            html,
            "https://bbs.yamibo.com/blog-615797-117721.html"
        )
        val comment = detail.comments.single()

        assertEquals("AlmeasqViolet", comment.quotedAuthor)
        assertEquals("被回复的内容", comment.quotedContent)
        assertEquals("第一段\n\n第二段", comment.content)
    }

    @Test
    fun desktopReplyUsesCorrectColumnsAndExactPostLink() {
        val html = """
            <html><body>
              <a class="a" href="home.php?mod=space&amp;do=thread&amp;type=reply">回复</a>
              <div class="tl"><table><tbody>
                <tr class="bw0_all">
                  <td class="icn"><a href="forum.php?mod=viewthread&amp;tid=542496"><i class="fico-thread"></i></a></td>
                  <th><a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=542496&amp;pid=">真正的帖子标题</a></th>
                  <td><a class="xg1" href="forum-16-1.html">管理版</a></td>
                  <td class="num"><a class="xi2" href="thread-542496-1-1.html">4</a><em>5716</em></td>
                  <td class="by"><em>2026-8-18 02:35</em></td>
                </tr>
                <tr><td colspan="5" class="xg1"><a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=542496&amp;pid=41607148">我的回复内容</a></td></tr>
              </tbody></table></div>
            </body></html>
        """.trimIndent()

        val item = SpaceDesktopParser.parseListPage(SpacePageKind.USER_THREAD, html)
            .items.single() as SpaceListItem.UserThread

        assertEquals("542496", item.tid)
        assertEquals("真正的帖子标题", item.title)
        assertEquals("管理版", item.forumName)
        assertEquals("4", item.replyCount)
        assertEquals("5716", item.viewCount)
        assertEquals("2026-8-18 02:35", item.time)
        assertEquals("我的回复内容", item.replyExcerpt)
        assertEquals("回复", item.entryType)
        assertEquals("41607148", item.postId)
        assertTrue(item.url.contains("pid=41607148"))
    }

    @Test
    fun desktopPostCommentUsesCommentTextAndTitleExactPostLink() {
        val html = """
            <html><body>
              <a class="a" href="home.php?mod=space&amp;do=thread&amp;type=postcomment">点评</a>
              <div class="tl"><table><tbody>
                <tr class="bw0_all">
                  <td class="icn"><a href="forum.php?mod=viewthread&amp;tid=572320"><i class="fico-thread"></i></a></td>
                  <th><a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=572320&amp;pid=41609509">百合会论坛非官方 Android 阅读客户端</a></th>
                  <td><a class="xg1" href="forum-16-1.html">管理版</a></td>
                </tr>
                <tr><td class="icn"></td><td colspan="2" class="xg1">不清楚</td></tr>
              </tbody></table></div>
            </body></html>
        """.trimIndent()

        val item = SpaceDesktopParser.parseListPage(SpacePageKind.USER_THREAD, html)
            .items.single() as SpaceListItem.UserThread

        assertEquals("572320", item.tid)
        assertEquals("不清楚", item.replyExcerpt)
        assertEquals("点评", item.entryType)
        assertTrue(item.url.contains("pid=41609509"))
    }

    @Test
    fun postCommentTimeIsRecoveredFromExactPostPage() {
        val html = """
            <html><body>
              <div id="post_41609509">
                <div class="authi"><em id="authorposton41609509">发表于 2026-8-20 12:34</em></div>
                <div id="comment_41609509">
                  <div class="pstl">
                    <div class="psta"><a class="xw1">tester</a></div>
                    <div class="psti">不清楚 <span class="xg1">发表于 2026-8-21 09:08</span></div>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        assertEquals(
            "2026-8-21 09:08",
            SpaceDesktopParser.parseUserThreadTargetTime(
                html,
                postId = "41609509",
                excerpt = "不清楚"
            )
        )
    }
}

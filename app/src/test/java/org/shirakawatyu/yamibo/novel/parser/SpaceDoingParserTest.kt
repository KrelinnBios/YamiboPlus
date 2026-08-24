package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind

class SpaceDoingParserTest {
    @Test
    fun desktopDoingParsesAuthorContentTimeAndPagination() {
        val html = """
            <html><body>
              <div class="xld xlda">
                <dl id="eb622b1Odl166677" class="pbn bbda cl">
                  <dd class="m avt">
                    <a href="space-uid-632524.html"><img src="uc_server/avatar.php?uid=632524&amp;size=small"></a>
                  </dd>
                  <dd class="ptm xs2">
                    <a href="space-uid-632524.html">seccyzwvvk</a>:
                    <span>七夕好耶<img src="static/image/smiley/comcom/2.gif">幸福幸福幸福！</span>
                  </dd>
                  <dd class="cmt brm" id="eb622b1O_166677" style="display:none;"></dd>
                  <dd class="ptn xg1"><span class="y">2026-8-19 12:29</span><a href="javascript:;" onclick="docomment_form(166677, 0, 'eb622b1O');">回复</a></dd>
                </dl>
              </div>
              <div class="pgs"><div class="pg">
                <strong>1</strong>
                <a href="home.php?mod=space&amp;do=doing&amp;view=we&amp;page=2" class="nxt">下一页</a>
              </div></div>
            </body></html>
        """.trimIndent()

        val page = SpaceDesktopParser.parseListPage(SpacePageKind.DOING, html)
        val item = page.items.single() as SpaceListItem.Doing

        assertEquals("166677", item.doId)
        assertEquals("632524", item.uid)
        assertEquals("seccyzwvvk", item.name)
        assertEquals("七夕好耶 [表情] 幸福幸福幸福！", item.content)
        assertEquals(1, item.contentImages.size)
        assertEquals(item.content.indexOf("[表情]"), item.contentImages.single().offset)
        assertEquals(
            "https://bbs.yamibo.com/static/image/smiley/comcom/2.gif",
            item.contentImages.single().url
        )
        assertEquals("2026-8-19 12:29", item.time)
        assertEquals("https://bbs.yamibo.com/space-uid-632524.html", item.spaceUrl)
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=spacecp&ac=doing&op=docomment&handlekey=msg_0&doid=166677&id=0&key=eb622b1O&mobile=no",
            item.replyUrl
        )
        assertEquals("https://bbs.yamibo.com/home.php?mod=space&do=doing&view=we&page=2", page.nextUrl)
        assertNull(page.previousUrl)
    }

    @Test
    fun desktopDoingParsesCommentsWithoutMixingActionsIntoContent() {
        val html = """
            <div class="xld xlda">
              <dl id="MXo6oz4Zdl166637" class="pbn bbda cl">
                <dd class="m avt"><a href="space-uid-509957.html"><img src="avatar.jpg"></a></dd>
                <dd class="ptm xs2">
                  <a href="space-uid-509957.html">zhongmefeishi</a>:
                  <span>通宵给自己电脑做了个高度定制播放器</span>
                </dd>
                <dd class="cmt brm" id="MXo6oz4Z_166637">
                  <ul>
                    <li class="ptn pbn">
                      <a href="space-uid-615797.html" class="lit">krelinnbios</a>: 什么样的<img src="static/image/smiley/comcom/1.gif">
                      <span class="xg1">(8-11 01:12)</span>
                      <a href="javascript:;" onclick="docomment_form(166637, 283583, 'MXo6oz4Z');">回复</a>
                      <a href="home.php?mod=spacecp&amp;ac=doing&amp;op=delete&amp;doid=166637&amp;id=283583">删除</a>
                    </li>
                    <li class="ptn pbn dtls">
                      <a href="space-uid-509957.html" class="lit">zhongmefeishi</a>: 正在施工，目前有播放功能
                      <span class="xg1">(8-11 09:57)</span>
                      <a href="javascript:;" onclick="docomment_form(166637, 283584, 'MXo6oz4Z');">回复</a>
                    </li>
                  </ul>
                </dd>
                <dd class="ptn xg1"><span class="y">2026-8-10 20:40</span><a href="javascript:;">回复</a></dd>
              </dl>
            </div>
        """.trimIndent()

        val item = SpaceDesktopParser.parseListPage(SpacePageKind.DOING, html)
            .items.single() as SpaceListItem.Doing

        assertEquals("2026-8-10 20:40", item.time)
        assertEquals(2, item.comments.size)
        assertEquals("283583", item.comments[0].id)
        assertEquals("krelinnbios", item.comments[0].authorName)
        assertEquals("什么样的 [表情]", item.comments[0].content)
        assertEquals(
            item.comments[0].content.indexOf("[表情]"),
            item.comments[0].contentImages.single().offset
        )
        assertEquals(
            "https://bbs.yamibo.com/static/image/smiley/comcom/1.gif",
            item.comments[0].contentImages.single().url
        )
        assertEquals("8-11 01:12", item.comments[0].time)
        assertEquals(
            "https://bbs.yamibo.com/home.php?mod=spacecp&ac=doing&op=delete&doid=166637&id=283583",
            item.comments[0].deleteUrl
        )
        assertEquals("正在施工，目前有播放功能", item.comments[1].content)
    }
}

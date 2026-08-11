package org.shirakawatyu.yamibo.novel.util.forum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumBlacklistRemoteClientTest {
    @Test
    fun parsesDiscuzBlacklistPage() {
        val html = """
            <html>
            <script>var discuz_uid = '615797';</script>
            <form name="blackform">
              <input type="hidden" name="formhash" value="4328c6d7">
            </form>
            <div id="friend_ul">
              <ul class="buddy">
                <li id="friend_489445_li">
                  <h4><a href="https://bbs.yamibo.com/space-uid-489445.html">425</a></h4>
                </li>
              </ul>
            </div>
            </html>
        """.trimIndent()

        val snapshot = ForumBlacklistRemoteClient.parseSnapshot(html)

        assertEquals("4328c6d7", snapshot?.formHash)
        assertEquals("615797", snapshot?.currentUid)
        assertEquals(
            listOf(
                ForumBlockedItem(
                    type = ForumBlockedItem.TYPE_USER,
                    id = "489445",
                    title = "425",
                    authorUid = "489445",
                    authorName = "425"
                )
            ),
            snapshot?.users
        )
    }

    @Test
    fun ignoresBlacklistRemovalActionWhenReadingUsername() {
        val html = """
            <html>
            <script>var discuz_uid = '615797';</script>
            <form name="blackform">
              <input type="hidden" name="formhash" value="4328c6d7">
            </form>
            <ul id="friend_ul">
              <li id="friend_145770_li">
                <a href="home.php?mod=spacecp&ac=friend&op=blacklist&subop=delete&uid=145770">黑名单除名</a>
                <a href="home.php?mod=space&uid=145770"><img alt="面瘫行者"></a>
                <h4><a href="home.php?mod=space&uid=145770">面瘫行者</a></h4>
              </li>
            </ul>
            </html>
        """.trimIndent()

        assertEquals(
            ForumBlockedItem(
                type = ForumBlockedItem.TYPE_USER,
                id = "145770",
                title = "面瘫行者",
                authorUid = "145770",
                authorName = "面瘫行者"
            ),
            ForumBlacklistRemoteClient.parseSnapshot(html)?.users?.single()
        )
    }

    @Test
    fun fallsBackToUidWhenOnlyRemovalActionExists() {
        val html = """
            <html>
            <form name="blackform">
              <input type="hidden" name="formhash" value="4328c6d7">
            </form>
            <ul id="friend_ul">
              <li id="friend_145770_li">
                <a href="home.php?mod=spacecp&ac=friend&op=blacklist&subop=delete&uid=145770">黑名单除名</a>
              </li>
            </ul>
            </html>
        """.trimIndent()

        assertEquals(
            "UID 145770",
            ForumBlacklistRemoteClient.parseSnapshot(html)?.users?.single()?.authorName
        )
    }

    @Test
    fun rejectsLoginOrUnrelatedPage() {
        assertNull(ForumBlacklistRemoteClient.parseSnapshot("<html><form name=\"loginform\"></form></html>"))
    }

}

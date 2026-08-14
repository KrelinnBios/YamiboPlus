package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileApiParserTest {
    @Test
    fun parseProfile_distinguishesPrivateMessagesFromPrompts() {
        val profile = ProfileApiParser.parseProfile(
            """
            {
              "Variables": {
                "member_uid": "123",
                "member_username": "krelinnbios",
                "notice": {"newpm": "0", "newprompt": "2"}
              }
            }
            """.trimIndent()
        )

        assertEquals(true, profile.hasNewMessage)
        assertEquals(true, profile.hasNewPrompt)
    }

    @Test
    fun parseProfileHtml_readsForumProfileValues() {
        val profile = ProfileApiParser.parseProfileHtml(
            """
            <div class="avatar_bg"><div class="name">krelinnbios</div></div>
            <div class="avatar_m"><img src="/uc_server/avatar.php?uid=123&size=big" /></div>
            <ul class="user_box"><li>总积分 <span>1266点</span></li><li>积分 <span>422点</span></li><li>对象 <span>37点</span></li></ul>
            <ul class="myinfo_list"><li>UID <span>123</span></li><li>用户组 <span><font>百合花蕾</font></span></li></ul>
            <a href="home.php?mod=space&amp;do=pm"><span class="ico_msg"></span></a>
            <input name="formhash" value="abc123" />
            """.trimIndent()
        )

        assertEquals("123", profile.uid)
        assertEquals("krelinnbios", profile.username)
        assertEquals("百合花蕾", profile.groupTitle)
        assertEquals(422, profile.credits)
        assertEquals(1266, profile.totalCredits)
        assertEquals(37, profile.partner)
        assertEquals(true, profile.hasNewMessage)
        assertEquals("abc123", profile.formhash)
    }
}

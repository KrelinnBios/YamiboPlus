package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceLoginDetectionTest {
    @Test
    fun normalResponseWithLoginLinkAndFormHashIsNotTreatedAsLoggedOut() {
        val html = """
            <html><body>
              <a href="member.php?mod=logging&action=logout">退出</a>
              <input type="hidden" name="formhash" value="abc123">
              <div>回复发布成功</div>
            </body></html>
        """.trimIndent()

        assertFalse(SpaceMobileParser.isLoginRequired(html))
    }

    @Test
    fun actualLoginFormIsDetected() {
        val html = """
            <form id="loginform" action="member.php?mod=logging&action=login">
              <input name="username">
            </form>
        """.trimIndent()

        assertTrue(SpaceMobileParser.isLoginRequired(html))
    }
}

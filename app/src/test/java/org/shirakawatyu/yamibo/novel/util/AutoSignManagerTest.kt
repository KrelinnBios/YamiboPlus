package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSignManagerTest {
    @Test
    fun signActionUsesLiteCompatibleUrl() {
        assertEquals(
            "https://bbs.yamibo.com/plugin.php?id=zqlj_sign&sign=abc123",
            AutoSignManager.buildSignActionUrl("abc123")
        )
    }

    @Test
    fun signedPageIsParsedAsSigned() {
        val html = """
            <html><body>
              <div class="notice">今日已签到</div>
              <a href="plugin.php?id=zqlj_sign&sign=abc123&formhash=xyz">打卡</a>
            </body></html>
        """.trimIndent()

        val captured = parseCapturedSignPage(html)

        assertEquals(TodaySignStatus.SIGNED, captured.status)
    }

    @Test
    fun commonAlreadySignedTextIsParsedAsSigned() {
        val html = "<html><body><div>您今天已经签到</div></body></html>"

        val captured = parseCapturedSignPage(html)

        assertEquals(TodaySignStatus.SIGNED, captured.status)
    }

    @Test
    fun notSignedPageExtractsActionUrl() {
        val html = """
            <html><body>
              <a href="plugin.php?id=zqlj_sign&mobile=2&sign=abc123">立即签到</a>
            </body></html>
        """.trimIndent()

        val captured = parseCapturedSignPage(html)

        assertEquals(TodaySignStatus.NOT_SIGNED, captured.status)
        assertEquals("plugin.php?id=zqlj_sign&mobile=2&sign=abc123", captured.actionUrl)
    }

    @Test
    fun unrelatedPageYieldsUnknownWithoutActionUrl() {
        val captured = parseCapturedSignPage("<html><body>没有签到相关内容</body></html>")

        assertEquals(TodaySignStatus.UNKNOWN, captured.status)
        assertNull(captured.actionUrl)
    }
}

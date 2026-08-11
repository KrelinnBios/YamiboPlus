package org.shirakawatyu.yamibo.novel.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumRedirectCookieUtilTest {
    private val forumUrl = "https://bbs.yamibo.com/forum.php?mobile=no".toHttpUrl()

    @Test
    fun appliesTemplateCookieBeforeFollowingRedirect() {
        val merged = ForumRedirectCookieUtil.merge(
            currentHeader = "EeqY_2132_auth=secret; EeqY_2132_mobile=2",
            responseUrl = forumUrl,
            setCookieHeaders = listOf(
                "EeqY_2132_mobile=no; path=/; domain=.yamibo.com"
            )
        )

        assertTrue(merged.contains("EeqY_2132_auth=secret"))
        assertTrue(merged.contains("EeqY_2132_mobile=no"))
        assertFalse(merged.contains("EeqY_2132_mobile=2"))
    }

    @Test
    fun removesExpiredCookieFromRedirectChain() {
        val merged = ForumRedirectCookieUtil.merge(
            currentHeader = "mobile=2; sid=abc",
            responseUrl = forumUrl,
            setCookieHeaders = listOf(
                "mobile=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT; path=/"
            ),
            nowMillis = 1_000L
        )

        assertEquals("sid=abc", merged)
    }
}

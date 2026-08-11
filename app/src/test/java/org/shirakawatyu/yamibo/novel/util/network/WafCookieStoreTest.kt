package org.shirakawatyu.yamibo.novel.util.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WafCookieStoreTest {
    private val imageUrl = "https://bbs.yamibo.com/data/attachment/forum/image.jpg".toHttpUrl()

    @Test
    fun storesOnlyWafCookieAndKeepsLoginCookieWhenMerging() {
        val store = WafCookieStore()
        store.capture(
            imageUrl,
            listOf(
                "abymg_id=waf-cookie; Max-Age=86400; Domain=.yamibo.com; Path=/; Secure",
                "tracking=must-not-leak; Max-Age=86400; Domain=.yamibo.com; Path=/"
            )
        )

        val wafCookie = store.cookieHeaderFor(imageUrl)

        assertEquals("abymg_id=waf-cookie", wafCookie)
        assertEquals(
            "EeqY_2132_auth=auth-cookie; abymg_id=waf-cookie",
            WafCookieStore.mergeCookieHeader(
                "EeqY_2132_auth=auth-cookie; abymg_id=expired",
                wafCookie!!
            )
        )
    }

    @Test
    fun doesNotSendWafCookieToExternalHost() {
        val store = WafCookieStore()
        store.capture(
            imageUrl,
            listOf("abymg_id=waf-cookie; Max-Age=86400; Domain=.yamibo.com; Path=/")
        )

        assertNull(store.cookieHeaderFor("https://example.com/image.jpg".toHttpUrl()))
    }

    @Test
    fun ignoresUnrelatedResponseCookies() {
        val store = WafCookieStore()
        store.capture(
            imageUrl,
            listOf("EeqY_2132_auth=must-not-be-owned; Max-Age=86400; Domain=.yamibo.com; Path=/")
        )

        assertNull(store.cookieHeaderFor(imageUrl))
    }

    @Test
    fun removesExpiredWafCookie() {
        val store = WafCookieStore()
        store.capture(
            imageUrl,
            listOf("abymg_id=waf-cookie; Max-Age=86400; Domain=.yamibo.com; Path=/")
        )
        store.capture(
            imageUrl,
            listOf("abymg_id=deleted; Max-Age=0; Domain=.yamibo.com; Path=/")
        )

        assertNull(store.cookieHeaderFor(imageUrl))
    }
}

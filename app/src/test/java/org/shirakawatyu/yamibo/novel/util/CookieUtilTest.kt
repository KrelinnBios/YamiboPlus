package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieUtilTest {
    @Test
    fun persistentCookieHeader_dropsShortLivedWafCookies() {
        assertEquals(
            "EeqY_2132_auth=secret; EeqY_2132_sid=abc; mobile=2",
            CookieUtil.persistentCookieHeader(
                "EeqY_2132_auth=secret; nox_jst_v1=old; " +
                        "nox_extra=short; abymg_id=image; EeqY_2132_sid=abc; mobile=2"
            )
        )
    }
}

package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogStoreTest {
    @Test
    fun redactSensitiveValuesRemovesHeadersJsonAndQueryValues() {
        val input = """
            request https://bbs.yamibo.com/forum.php?auth=secret&tid=12&formhash=abc
            Cookie: EeqY_2132_auth=secret
            {"token":"secret-token"}
        """.trimIndent()

        val redacted = ErrorLogStore.redactSensitiveValues(input)

        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("abc"))
        assertTrue(redacted.contains("auth=<redacted>"))
        assertTrue(redacted.contains("formhash=<redacted>"))
        assertTrue(redacted.contains("Cookie: <redacted>"))
        assertTrue(redacted.contains("\"token\":\"<redacted>\""))
    }
}

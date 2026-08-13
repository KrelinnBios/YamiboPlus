package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaySignStatusCacheTest {
    @Test
    fun decodeReturnsStatusForSameDay() {
        val cached = TodaySignStatusCache.encode("2026-08-13", TodaySignStatus.SIGNED)

        assertEquals(
            TodaySignStatus.SIGNED,
            TodaySignStatusCache.decode(cached, "2026-08-13")
        )
    }

    @Test
    fun decodeRejectsExpiredOrUnknownStatus() {
        assertNull(
            TodaySignStatusCache.decode("2026-08-12:SIGNED", "2026-08-13")
        )
        assertNull(
            TodaySignStatusCache.decode("2026-08-13:UNKNOWN", "2026-08-13")
        )
    }

    @Test
    fun decodeRejectsMalformedValue() {
        assertNull(TodaySignStatusCache.decode("not-a-cache", "2026-08-13"))
        assertNull(TodaySignStatusCache.decode("2026-08-13:INVALID", "2026-08-13"))
    }
}

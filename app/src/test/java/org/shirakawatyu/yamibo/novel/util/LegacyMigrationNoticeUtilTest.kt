package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyMigrationNoticeUtilTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun markerIsAbsentUntilNoticeIsClosed() {
        val directory = temporaryFolder.newFolder("no_backup")

        assertFalse(LegacyMigrationNoticeUtil.hasShownIn(directory))
        assertTrue(LegacyMigrationNoticeUtil.markShownIn(directory))
        assertTrue(LegacyMigrationNoticeUtil.hasShownIn(directory))
    }

    @Test
    fun markingShownIsIdempotent() {
        val directory = temporaryFolder.newFolder("no_backup")

        assertTrue(LegacyMigrationNoticeUtil.markShownIn(directory))
        assertTrue(LegacyMigrationNoticeUtil.markShownIn(directory))
    }
}

package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUtilTest {
    @Test
    fun acceptsCurrentAndLegacyLiteBackupPackages() {
        assertTrue(
            BackupUtil.isSupportedBackupPackage(
                currentPackageName = "com.krelinnbios.yamiboplus",
                backupPackageName = "com.krelinnbios.yamiboplus"
            )
        )
        assertTrue(
            BackupUtil.isSupportedBackupPackage(
                currentPackageName = "com.krelinnbios.yamiboplus",
                backupPackageName = "com.krelinnbios.yamiboreaderlite"
            )
        )
    }

    @Test
    fun rejectsUnknownOrMissingBackupPackage() {
        assertFalse(
            BackupUtil.isSupportedBackupPackage(
                currentPackageName = "com.krelinnbios.yamiboplus",
                backupPackageName = "example.unrelated.app"
            )
        )
        assertFalse(
            BackupUtil.isSupportedBackupPackage(
                currentPackageName = "com.krelinnbios.yamiboplus",
                backupPackageName = null
            )
        )
    }
}

package org.shirakawatyu.yamibo.novel.util.updateCheck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckEngineTest {
    @Test
    fun manualNoUpdateAcknowledgesExistingIndicator() {
        assertFalse(
            shouldKeepUnreadUpdate(
                hadUnreadUpdate = true,
                detectedUpdate = false,
                acknowledgeExistingUpdate = true
            )
        )
    }

    @Test
    fun manualDetectedUpdateKeepsIndicator() {
        assertTrue(
            shouldKeepUnreadUpdate(
                hadUnreadUpdate = true,
                detectedUpdate = true,
                acknowledgeExistingUpdate = true
            )
        )
    }

    @Test
    fun backgroundNoUpdatePreservesExistingIndicator() {
        assertTrue(
            shouldKeepUnreadUpdate(
                hadUnreadUpdate = true,
                detectedUpdate = false,
                acknowledgeExistingUpdate = false
            )
        )
    }
}

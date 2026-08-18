package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Waf405RecoveryPolicyTest {
    @Test
    fun mainFrameGet405TriggersVisibleRecovery() {
        assertTrue(Waf405RecoveryPolicy.shouldRecover(405, "GET", true, true))
        assertFalse(
            Waf405RecoveryPolicy.shouldRecover(
                405,
                "GET",
                true,
                true,
                isSignPage = true
            )
        )
    }

    @Test
    fun postAndSubresource405AreNotAutomaticallyReplayed() {
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "POST", true, true))
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "GET", false, true))
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "GET", true, false))
    }

    @Test
    fun refreshForResponseOnlyMatchesGet405() {
        assertTrue(Waf405RecoveryPolicy.shouldRefreshForResponse(405, "GET"))
        assertFalse(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                405,
                "GET",
                isSignPage = true
            )
        )
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(403, "GET"))
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(405, "POST"))
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(444, "GET"))
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(200, "GET"))
    }

    @Test
    fun samePageCanOnlyBeAutomaticallyRetriedOnceWithinGuardWindow() {
        assertTrue(
            Waf405RecoveryPolicy.isSamePageRetryGuarded(
                previousUrl = "https://bbs.yamibo.com/thread-1-1-1.html",
                previousAttemptMs = 100_000L,
                url = "https://bbs.yamibo.com/thread-1-1-1.html",
                nowMs = 129_999L
            )
        )
        assertFalse(
            Waf405RecoveryPolicy.isSamePageRetryGuarded(
                previousUrl = "https://bbs.yamibo.com/thread-1-1-1.html",
                previousAttemptMs = 100_000L,
                url = "https://bbs.yamibo.com/thread-1-1-1.html",
                nowMs = 130_001L
            )
        )
    }

    @Test
    fun failedChallengeCoolingDownBlocksOnlyWithinWindow() {
        assertTrue(Waf405RecoveryPolicy.isFailedChallengeCoolingDown(100_000L, 129_999L))
        assertFalse(Waf405RecoveryPolicy.isFailedChallengeCoolingDown(100_000L, 130_001L))
        assertFalse(Waf405RecoveryPolicy.isFailedChallengeCoolingDown(0L, 100_000L))
    }

    @Test
    fun signPageUrlsAreRecognized() {
        assertTrue(
            Waf405RecoveryPolicy.isSignPageUrl(
                "https://bbs.yamibo.com/plugin.php?id=zqlj_sign&mobile=2&sign=abc"
            )
        )
        assertTrue(Waf405RecoveryPolicy.isSignPageUrl("https://bbs.yamibo.com/plugin.php?id=zqlj_sign"))
        assertFalse(
            Waf405RecoveryPolicy.isSignPageUrl("https://bbs.yamibo.com/forum.php?mod=forumdisplay")
        )
    }
}

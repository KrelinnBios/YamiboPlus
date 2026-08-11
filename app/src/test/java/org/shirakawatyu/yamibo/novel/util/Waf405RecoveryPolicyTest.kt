package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Waf405RecoveryPolicyTest {
    @Test
    fun mainFrameGet405TriggersVisibleRecovery() {
        assertTrue(Waf405RecoveryPolicy.shouldRecover(405, "GET", true, true))
    }

    @Test
    fun postAndSubresource405AreNotAutomaticallyReplayed() {
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "POST", true, true))
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "GET", false, true))
        assertFalse(Waf405RecoveryPolicy.shouldRecover(405, "GET", true, false))
    }

    @Test
    fun nativeGetRecoveryOnlyReplaysTheKnown405Get() {
        assertTrue(Waf405RecoveryPolicy.shouldRefreshForResponse(405, "GET"))
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(444, "GET"))
        assertFalse(Waf405RecoveryPolicy.shouldRefreshForResponse(405, "POST"))
    }

    @Test
    fun challengeDoesNotForceForumTemplateCookie() {
        assertFalse(Waf405RecoveryPolicy.CHALLENGE_URL.contains("mobile="))
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
}

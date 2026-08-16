package org.shirakawatyu.yamibo.novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun nativeRecoveryOnlyAcceptsKnownNoxChallengeResponses() {
        assertTrue(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                405,
                "GET",
                "<script>window.__noxExpire = 1; fetch('/NOX_CHECK')</script>"
            )
        )
        assertTrue(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                405,
                "GET",
                "const worker = 'gangplank_ab12'"
            )
        )
        assertFalse(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                405,
                "GET",
                "Method Not Allowed"
            )
        )
        assertFalse(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                444,
                "GET",
                "__noxExpire"
            )
        )
        assertFalse(
            Waf405RecoveryPolicy.shouldRefreshForResponse(
                405,
                "POST",
                "__noxExpire"
            )
        )
    }

    @Test
    fun probeFailureAfterChallengeInvalidatesNoxAndRecordsRetryableError() {
        val action = Waf405RecoveryPolicy.postRefreshAction(probeSucceeded = false)

        assertEquals(
            Waf405RecoveryPolicy.PostRefreshAction.INVALIDATE_AFTER_PROBE_FAILURE,
            action
        )
        assertTrue(action.shouldInvalidateNox)
        assertEquals("WAF 探测未通过，等待下次挑战", action.errorLog)
    }

    @Test
    fun replayedNoxChallengeInvalidatesNoxAndRecordsClearCredentialError() {
        val action = Waf405RecoveryPolicy.postRefreshAction(
            probeSucceeded = true,
            replayStatusCode = 405,
            replayBodyPreview = "<script>window.__noxExpire = 1</script>"
        )

        assertEquals(
            Waf405RecoveryPolicy.PostRefreshAction.INVALIDATE_AFTER_REPLAY_CHALLENGE,
            action
        )
        assertTrue(action.shouldInvalidateNox)
        assertEquals("WAF 重放仍被拦截，已清凭证", action.errorLog)
    }

    @Test
    fun acceptedReplayDoesNotInvalidateNoxOrRecordError() {
        val action = Waf405RecoveryPolicy.postRefreshAction(
            probeSucceeded = true,
            replayStatusCode = 200
        )

        assertEquals(Waf405RecoveryPolicy.PostRefreshAction.CONTINUE, action)
        assertFalse(action.shouldInvalidateNox)
        assertNull(action.errorLog)
    }

    @Test
    fun staleNoxCookieIsRemovedWithoutDroppingLoginCookies() {
        assertEquals(
            "auth=token; sid=session",
            Waf405RecoveryPolicy.withoutNoxCookie(
                "auth=token; nox_jst_v1=stale; sid=session"
            )
        )
    }

    @Test
    fun freshNoxCookieCanBeDetected() {
        assertEquals(
            "fresh",
            Waf405RecoveryPolicy.extractNoxCookieValue(
                "auth=token; NOX_JST_V1=fresh; sid=session"
            )
        )
        assertNull(Waf405RecoveryPolicy.extractNoxCookieValue("auth=token; sid=session"))
        assertNull(Waf405RecoveryPolicy.extractNoxCookieValue("nox_jst_v1="))
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
}

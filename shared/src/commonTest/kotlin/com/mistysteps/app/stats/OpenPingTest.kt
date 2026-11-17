package com.mistysteps.app.stats

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

class OpenPingTest {
    private val gap = OpenPing.MIN_GAP_MS

    @Test
    fun notOptedInNeverPings() {
        assertFalse(OpenPing.shouldPing(optedIn = false, release = true, lastPingAt = 0, now = 10 * gap))
    }

    @Test
    fun debugBuildsNeverPing() {
        assertFalse(OpenPing.shouldPing(optedIn = true, release = false, lastPingAt = 0, now = 10 * gap))
    }

    @Test
    fun theFirstPingAfterOptingInIsSent() {
        assertTrue(OpenPing.shouldPing(optedIn = true, release = true, lastPingAt = 0, now = 1_000_000_000_000))
    }

    @Test
    fun aSecondStartWithinThirtyMinutesIsNotSent() {
        val last = 1_000_000_000_000
        assertFalse(OpenPing.shouldPing(true, true, last, last + gap - 60_000))
    }

    @Test
    fun aStartAfterExactlyThirtyMinutesIsSent() {
        val last = 1_000_000_000_000
        assertTrue(OpenPing.shouldPing(true, true, last, last + gap))
    }

    @Test
    fun theClockGoingBackwardsDoesNotSendTwice() {
        val last = 1_000_000_000_000
        assertFalse(OpenPing.shouldPing(true, true, last, last - 5_000))
    }

    @Test
    fun payloadIsExactlyVersionAndPlatform() {
        assertEquals("""{"v":"1.4.0","os":"android"}""", OpenPing.payload("1.4.0"))
        assertEquals("""{"v":"1.4.0-beta","os":"android"}""", OpenPing.payload("1.4.0-beta"))
    }

    @Test
    fun aVersionTheServerWouldRejectIsNotSent() {
        assertFailsWith<IllegalArgumentException> { OpenPing.payload("1.4") }
        assertFailsWith<IllegalArgumentException> { OpenPing.payload("1.4.0\"}") }
        assertFailsWith<IllegalArgumentException> { OpenPing.payload("1.4.0-dev1") }
    }

    @Test
    fun devSuffixFromTheDebugBuildIsAccepted() {
        assertEquals("""{"v":"1.3.2-dev","os":"android"}""", OpenPing.payload("1.3.2-dev"))
    }
}

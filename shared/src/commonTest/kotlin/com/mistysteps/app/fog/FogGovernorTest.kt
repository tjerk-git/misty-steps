package com.mistysteps.app.fog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FogGovernorTest {
    private var now = 1_000_000L

    /** Feeds [n] frames of [ms] each, 16 ms apart on the clock; returns whether the level changed along the way. */
    private fun FogGovernor.feed(n: Int, ms: Double, stepMs: Long = 16): Boolean {
        var changed = false
        repeat(n) { now += stepMs; if (onFrame(ms, now)) changed = true }
        return changed
    }

    /** Feeds slow frames until the level changes (like a phone that gets a cheaper mist and speeds up). */
    private fun FogGovernor.slowUntilStepDown(ms: Double = 60.0, stepMs: Long = 1000) {
        var guard = 0
        while (guard++ < 1000) { now += stepMs; if (onFrame(ms, now)) return }
        error("level never changed")
    }

    @Test
    fun startsFullAndStaysThereWhileFramesAreFast() {
        val g = FogGovernor()
        assertFalse(g.feed(300, 8.0))
        assertEquals(FogLevel.FULL, g.level)
    }

    @Test
    fun slowFramesStepDownOneLevelAtATime() {
        val g = FogGovernor()
        g.slowUntilStepDown()
        assertEquals(FogLevel.LITE, g.level)
        // The next level is judged on its own frames and after a cooldown, not straight away.
        g.slowUntilStepDown()
        assertEquals(FogLevel.MINIMAL, g.level)
        g.feed(300, 60.0, stepMs = 1000)
        assertEquals(FogLevel.MINIMAL, g.level)
    }

    @Test
    fun aFewSlowFramesAreNotEnough() {
        val g = FogGovernor()
        repeat(10) { g.feed(1, 120.0); g.feed(5, 8.0) } // spikes, but the typical frame is fast
        assertEquals(FogLevel.FULL, g.level)
    }

    @Test
    fun doesNotStepDownTwiceInsideTheCooldown() {
        val g = FogGovernor()
        g.feed(30, 60.0, stepMs = 10)      // 300 ms of clock for 30 frames
        assertEquals(FogLevel.LITE, g.level)
        g.feed(30, 60.0, stepMs = 10)      // still inside the 10 s cooldown
        assertEquals(FogLevel.LITE, g.level)
    }

    @Test
    fun recoversOnlyAfterALongCalmStretch() {
        val g = FogGovernor()
        g.slowUntilStepDown()
        assertEquals(FogLevel.LITE, g.level)
        g.feed(300, 8.0, stepMs = 100)     // 30 s of fast frames: too soon
        assertEquals(FogLevel.LITE, g.level)
        g.feed(2000, 8.0, stepMs = 100)    // well over two minutes
        assertEquals(FogLevel.FULL, g.level)
    }

    @Test
    fun slowAgainAfterRecoveryMakesTheNextRecoveryWaitLonger() {
        val g = FogGovernor()
        g.slowUntilStepDown()
        g.feed(2000, 8.0, stepMs = 100)
        assertEquals(FogLevel.FULL, g.level)
        g.slowUntilStepDown()              // slow again right away
        assertEquals(FogLevel.LITE, g.level)
        // The base wait (2 min) is no longer enough: it has doubled to 4 min.
        g.feed(1500, 8.0, stepMs = 100)    // 2.5 min
        assertEquals(FogLevel.LITE, g.level)
        g.feed(2500, 8.0, stepMs = 100)    // another 4+ min
        assertEquals(FogLevel.FULL, g.level)
    }

    @Test
    fun batterySaverStartsAtLiteAndNeverGoesBackToFull() {
        val g = FogGovernor()
        assertTrue(g.setPowerSave(true, now))
        assertEquals(FogLevel.LITE, g.level)
        g.feed(5000, 8.0, stepMs = 100)    // lots of calm
        assertEquals(FogLevel.LITE, g.level)
    }

    @Test
    fun leavingBatterySaverAllowsFullAgain() {
        val g = FogGovernor()
        g.setPowerSave(true, now)
        g.setPowerSave(false, now)
        g.feed(2000, 8.0, stepMs = 100)
        assertEquals(FogLevel.FULL, g.level)
    }

    @Test
    fun batterySaverStillStepsDownToMinimalOnSlowFrames() {
        val g = FogGovernor()
        g.setPowerSave(true, now)
        g.slowUntilStepDown()
        assertEquals(FogLevel.MINIMAL, g.level)
    }
}

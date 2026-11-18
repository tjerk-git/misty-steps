package com.mistysteps.app.record

import com.mistysteps.app.data.FogConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalkRecorderTest {
    private val lat0 = 53.2012
    private val lon0 = 5.7936

    /** A fix [metersEast] metres east of the start, [seconds] after it. */
    private fun fix(metersEast: Number, seconds: Long = 0, accuracy: Float? = 5f) =
        RecordedFix(lat0, lon0 + metersEast.toDouble() / 67_000.0, accuracy, 1_000_000 + seconds * 1000, seconds * 1000)

    @Test fun poorFixesAreNotAccepted() {
        val r = WalkRecorder()
        assertTrue(r.accepts(fix(0, accuracy = 35f)))
        assertTrue(r.accepts(fix(0, accuracy = null)))
        assertFalse(r.accepts(fix(0, accuracy = 35.1f)))
    }

    @Test fun firstFixStartsALineWithTheBigClearing() {
        val r = WalkRecorder()
        r.begin()
        val step = assertNotNull(r.onFix(fix(0), 7L))
        assertTrue(step.newSegment)
        assertEquals(1, step.points.size)
        assertEquals(FogConstants.START_CLEAR_RADIUS_M, step.points[0].radius)
        assertEquals(7L, step.points[0].sessionId)
        assertEquals(0.0, step.distanceM)
    }

    @Test fun smallStepsAreIgnored() {
        val r = WalkRecorder()
        r.onFix(fix(0), 1L)
        assertNull(r.onFix(fix(8, 6), 1L))
        // Still measured from the first fix, so 12 m away counts.
        assertNotNull(r.onFix(fix(12, 12), 1L))
    }

    @Test fun gapsAreFilledEveryTwentyMetres() {
        val r = WalkRecorder()
        r.onFix(fix(0), 1L)
        val step = assertNotNull(r.onFix(fix(100, 70), 1L))
        // 100 m / 20 m = 5 steps: 4 filled spots plus the fix itself.
        assertEquals(5, step.points.size)
        assertFalse(step.newSegment)
        assertEquals(100.0, step.distanceM, 1.0)
        assertTrue(step.points.zipWithNext().all { (a, b) -> a.lon < b.lon })
    }

    @Test fun aJumpIsNotFilledOrJoined() {
        val r = WalkRecorder()
        r.onFix(fix(0), 1L)
        val step = assertNotNull(r.onFix(fix(400, 300), 1L))
        assertEquals(1, step.points.size)
        assertTrue(step.newSegment)
        assertEquals(0.0, step.distanceM)
    }

    @Test fun ridingAVehicleClearsNothingAndRestartsTheLine() {
        val r = WalkRecorder()
        r.onFix(fix(0), 1L)
        // 15 m/s (54 km/h) for a minute: well above the vehicle limit once the window has filled.
        var stepAt = 0
        var inVehicle = false
        for (i in 1..12) {
            val s = r.onFix(fix(15.0 * 5 * i, 5L * i), 1L)
            if (r.inVehicle) { inVehicle = true; stepAt = i; assertNull(s); break }
        }
        assertTrue(inVehicle)
        assertTrue(stepAt > 2)
        // Back to walking pace: clearing resumes, as a new line, with the normal radius.
        var resumed = false
        var t = 70L
        var x = 15.0 * 5 * 12
        for (i in 1..30) {
            t += 5; x += 7
            val s = r.onFix(fix(x, t), 1L)
            if (s != null) {
                assertTrue(s.newSegment)
                assertEquals(FogConstants.CLEAR_RADIUS_M, s.points[0].radius)
                resumed = true
                break
            }
        }
        assertTrue(resumed)
        assertFalse(r.inVehicle)
    }

    @Test fun withoutAWalkThereIsNoVehicleCheck() {
        val r = WalkRecorder()
        r.onFix(fix(0), null)
        for (i in 1..12) r.onFix(fix(75.0 * i, 5L * i), null)
        assertFalse(r.inVehicle)
    }

    @Test fun beginForgetsTheLastFix() {
        val r = WalkRecorder()
        r.onFix(fix(0), 1L)
        r.end()
        r.begin()
        val step = assertNotNull(r.onFix(fix(5, 10), 2L))
        assertTrue(step.newSegment)
        assertEquals(FogConstants.START_CLEAR_RADIUS_M, step.points[0].radius)
    }
}

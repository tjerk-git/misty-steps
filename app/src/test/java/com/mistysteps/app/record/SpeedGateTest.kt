package com.mistysteps.app.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGateTest {
    private val mPerDegLat = 111_195.0

    /** Feeds [seconds] of fixes every 2 s heading north at [kmh], starting at [fromM] metres. Returns where it ended. */
    private fun SpeedGate.go(kmh: Double, seconds: Int, startS: Int, fromM: Double): Double {
        var m = fromM
        for (s in 0 until seconds step 2) {
            update((startS + s) * 1000L, 53.2 + m / mPerDegLat, 5.8)
            m += kmh / 3.6 * 2
        }
        return m
    }

    @Test
    fun ownPowerKeepsClearing() {
        val g = SpeedGate()
        var m = g.go(5.0, 60, 0, 0.0)          // walking
        assertFalse(g.inVehicle)
        assertEquals(5 / 3.6, g.speed!!, 0.1)
        m = g.go(11.0, 60, 60, m)               // jogging
        assertFalse(g.inVehicle)
        m = g.go(22.0, 60, 120, m)              // cycling
        assertFalse(g.inVehicle)
        g.go(28.0, 60, 180, m)                  // fast e-bike
        assertFalse(g.inVehicle)
    }

    @Test
    fun aCarPausesClearingUntilYouSlowDown() {
        val g = SpeedGate()
        var m = g.go(50.0, 40, 0, 0.0)
        assertTrue(g.inVehicle)
        // Slowing to 28 km/h for a bend: still above the 25 km/h threshold, stay paused.
        m = g.go(28.0, 40, 40, m)
        assertTrue(g.inVehicle)
        // Out of the car and walking.
        g.go(4.5, 40, 80, m)
        assertFalse(g.inVehicle)
    }

    @Test
    fun needsFifteenSecondsBeforeDeciding() {
        val g = SpeedGate()
        g.go(80.0, 10, 0, 0.0)
        assertNull(g.speed)
        assertFalse(g.inVehicle)
    }
}

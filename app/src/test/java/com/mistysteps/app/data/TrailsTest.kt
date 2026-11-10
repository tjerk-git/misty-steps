package com.mistysteps.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailsTest {

    private fun p(lat: Double, lon: Double, session: Long?) = TrailPoint(lat, lon, 0L, sessionId = session)

    @Test
    fun splitsOnNewWalkAndOnBigJumps() {
        val t = Trails()
        // Walk 1: three spots ~20 m apart.
        t.add(p(52.3700, 4.8800, 1))
        t.add(p(52.3702, 4.8800, 1))
        t.add(p(52.3704, 4.8800, 1))
        // Same walk, but GPS jumped ~1.1 km: new segment.
        t.add(p(52.3804, 4.8800, 1))
        t.add(p(52.3806, 4.8800, 1))
        // Walk 2 starts right next to the end of walk 1: still a new segment.
        t.add(p(52.3807, 4.8800, 2))
        // Explicit split.
        t.add(p(52.3808, 4.8800, 2), newSegment = true)

        assertEquals(listOf(3, 2, 1, 1), t.segments.map { it.size })
        assertEquals(listOf<Long?>(1, 1, 2, 2), t.segments.map { it.sessionId })
    }

    @Test
    fun filledGapsStayInOneSegment() {
        // FogStore fills gaps up to 300 m; those must never split the line.
        assertEquals(300.0, Trails.approxDistanceM(52.37, 4.88, 52.37 + 300.0 / 111_195.0, 4.88), 1.0)
        val t = Trails()
        t.add(p(52.37, 4.88, 1))
        t.add(p(52.37 + 300.0 / 111_195.0, 4.88, 1))
        assertEquals(1, t.segments.size)
    }

    @Test
    fun distanceWrapsAroundTheDateLine() {
        assertEquals(111_195.0 * 0.2, Trails.approxDistanceM(0.0, 179.9, 0.0, -179.9), 50.0)
    }
}

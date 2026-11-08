package com.mistysteps.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailsTest {

    private fun p(lat: Double, lon: Double, session: Long?) = TrailPoint(lat, lon, 0L, sessionId = session)

    @Test
    fun splitsOnNewWalkAndOnBigJumps() {
        val t = Trails()
        // Walk 1: three spots ~20 m apart.
        t.add(p(53.2000, 5.8000, 1))
        t.add(p(53.2002, 5.8000, 1))
        t.add(p(53.2004, 5.8000, 1))
        // Same walk, but GPS jumped ~1.1 km: new segment.
        t.add(p(53.2104, 5.8000, 1))
        t.add(p(53.2106, 5.8000, 1))
        // Walk 2 starts right next to the end of walk 1: still a new segment.
        t.add(p(53.2107, 5.8000, 2))
        // Explicit split.
        t.add(p(53.2108, 5.8000, 2), newSegment = true)

        assertEquals(listOf(3, 2, 1, 1), t.segments.map { it.size })
        assertEquals(listOf<Long?>(1, 1, 2, 2), t.segments.map { it.sessionId })
    }

    @Test
    fun filledGapsStayInOneSegment() {
        // FogStore fills gaps up to 300 m; those must never split the line.
        assertEquals(300.0, Trails.approxDistanceM(53.2, 5.8, 53.2 + 300.0 / 111_195.0, 5.8), 1.0)
        val t = Trails()
        t.add(p(53.2, 5.8, 1))
        t.add(p(53.2 + 300.0 / 111_195.0, 5.8, 1))
        assertEquals(1, t.segments.size)
    }

    @Test
    fun distanceWrapsAroundTheDateLine() {
        assertEquals(111_195.0 * 0.2, Trails.approxDistanceM(0.0, 179.9, 0.0, -179.9), 50.0)
    }
}

package com.mistysteps.app.map

import com.mistysteps.app.data.Mercator
import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.data.Trails
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailGeoJsonTest {
    private fun trails(vararg walks: Pair<Long, List<Pair<Double, Double>>>): Trails {
        val t = Trails()
        var time = 0L
        for ((session, points) in walks) {
            for ((i, p) in points.withIndex()) t.add(TrailPoint(p.first, p.second, time++, sessionId = session), newSegment = i == 0)
        }
        return t
    }

    private fun lines(json: String): List<List<List<Double>>> {
        val features = JSONObject(json).getJSONArray("features")
        return (0 until features.length()).map { i ->
            val coords = features.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates")
            (0 until coords.length()).map { j -> coords.getJSONArray(j).let { listOf(it.getDouble(0), it.getDouble(1)) } }
        }
    }

    // Points about 30 m apart along a street.
    private fun street(lat: Double = 53.2, count: Int = 4) = List(count) { lat + it * 0.00027 to 5.8 }

    @Test
    fun theCurrentWalkAndEarlierWalksAreSeparated() {
        val t = trails(1L to street(53.2), 2L to street(53.21))
        val (current, past) = TrailGeoJson.build(t.segments, currentSession = 2L)
        assertEquals(1, lines(current).size)
        assertEquals(1, lines(past).size)
        assertTrue(lines(current)[0][0][1] > 53.209)
        assertTrue(lines(past)[0][0][1] < 53.201)
    }

    @Test
    fun withoutACurrentWalkEverythingIsPast() {
        val t = trails(1L to street())
        val (current, past) = TrailGeoJson.build(t.segments, currentSession = null)
        assertEquals(0, lines(current).size)
        assertEquals(1, lines(past).size)
    }

    @Test
    fun coordinatesAreLongitudeThenLatitudeAndRoundTrip() {
        val t = trails(1L to street(53.2, 2))
        val first = lines(TrailGeoJson.build(t.segments, null).second)[0][0]
        assertEquals(5.8, first[0], 1e-6)
        assertEquals(53.2, first[1], 1e-6)
    }

    @Test
    fun aSegmentWithOnePointIsLeftOut() {
        val t = trails(1L to listOf(53.2 to 5.8))
        assertEquals(0, lines(TrailGeoJson.build(t.segments, null).second).size)
    }

    @Test
    fun pointsCloserThanAFewMetresAreThinnedButTheEndsAreKept() {
        // 20 points about 0.3 m apart: thinned to a few, with the first and the last kept.
        val dense = List(20) { 53.2 + it * 0.0000027 to 5.8 }
        val t = trails(1L to dense)
        val line = lines(TrailGeoJson.build(t.segments, null).second)[0]
        assertTrue(line.size in 2..6)
        assertEquals(53.2, line.first()[1], 1e-6)
        assertEquals(dense.last().first, line.last()[1], 1e-6)
    }

    @Test
    fun aGpsJumpGivesTwoLines() {
        val t = trails(1L to street(53.2, 3) + street(53.5, 3))
        assertEquals(2, lines(TrailGeoJson.build(t.segments, null).second).size)
    }
}

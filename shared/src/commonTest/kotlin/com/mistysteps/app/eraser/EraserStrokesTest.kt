package com.mistysteps.app.eraser

import com.mistysteps.app.data.Trails
import com.mistysteps.app.map.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EraserStrokesTest {
    private val lat0 = 53.2
    private val lon0 = 5.8

    private fun eastward(metres: Double): List<LatLon> {
        val perMetreLon = 1.0 / (111_320.0 * kotlin.math.cos(lat0 * kotlin.math.PI / 180))
        return (0..(metres / 5.0).toInt()).map { LatLon(lat0, lon0 + it * 5.0 * perMetreLon) }
    }

    @Test
    fun feedingAPathPointByPointGivesTheSameSpotsAsSpotsAlong() {
        val path = eastward(200.0)
        val walker = SpotWalker(50.0)
        val live = arrayListOf<LatLon>()
        for (p in path) live += walker.add(p)
        live += walker.finish()
        assertEquals(EraserRules.spotsAlong(path, 50.0), live)
    }

    @Test
    fun aWalkerThatSawOnePointGivesThatOneSpotAndNothingMoreAtTheEnd() {
        val walker = SpotWalker(50.0)
        assertEquals(1, walker.add(LatLon(lat0, lon0)).size)
        assertEquals(0, walker.finish().size)
    }

    @Test
    fun theWalkerCountsTheDragInMetres() {
        val walker = SpotWalker(50.0)
        for (p in eastward(100.0)) walker.add(p)
        assertEquals(100.0, walker.dragM, 1.0)
    }

    @Test
    fun strokeForTagsEverySpotWithTheStrokeAndNoWalk() {
        val stroke = strokeFor(1234L, 5_000L, eastward(100.0), 50.0)
        assertEquals(1234L, stroke.id)
        assertTrue(stroke.spots.size >= 3)
        assertTrue(stroke.spots.all { it.eraseStroke == 1234L && it.sessionId == null && it.radius == 50.0 })
        assertTrue(stroke.spots.zipWithNext().all { (a, b) -> b.time > a.time })
        assertEquals(5_000L, stroke.spots.first().time)
    }

    @Test
    fun aOnePointPathMakesAOneSpotStroke() {
        assertEquals(1, strokeFor(1L, 10L, listOf(LatLon(lat0, lon0)), 50.0).spots.size)
    }

    @Test
    fun anEmptyPathMakesAnEmptyStroke() {
        assertTrue(strokeFor(1L, 10L, emptyList(), 50.0).spots.isEmpty())
    }
}

package com.mistysteps.app.eraser

import com.mistysteps.app.data.Trails
import com.mistysteps.app.map.LatLon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EraserRulesTest {
    private val lat0 = 53.2
    private val lon0 = 5.8

    /** A straight path heading east, [metres] long, made of points every 5 m. */
    private fun eastward(metres: Double): List<LatLon> {
        val perMetreLon = 1.0 / (111_320.0 * kotlin.math.cos(lat0 * kotlin.math.PI / 180))
        val n = (metres / 5.0).toInt()
        return (0..n).map { LatLon(lat0, lon0 + it * 5.0 * perMetreLon) }
    }

    private fun length(path: List<LatLon>): Double =
        path.zipWithNext().sumOf { (a, b) -> Trails.approxDistanceM(a.lat, a.lon, b.lat, b.lon) }

    @Test
    fun theEraserNeedsZoom14OrCloser() {
        assertTrue(!EraserRules.canErase(13.99))
        assertTrue(EraserRules.canErase(14.0))
    }

    @Test
    fun brushRadiusFollowsTheScreenSizeAndIsCappedAt100Metres() {
        assertEquals(90.0, EraserRules.brushRadiusM(90f, 1f), 1e-6)
        assertEquals(100.0, EraserRules.brushRadiusM(90f, 0.5f), 1e-6)
    }

    @Test
    fun aSinglePointStrokeStillErasesOnePatch() {
        assertEquals(1, EraserRules.spotsAlong(listOf(LatLon(lat0, lon0)), 50.0).size)
    }

    @Test
    fun anEmptyPathErasesNothing() {
        assertEquals(0, EraserRules.spotsAlong(emptyList(), 50.0).size)
    }

    @Test
    fun spotsAlongALongPathAreSpaced060TimesTheRadiusApart() {
        val path = eastward(200.0)
        val spots = EraserRules.spotsAlong(path, 50.0)
        assertEquals(path.first(), spots.first())
        val gaps = spots.zipWithNext().map { (a, b) -> Trails.approxDistanceM(a.lat, a.lon, b.lat, b.lon) }
        // 0.6 * 50 m = 30 m between spots, give or take the 5 m path resolution
        assertTrue(gaps.dropLast(1).all { abs(it - 30.0) <= 5.0 }, "gaps were $gaps")
        val last = spots.last()
        assertTrue(Trails.approxDistanceM(last.lat, last.lon, path.last().lat, path.last().lon) <= 15.0)
    }

    @Test
    fun trimToAllowanceCutsTheDragAtTheAllowance() {
        val trimmed = EraserRules.trimToAllowance(eastward(300.0), 100.0)
        assertEquals(100.0, length(trimmed), 1.0)
    }

    @Test
    fun anAllowanceOfZeroGivesAnEmptyPath() {
        assertTrue(EraserRules.trimToAllowance(eastward(300.0), 0.0).isEmpty())
    }
}

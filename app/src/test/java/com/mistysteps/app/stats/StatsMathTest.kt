package com.mistysteps.app.stats

import com.mistysteps.app.data.TrailPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class StatsMathTest {

    /** Axis-aligned box of [km] x [km] around (lat, lon), as a closed GeoJSON-style ring. */
    private fun square(lat: Double, lon: Double, km: Double): DoubleArray {
        val dLat = km / 2 / 111.32
        val dLon = km / 2 / (111.32 * kotlin.math.cos(Math.toRadians(lat)))
        return doubleArrayOf(
            lon - dLon, lat - dLat,
            lon + dLon, lat - dLat,
            lon + dLon, lat + dLat,
            lon - dLon, lat + dLat,
            lon - dLon, lat - dLat,
        )
    }

    @Test
    fun boundaryAreaOfTenKmSquare() {
        val b = Boundary("Box", listOf(listOf(square(53.2, 5.8, 10.0))))
        assertEquals(100e6, b.areaM2, 100e6 * 0.01)
    }

    @Test
    fun holesAreSubtractedAndExcluded() {
        val b = Boundary("Donut", listOf(listOf(square(0.0, 0.0, 10.0), square(0.0, 0.0, 2.0))))
        assertEquals(96e6, b.areaM2, 96e6 * 0.01)
        assertFalse(b.contains(0.0, 0.0))          // in the hole
        assertTrue(b.contains(0.0, 0.03))          // in the ring
        assertFalse(b.contains(1.0, 1.0))          // outside
    }

    @Test
    fun pointBoundaryHasNoArea() {
        val b = Boundary("Village", emptyList())
        assertFalse(b.hasArea)
        assertFalse(b.contains(0.0, 0.0))
    }

    @Test
    fun oneSpotClearsItsCircle() {
        val cells = ClearedCells.from(listOf(TrailPoint(53.2, 5.8, 0, radius = 50.0)))
        val circle = PI * 50 * 50
        assertEquals(circle, cells.areaM2, circle * 0.05)
    }

    @Test
    fun overlappingSpotsAreNotCountedTwice() {
        val once = ClearedCells.from(listOf(TrailPoint(53.2, 5.8, 0))).areaM2
        val twice = ClearedCells.from(listOf(TrailPoint(53.2, 5.8, 0), TrailPoint(53.2, 5.8, 1))).areaM2
        assertEquals(once, twice, 0.0)
    }

    @Test
    fun clearedAreaInsideBorder() {
        val city = Boundary("City", listOf(listOf(square(53.2, 5.8, 10.0))))
        val inside = TrailPoint(53.2, 5.8, 0)
        val outside = TrailPoint(10.0, 10.0, 0)
        val cells = ClearedCells.from(listOf(inside, outside))
        assertEquals(cells.areaM2 / 2, cells.areaInside(city), cells.areaM2 * 0.02)
    }
}

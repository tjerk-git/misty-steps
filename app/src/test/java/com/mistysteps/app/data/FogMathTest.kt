package com.mistysteps.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogMathTest {

    @Test
    fun mercatorCornersAndCentre() {
        assertEquals(0.5, Mercator.x(0.0), 1e-12)
        assertEquals(0.5, Mercator.y(0.0), 1e-12)
        assertEquals(0.0, Mercator.x(-180.0), 1e-12)
        assertEquals(1.0, Mercator.x(180.0), 1e-12)
        assertEquals(0.0, Mercator.y(Mercator.MAX_LAT), 1e-6)
        assertEquals(1.0, Mercator.y(-Mercator.MAX_LAT), 1e-6)
    }

    @Test
    fun fiftyMetresGrowsTowardsThePoles() {
        val equator = Mercator.metersToUnits(50.0, 0.0)
        val leeuwarden = Mercator.metersToUnits(50.0, 53.2)
        // ~1.25e-6 of the world at the equator
        assertEquals(50.0 / 40_075_016.7, equator, 1e-9)
        assertTrue(leeuwarden > equator * 1.6)
        // Cross-check against the y-projection: 50 m north should move y by about the same amount.
        val dy = Mercator.y(53.2) - Mercator.y(53.2 + 50.0 / 111_320.0)
        assertEquals(leeuwarden, dy, leeuwarden * 0.01)
    }

    @Test
    fun indexOnlyReturnsNearbyBuckets() {
        val index = PointIndex()
        index.add(53.2, 5.8, 50.0)   // Leeuwarden
        index.add(-33.9, 151.2, 50.0) // Sydney
        assertEquals(2, index.count)

        val x = Mercator.x(5.8)
        val y = Mercator.y(53.2)
        var seen = 0
        index.forEachBucketIn(x - 1e-4, y - 1e-4, x + 1e-4, y + 1e-4) { seen += it.size }
        assertEquals(1, seen)

        var all = 0
        index.forEachBucketIn(0.0, 0.0, 1.0, 1.0) { all += it.size }
        assertEquals(2, all)
    }

    @Test
    fun mercatorRoundTrips() {
        for (lat in listOf(-80.0, -33.9, 0.0, 53.2, 84.9)) assertEquals(lat, Mercator.lat(Mercator.y(lat)), 1e-9)
        for (lon in listOf(-179.9, 0.0, 5.8, 179.9)) assertEquals(lon, Mercator.lon(Mercator.x(lon)), 1e-9)
    }
}

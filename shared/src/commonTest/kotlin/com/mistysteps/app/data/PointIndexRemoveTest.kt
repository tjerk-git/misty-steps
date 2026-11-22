package com.mistysteps.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointIndexRemoveTest {
    private fun around(index: PointIndex, lat: Double, lon: Double): DoubleArray {
        val x = Mercator.x(lon)
        val y = Mercator.y(lat)
        return index.snapshotIn(x - 0.001, y - 0.001, x + 0.001, y + 0.001)
    }

    @Test
    fun removingASpotLeavesTheOthersAndCountsDown() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)
        index.add(52.0001, 5.0001, 40.0)
        assertTrue(index.remove(52.0, 5.0, 30.0))
        assertEquals(1, index.count)
        val left = around(index, 52.0, 5.0)
        assertEquals(1, left.size / 3)
        assertEquals(Mercator.metersToUnits(40.0, 52.0001), left[2], 1e-12)
    }

    @Test
    fun removingASpotThatIsNotThereChangesNothing() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)
        assertFalse(index.remove(52.5, 5.0, 30.0))
        assertFalse(index.remove(52.0, 5.0, 31.0))
        assertEquals(1, index.count)
    }

    @Test
    fun removingOneOfTwoIdenticalSpotsRemovesOnlyOne() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)
        index.add(52.0, 5.0, 30.0)
        assertTrue(index.remove(52.0, 5.0, 30.0))
        assertEquals(1, index.count)
        assertEquals(1, around(index, 52.0, 5.0).size / 3)
    }
}

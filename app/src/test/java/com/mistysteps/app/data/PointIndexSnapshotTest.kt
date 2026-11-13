package com.mistysteps.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointIndexSnapshotTest {

    private fun spots(a: DoubleArray) = (0 until a.size / 3).map { Triple(a[3 * it], a[3 * it + 1], a[3 * it + 2]) }

    @Test
    fun snapshotHoldsSpotsNearTheRectangleAndSkipsFarOnes() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)   // inside
        index.add(52.0001, 5.0001, 30.0)
        index.add(10.0, 100.0, 30.0) // far away, other bucket

        val x = Mercator.x(5.0)
        val y = Mercator.y(52.0)
        val snap = index.snapshotIn(x - 0.001, y - 0.001, x + 0.001, y + 0.001)

        assertEquals(2, snap.size / 3)
        assertTrue(spots(snap).all { Math.abs(it.first - x) < 0.001 })
    }

    @Test
    fun snapshotIsACopyThatLaterAdditionsDoNotChange() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)
        val x = Mercator.x(5.0)
        val y = Mercator.y(52.0)
        val snap = index.snapshotIn(x - 0.001, y - 0.001, x + 0.001, y + 0.001)

        // Enough new spots in the same bucket to force its array to grow.
        repeat(100) { index.add(52.0, 5.0, 30.0) }

        assertEquals(1, snap.size / 3)
        assertEquals(x, snap[0], 1e-12)
    }

    @Test
    fun snapshotSkipsSpotsOutsideTheRectangleEvenInTheSameBucket() {
        val index = PointIndex()
        index.add(52.0, 5.0, 30.0)
        index.add(52.01, 5.01, 30.0) // about a kilometre away: same bucket, outside the ~400 m rectangle

        val x = Mercator.x(5.0)
        val y = Mercator.y(52.0)
        val snap = index.snapshotIn(x - 1e-5, y - 1e-5, x + 1e-5, y + 1e-5)

        assertEquals(1, snap.size / 3)
        assertEquals(x, snap[0], 1e-12)
    }

    @Test
    fun emptyIndexGivesEmptySnapshot() {
        assertEquals(0, PointIndex().snapshotIn(0.0, 0.0, 1.0, 1.0).size)
    }
}

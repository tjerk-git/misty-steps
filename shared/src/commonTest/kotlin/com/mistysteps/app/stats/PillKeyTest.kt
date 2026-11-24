package com.mistysteps.app.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PillKeyTest {
    @Test
    fun theSameGroundAndPlaceIsTheSameKey() {
        assertEquals(PillRefresh.key(7, 53.2012, 5.7936), PillRefresh.key(7, 53.2012, 5.7936))
    }

    @Test
    fun standingStillWithGpsJitterIsTheSameKey() {
        // A few metres of drift must not trigger a recount.
        assertEquals(PillRefresh.key(7, 53.20120, 5.79360), PillRefresh.key(7, 53.20121, 5.79361))
    }

    @Test
    fun moreClearedGroundIsADifferentKey() {
        assertNotEquals(PillRefresh.key(7, 53.2012, 5.7936), PillRefresh.key(8, 53.2012, 5.7936))
    }

    @Test
    fun walkingOnIsADifferentKey() {
        assertNotEquals(PillRefresh.key(7, 53.2012, 5.7936), PillRefresh.key(7, 53.2025, 5.7936))
        assertNotEquals(PillRefresh.key(7, 53.2012, 5.7936), PillRefresh.key(7, 53.2012, 5.8000))
    }

    @Test
    fun noFixIsItsOwnKeyAndStaysTheSame() {
        assertEquals(PillRefresh.key(7, null, null), PillRefresh.key(7, null, null))
        assertNotEquals(PillRefresh.key(7, null, null), PillRefresh.key(7, 53.2012, 5.7936))
    }

    @Test
    fun aNegativeCoordinateStillFallsInOneCell() {
        assertEquals(PillRefresh.key(1, -33.8700, 151.2093), PillRefresh.key(1, -33.87001, 151.20931))
    }
}

package com.mistysteps.app.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class MistBucketsTest {
    @Test
    fun aSquareMetreIsTwoHundredBuckets() {
        assertEquals(200L, MistBuckets.count(1.0))
        assertEquals(7_800_000L, MistBuckets.count(39_000.0))
    }

    @Test
    fun noGroundOrNonsenseMeansNoBuckets() {
        assertEquals(0L, MistBuckets.count(0.0))
        assertEquals(0L, MistBuckets.count(-3.0))
        assertEquals(0L, MistBuckets.count(Double.NaN))
        assertEquals(0L, MistBuckets.count(Double.POSITIVE_INFINITY))
    }

    @Test
    fun anAbsurdAreaStaysAValidNumber() {
        assertEquals(Long.MAX_VALUE, MistBuckets.count(1.0e300))
    }

    @Test
    fun readableKeepsTwoSignificantDigitsFromOneHundredUp() {
        assertEquals(0L, MistBuckets.readable(0))
        assertEquals(99L, MistBuckets.readable(99))
        assertEquals(100L, MistBuckets.readable(100))
        assertEquals(7_800_000L, MistBuckets.readable(7_812_345))
        assertEquals(850_000_000L, MistBuckets.readable(848_000_000))
        assertEquals(8_200_000_000_000L, MistBuckets.readable(8_150_000_000_000))
    }
}

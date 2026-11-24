package com.mistysteps.app.postcard

import kotlin.test.Test
import kotlin.test.assertEquals

class WalkStepsTest {
    @Test
    fun countDividesDistanceByStride() {
        assertEquals(2000L, WalkSteps.count(1000.0, 0.5))
        assertEquals(20_000_000L, WalkSteps.count(1_000_000.0, 0.05))
    }

    @Test
    fun noDistanceOrNoStrideMeansNoSteps() {
        assertEquals(0L, WalkSteps.count(0.0, 0.5))
        assertEquals(0L, WalkSteps.count(-5.0, 0.5))
        assertEquals(0L, WalkSteps.count(1000.0, 0.0))
        assertEquals(0L, WalkSteps.count(1000.0, -1.0))
        assertEquals(0L, WalkSteps.count(Double.NaN, 0.5))
        assertEquals(0L, WalkSteps.count(Double.POSITIVE_INFINITY, 0.5))
        assertEquals(0L, WalkSteps.count(1000.0, Double.NaN))
    }

    @Test
    fun anAbsurdDistanceStaysAValidNumber() {
        assertEquals(Long.MAX_VALUE, WalkSteps.count(1.0e300, 0.05))
    }

    @Test
    fun readableKeepsTwoSignificantDigitsFromOneHundredUp() {
        assertEquals(0L, WalkSteps.readable(0))
        assertEquals(99L, WalkSteps.readable(99))
        assertEquals(100L, WalkSteps.readable(100))
        assertEquals(31_000L, WalkSteps.readable(31_234))
        assertEquals(7_500L, WalkSteps.readable(7_460))
        assertEquals(120_000L, WalkSteps.readable(123_456))
        assertEquals(20_000_000L, WalkSteps.readable(20_000_000))
    }
}

package com.mistysteps.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class EraseFilterTest {
    private val walked = TrailPoint(52.0, 5.0, 1L, 50.0, 7L)
    private val old = TrailPoint(52.1, 5.1, 2L, 50.0, null)
    private val erased = TrailPoint(52.2, 5.2, 3L, 60.0, null, 99L)

    @Test
    fun walkedOnlyKeepsWalkedAndOldSpotsAndDropsErasedOnes() {
        assertEquals(listOf(walked, old), walkedOnly(listOf(walked, erased, old)))
    }

    @Test
    fun walkedOnlyOfNothingIsNothing() {
        assertEquals(emptyList(), walkedOnly(emptyList()))
    }
}

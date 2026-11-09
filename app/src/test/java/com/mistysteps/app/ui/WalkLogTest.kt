package com.mistysteps.app.ui

import com.mistysteps.app.data.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkLogTest {
    private fun walk(id: Long, m: Double, ended: Boolean = true) =
        Session(id = id, startTime = id, endTime = if (ended) id + 1 else null, distanceM = m)

    @Test
    fun tinyWalksAreLeftOut() {
        val walks = listOf(walk(1, 0.0), walk(2, 49.0), walk(3, 50.0), walk(4, 5_900.0))
        assertEquals(listOf(3L, 4L), walksToShow(walks, emptySet()).map { it.id })
    }

    @Test
    fun tinyWalkWithAPinStays() {
        assertEquals(listOf(1L), walksToShow(listOf(walk(1, 0.0)), setOf(1L)).map { it.id })
    }

    @Test
    fun walkInProgressStays() {
        assertEquals(listOf(1L), walksToShow(listOf(walk(1, 0.0, ended = false)), emptySet()).map { it.id })
    }
}

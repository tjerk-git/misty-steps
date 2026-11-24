package com.mistysteps.app.ui

import com.mistysteps.app.data.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class PostcardPagingTest {
    private fun walk(id: Long, distance: Double = 500.0, ended: Boolean = true) =
        Session(id = id, startTime = id, endTime = if (ended) id + 1000 else null, distanceM = distance)

    @Test
    fun newestFirst() {
        assertEquals(listOf(3L, 2L, 1L), PostcardPaging.walkIds(listOf(walk(3), walk(1), walk(2)), emptySet(), 2))
    }

    @Test
    fun shortWalksWithoutPinsAreLeftOut() {
        val walks = listOf(walk(3), walk(2, distance = 30.0), walk(1))
        assertEquals(listOf(3L, 1L), PostcardPaging.walkIds(walks, emptySet(), 3))
    }

    @Test
    fun aShortWalkWithAPinStays() {
        val walks = listOf(walk(3), walk(2, distance = 30.0), walk(1))
        assertEquals(listOf(3L, 2L, 1L), PostcardPaging.walkIds(walks, setOf(2L), 3))
    }

    @Test
    fun aRunningWalkIsLeftOut() {
        // The log does not let you open a walk that is still going, so you cannot swipe to it either.
        val walks = listOf(walk(3, distance = 10.0, ended = false), walk(1))
        assertEquals(listOf(1L), PostcardPaging.walkIds(walks, emptySet(), 1))
    }

    @Test
    fun aRunningWalkYouOpenedStays() {
        val walks = listOf(walk(3, distance = 10.0, ended = false), walk(1))
        assertEquals(listOf(3L, 1L), PostcardPaging.walkIds(walks, emptySet(), 3))
    }

    @Test
    fun theStartWalkIsAlwaysIncludedInItsSortedPlace() {
        val walks = listOf(walk(3), walk(2, distance = 30.0), walk(1))
        assertEquals(listOf(3L, 2L, 1L), PostcardPaging.walkIds(walks, emptySet(), 2))
    }

    @Test
    fun theStartWalkIsNotDuplicated() {
        assertEquals(listOf(3L, 1L), PostcardPaging.walkIds(listOf(walk(3), walk(1)), emptySet(), 3))
    }

    @Test
    fun anEmptyListGivesJustTheStart() {
        assertEquals(listOf(7L), PostcardPaging.walkIds(emptyList(), emptySet(), 7))
    }
}

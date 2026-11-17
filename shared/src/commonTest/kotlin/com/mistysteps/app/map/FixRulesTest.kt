package com.mistysteps.app.map

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class FixRulesTest {
    private fun fix(time: Long, accuracy: Float? = 10f) = Fix(53.2, 5.8, bearing = null, speed = null, accuracy = accuracy, time = time)

    @Test
    fun theFirstFixIsTaken() {
        val f = fix(1000)
        assertSame(f, FixRules.prefer(null, f))
    }

    @Test
    fun aNewerFixReplacesAnOlderOne() {
        val old = fix(1000)
        val new = fix(2000)
        assertSame(new, FixRules.prefer(old, new))
    }

    @Test
    fun anOlderFixNeverReplacesANewerOne() {
        val old = fix(1000)
        val new = fix(2000)
        assertSame(new, FixRules.prefer(new, old))
    }

    @Test
    fun aFixAtTheSameTimeButMoreAccurateIsPreferred() {
        val rough = fix(1000, accuracy = 50f)
        val good = fix(1000, accuracy = 5f)
        assertSame(good, FixRules.prefer(rough, good))
        assertSame(good, FixRules.prefer(good, rough))
    }

    @Test
    fun aFixOlderThanTenSecondsIsStale() {
        assertTrue(FixRules.isStale(fix(0), now = 10_001))
        assertFalse(FixRules.isStale(fix(0), now = 10_000))
        assertEquals(false, FixRules.isStale(fix(5000), now = 6000))
    }
}

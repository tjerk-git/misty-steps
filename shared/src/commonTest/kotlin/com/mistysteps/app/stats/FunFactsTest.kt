package com.mistysteps.app.stats

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunFactsTest {
    private fun logSpace(from: Double, to: Double, n: Int) = (0 until n).map { from * (to / from).pow(it.toDouble() / (n - 1)) }

    private fun inWindow(fact: Fact) =
        if (fact.reference.asPercent) fact.value >= 0.0001 && fact.value < 100.0 else fact.value >= 1.5 && fact.value < 10_000.0

    @Test
    fun noAmountNoFact() {
        for (measure in FactMeasure.entries) {
            for (bad in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertNull(FunFacts.pick(measure, bad, 0), "$measure $bad")
            }
        }
    }

    @Test
    fun theRatioStaysInItsWindow() {
        val ranges = mapOf(
            FactMeasure.DISTANCE to (40.0 to 4.0e8),
            FactMeasure.AREA to (100.0 to 4.2e10),
            FactMeasure.VOLUME to (2.0e5 to 8.4e13),
        )
        for ((measure, range) in ranges) for (amount in logSpace(range.first, range.second, 60)) for (day in 0L..3L) {
            val fact = assertNotNull(FunFacts.pick(measure, amount, day), "$measure $amount")
            assertTrue(inWindow(fact), "$measure $amount gave ${fact.reference.key} ${fact.value}")
        }
    }

    @Test
    fun everyRealisticAmountHasAFact() {
        assertNotNull(FunFacts.pick(FactMeasure.DISTANCE, 64_100.0, 0))
        assertNotNull(FunFacts.pick(FactMeasure.AREA, 4_240_000.0, 0))
        assertNotNull(FunFacts.pick(FactMeasure.VOLUME, 8_480_000_000.0, 0))
    }

    @Test
    fun theSameDayGivesTheSameFact() {
        assertEquals(FunFacts.pick(FactMeasure.DISTANCE, 64_100.0, 20_000), FunFacts.pick(FactMeasure.DISTANCE, 64_100.0, 20_000))
    }

    @Test
    fun differentDaysRotate() {
        val references = (0L..9L).mapNotNull { FunFacts.pick(FactMeasure.DISTANCE, 64_100.0, 20_000 + it)?.reference?.key }.toSet()
        assertTrue(references.size >= 2, "the same reference every day: $references")
    }

    @Test
    fun aNegativeDayStillPicks() {
        assertNotNull(FunFacts.pick(FactMeasure.AREA, 4_240_000.0, -3))
    }

    @Test
    fun roundForReadingKeepsTwoSignificantDigits() {
        assertEquals(590.0, FunFacts.roundForReading(594.2))
        assertEquals(32.0, FunFacts.roundForReading(31.6))
        assertEquals(2.0, FunFacts.roundForReading(2.04))
        assertEquals(1.6, FunFacts.roundForReading(1.55))
        assertEquals(10_000.0, FunFacts.roundForReading(9999.4))
        assertEquals(120_000.0, FunFacts.roundForReading(123_456.0))
    }

    @Test
    fun keysAreUniqueAndNamed() {
        val tennis = FunFacts.references(FactMeasure.DISTANCE).first { it.key == "tennis_court" }
        assertEquals("fact_distance_tennis_court", FunFacts.stringKey(FactMeasure.DISTANCE, tennis))
        val all = FactMeasure.entries.flatMap { m -> FunFacts.references(m).map { FunFacts.stringKey(m, it) } }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.size >= 20)
    }
}

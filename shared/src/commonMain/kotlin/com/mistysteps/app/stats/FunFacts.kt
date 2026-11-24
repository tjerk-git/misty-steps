package com.mistysteps.app.stats

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** What a fun fact measures. */
enum class FactMeasure { DISTANCE, AREA, VOLUME }

/**
 * Something to compare with. [size] is in metres, square metres or litres by measure; [asPercent] says the fact is "x% of the
 * way" to it (the moon) rather than "x times" it.
 */
data class FactReference(val key: String, val size: Double, val asPercent: Boolean = false)

/** [value] is how many times [reference] fits in the amount (or the percentage, for a [FactReference.asPercent] reference). */
data class Fact(val reference: FactReference, val value: Double)

/**
 * Crazy comparisons for the stats page. Constants are approximate (this is a joke, not a survey); each has its source named
 * next to it. A reference is only used when the number reads nicely: between 1,5 and 9.999 times it (the moon: 0,0001% to 100%).
 */
object FunFacts {
    private val distance = listOf(
        FactReference("tennis_court", 23.77), // length of a tennis court, ITF
        FactReference("blue_whale", 30.0), // large blue whale, about 30 m
        FactReference("football_pitch", 105.0), // a regular football pitch length (UEFA 105 m)
        FactReference("dom_tower", 112.0), // Dom Tower, Utrecht
        FactReference("eiffel", 330.0), // Eiffel Tower with antenna
        FactReference("burj", 828.0), // Burj Khalifa
        FactReference("marathon", 42_195.0), // marathon distance
        FactReference("afsluitdijk", 32_000.0), // Afsluitdijk, about 32 km
        FactReference("elfsteden", 200_000.0), // Elfstedentocht, about 200 km
        FactReference("moon", 384_400_000.0, asPercent = true), // mean distance to the moon
    )

    private val area = listOf(
        FactReference("parking_space", 12.5), // a parking space, about 2,5 by 5 m
        FactReference("tennis_court", 261.0), // a tennis court, 23,77 by 10,97 m
        FactReference("football_pitch", 7_140.0), // 105 by 68 m
        FactReference("vatican", 440_000.0), // Vatican City, 0,44 km2
        FactReference("vondelpark", 470_000.0), // Vondelpark, about 47 ha
        FactReference("monaco", 2_020_000.0), // Monaco, 2,02 km2
        FactReference("schiermonnikoog", 40_000_000.0), // Schiermonnikoog, about 40 km2
        FactReference("texel", 162_000_000.0), // Texel, land only: about 162 km2 (463 km2 counts the sea around it)
        FactReference("luxembourg", 2_586_000_000.0), // Luxembourg, 2.586 km2
        FactReference("friesland", 3_340_000_000.0), // Friesland, land only: about 3.340 km2 (5.750 km2 counts the IJsselmeer and the Wadden Sea)
    )

    private val volume = listOf(
        FactReference("bathtub", 150.0), // a bathtub, about 150 litres
        FactReference("garden_pool", 30_000.0), // a garden swimming pool, about 30 m3
        FactReference("olympic_pool", 2_500_000.0), // Olympic swimming pool, 2.500 m3
        FactReference("empire_state", 1_050_000_000.0), // Empire State Building, about 1,05 million m3
        FactReference("pyramid", 2_600_000_000.0), // Great Pyramid of Giza, about 2,6 million m3
        FactReference("windermere", 314_000_000_000.0), // Lake Windermere, about 0,31 km3
        FactReference("ijsselmeer", 5_000_000_000_000.0), // IJsselmeer, about 5 km3
    )

    fun references(measure: FactMeasure): List<FactReference> = when (measure) {
        FactMeasure.DISTANCE -> distance
        FactMeasure.AREA -> area
        FactMeasure.VOLUME -> volume
    }

    /** The name of the sentence in the strings files: `fact_distance_marathon`. */
    fun stringKey(measure: FactMeasure, reference: FactReference): String = "fact_${measure.name.lowercase()}_${reference.key}"

    /**
     * One comparison for [amount] (metres, square metres or litres), or null when there is none worth making. [day] (days since
     * 1970) rotates the choice: the same all day, another tomorrow.
     */
    fun pick(measure: FactMeasure, amount: Double, day: Long): Fact? {
        if (!amount.isFinite() || amount <= 0.0) return null
        val candidates = references(measure).mapNotNull { reference ->
            val value = if (reference.asPercent) amount / reference.size * 100.0 else amount / reference.size
            val reads = if (reference.asPercent) value >= 0.0001 && value < 100.0 else value >= 1.5 && value < 10_000.0
            if (reads) Fact(reference, value) else null
        }
        if (candidates.isEmpty()) return null
        val n = candidates.size.toLong()
        val index = (((day + measure.ordinal) % n) + n) % n
        return candidates[index.toInt()]
    }

    /** Two significant digits from 100 up, whole numbers from 10, one decimal below: 594,2 reads 590; 31,6 reads 32; 2,04 reads 2. */
    fun roundForReading(value: Double): Double = when {
        value >= 100.0 -> {
            val magnitude = 10.0.pow(floor(log10(value)) - 1)
            round(value / magnitude) * magnitude
        }
        value >= 10.0 -> round(value)
        else -> round(value * 10.0) / 10.0
    }
}

package com.mistysteps.app.stats

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** What a fun fact measures. */
enum class FactMeasure { DISTANCE, AREA, VOLUME }

/**
 * Something to compare with. [size] is in metres, square metres or litres by measure; [asPercent] says the fact is "x% of the
 * way" to it rather than "x times" it (no reference uses it now).
 */
data class FactReference(val key: String, val size: Double, val asPercent: Boolean = false)

/** [value] is how many times [reference] fits in the amount (or the percentage, for a [FactReference.asPercent] reference). */
data class Fact(val reference: FactReference, val value: Double)

/**
 * Crazy comparisons for the stats page. Constants are approximate (this is a joke, not a survey); each has its source named
 * next to it. A reference is only used when the number reads nicely: between 1,5 and 9.999 times it (a percentage reference: 0,0001% to 100%).
 */
object FunFacts {
    // Everything here is Dutch (buildings, places, animals) apart from a few everyday units that need no introduction.
    private val distance = listOf(
        FactReference("swan", 1.5), // a mute swan, beak to tail, about 1,5 m
        FactReference("bike", 1.8), // a Dutch bicycle, about 1,8 m
        FactReference("seal", 2.0), // a grey seal of the Wadden Sea, about 2 m
        FactReference("frisian_horse", 2.4), // a Friesian horse, nose to tail, about 2,4 m
        FactReference("humpback", 15.0), // a humpback whale, about 15 m
        FactReference("tennis_court", 23.77), // length of a tennis court, ITF
        FactReference("blue_whale", 30.0), // large blue whale, about 30 m
        FactReference("oldehove", 39.5), // the leaning Oldehove tower in Leeuwarden, about 39,5 m
        FactReference("football_pitch", 105.0), // a regular football pitch length (UEFA 105 m)
        FactReference("dom_tower", 112.0), // Dom Tower, Utrecht
        FactReference("euromast", 185.0), // Euromast, Rotterdam
        FactReference("erasmusbrug", 802.0), // Erasmusbrug, Rotterdam, 802 m
        FactReference("zeelandbrug", 5_022.0), // Zeelandbrug, 5.022 m
        FactReference("oosterschelde", 9_000.0), // Oosterscheldekering, about 9 km
        FactReference("afsluitdijk", 32_000.0), // Afsluitdijk, about 32 km
        FactReference("marathon", 42_195.0), // marathon distance
        FactReference("elfsteden", 200_000.0), // Elfstedentocht, about 200 km
    )

    private val area = listOf(
        FactReference("parking_space", 12.5), // a parking space, about 2,5 by 5 m
        FactReference("tennis_court", 261.0), // a tennis court, 23,77 by 10,97 m
        FactReference("football_pitch", 7_140.0), // 105 by 68 m
        FactReference("madurodam", 18_000.0), // Madurodam, about 1,8 ha
        FactReference("keukenhof", 320_000.0), // Keukenhof, 32 ha
        FactReference("vondelpark", 470_000.0), // Vondelpark, about 47 ha
        FactReference("vlieland", 36_000_000.0), // Vlieland, about 36 km2
        FactReference("schiermonnikoog", 40_000_000.0), // Schiermonnikoog, about 40 km2
        FactReference("hoge_veluwe", 55_000_000.0), // Nationaal Park De Hoge Veluwe, about 5.500 ha
        FactReference("ameland", 58_000_000.0), // Ameland, land only: about 58 km2
        FactReference("texel", 162_000_000.0), // Texel, land only: about 162 km2 (463 km2 counts the sea around it)
        FactReference("flevoland", 1_412_000_000.0), // Flevoland, land only: about 1.412 km2
        FactReference("friesland", 3_340_000_000.0), // Friesland, land only: about 3.340 km2 (5.750 km2 counts the IJsselmeer and the Wadden Sea)
    )

    // The mist is 2 m deep, so a place full of mist is its area times 2.000 litres per m2.
    private val volume = listOf(
        FactReference("bathtub", 150.0), // a bathtub, about 150 litres
        FactReference("frisian_horse", 600.0), // a Friesian horse weighs about 600 kg, about 600 litres
        FactReference("elephant", 5_000.0), // an elephant, about 5 tonnes
        FactReference("garden_pool", 30_000.0), // a garden swimming pool, about 30 m3
        FactReference("blue_whale", 150_000.0), // a blue whale, about 150 tonnes
        FactReference("olympic_pool", 2_500_000.0), // Olympic swimming pool, 2.500 m3
        FactReference("madurodam", 36_000_000.0), // Madurodam, 18.000 m2 of mist 2 m deep
        FactReference("keukenhof", 640_000_000.0), // Keukenhof, 320.000 m2 of mist 2 m deep
        FactReference("vondelpark", 940_000_000.0), // Vondelpark, 470.000 m2 of mist 2 m deep
        FactReference("schiermonnikoog", 80_000_000_000.0), // Schiermonnikoog, 40 km2 of mist 2 m deep
        FactReference("texel", 324_000_000_000.0), // Texel, 162 km2 of mist 2 m deep
        FactReference("markermeer", 2_500_000_000_000.0), // Markermeer, about 2,5 km3
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

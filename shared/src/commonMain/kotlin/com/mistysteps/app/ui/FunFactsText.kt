package com.mistysteps.app.ui

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.allStringResources
import com.mistysteps.app.stats.FactMeasure
import com.mistysteps.app.stats.FunFacts
import com.mistysteps.app.stats.MistBuckets
import com.mistysteps.app.stats.Stats
import org.jetbrains.compose.resources.getString

private fun factNumber(value: Double): String =
    formatNumber(value, if (value < 10.0 && value % 1.0 != 0.0) 1 else 0, grouping = true)

/**
 * Up to three quirky lines for the stats page: your distance, the ground you cleared and the mist you cleared, each compared
 * with something real. A measure with no sensible comparison (nothing walked yet) is left out. [day] rotates the choice.
 */
suspend fun funFactLines(stats: Stats, day: Long): List<String> = buildList {
    val measures = listOf(
        FactMeasure.DISTANCE to stats.distanceM,
        FactMeasure.AREA to stats.clearedM2,
        FactMeasure.VOLUME to stats.clearedM2 * MistBuckets.LITRES_PER_SQUARE_METRE,
    )
    for ((measure, amount) in measures) {
        val fact = FunFacts.pick(measure, amount, day) ?: continue
        val sentence = Res.allStringResources[FunFacts.stringKey(measure, fact.reference)] ?: continue
        val number = if (fact.reference.asPercent) formatPercent(fact.value / 100.0) else factNumber(FunFacts.roundForReading(fact.value))
        add(getString(sentence, number))
    }
}

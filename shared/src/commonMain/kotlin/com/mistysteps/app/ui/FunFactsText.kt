package com.mistysteps.app.ui

import com.mistysteps.app.character.Character
import com.mistysteps.app.postcard.WalkSteps
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.allStringResources
import com.mistysteps.app.resources.fact_character_steps
import com.mistysteps.app.stats.FactMeasure
import com.mistysteps.app.stats.FunFacts
import com.mistysteps.app.stats.MistBuckets
import com.mistysteps.app.stats.Stats
import org.jetbrains.compose.resources.getString

private fun factNumber(value: Double): String =
    formatNumber(value, if (value < 10.0 && value % 1.0 != 0.0) 1 else 0, grouping = true)

/**
 * Up to three quirky lines for the stats page: your distance, the ground you cleared and the mist you cleared, each compared
 * with something real. A measure with no sensible comparison (nothing walked yet) is left out. [day] rotates the choice. With a
 * [character], a line follows the distance one: how many steps that animal needed to walk it all (its stride is in the sprite sheet).
 */
suspend fun funFactLines(stats: Stats, day: Long, character: Character? = null, customName: String? = null): List<String> = buildList {
    val measures = listOf(
        FactMeasure.DISTANCE to stats.distanceM,
        FactMeasure.AREA to stats.clearedM2,
        FactMeasure.VOLUME to stats.clearedM2 * MistBuckets.LITRES_PER_SQUARE_METRE,
    )
    for ((measure, amount) in measures) {
        FunFacts.pick(measure, amount, day)?.let { fact ->
            val sentence = Res.allStringResources[FunFacts.stringKey(measure, fact.reference)]
            if (sentence != null) {
                val number = if (fact.reference.asPercent) formatPercent(fact.value / 100.0) else factNumber(FunFacts.roundForReading(fact.value))
                add(getString(sentence, number))
            }
        }
        // Your animal's own count, even for a walk too short for a comparison.
        if (measure == FactMeasure.DISTANCE && character != null) {
            val steps = WalkSteps.readable(WalkSteps.count(stats.distanceM, character.strideM))
            if (steps > 0L) add(getString(Res.string.fact_character_steps, animalName(character, customName), factNumber(steps.toDouble())))
        }
    }
}

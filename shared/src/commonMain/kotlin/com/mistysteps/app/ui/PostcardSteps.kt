package com.mistysteps.app.ui

import com.mistysteps.app.character.Character
import com.mistysteps.app.postcard.WalkSteps
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.allStringResources
import com.mistysteps.app.resources.postcard_steps
import org.jetbrains.compose.resources.getString

/**
 * "That is about 31,000 Trail Cat steps.": the walk's distance in steps of [character], on the postcard. The animal is the name
 * you gave your character, or else its (translated) species name. Null when the walk has no steps to show.
 */
suspend fun postcardStepsLine(character: Character, customName: String?, distanceM: Double): String? {
    val steps = WalkSteps.readable(WalkSteps.count(distanceM, character.strideM))
    if (steps <= 0L) return null
    val animal = customName?.takeIf { it.isNotBlank() }
        ?: Res.allStringResources["character_${character.id}_name"]?.let { getString(it) }
        ?: character.name
    return getString(Res.string.postcard_steps, formatNumber(steps.toDouble(), 0, grouping = true), animal)
}

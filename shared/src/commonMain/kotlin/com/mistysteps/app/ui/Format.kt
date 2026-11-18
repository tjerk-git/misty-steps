package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.duration_h_min
import com.mistysteps.app.resources.duration_min
import com.mistysteps.app.resources.duration_under_minute
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/** Two significant digits, however tiny: 12%, 0.48%, 0.0000031%. */
fun formatPercent(fraction: Double): String {
    val pct = fraction * 100
    if (pct <= 0) return "0%"
    if (pct >= 10) return formatNumber(pct, 0) + "%"
    val decimals = max(1, 1 - floor(log10(pct)).toInt())
    return formatNumber(pct, decimals) + "%"
}

fun formatArea(m2: Double): String = when {
    m2 < 10_000 -> formatNumber(m2, 0) + " m\u00B2"
    m2 < 1_000_000 -> formatNumber(m2 / 10_000, 1) + " ha"
    m2 < 100_000_000 -> formatNumber(m2 / 1_000_000, 2) + " km\u00B2"
    else -> formatNumber(m2 / 1_000_000, 0, grouping = true) + " km\u00B2"
}

fun formatDistance(m: Double): String =
    if (m < 1000) "${m.toInt()} m" else formatNumber(m / 1000, 1) + " km"

/** Below this share a percentage is unreadable (0.0000031%), so a card says "1 in 32 million" instead. */
const val ONE_IN_BELOW = 1e-5

/** "54,000,000": the "1 in N" for a share, rounded to two significant digits. */
fun formatOneIn(fraction: Double): String {
    val n = 1 / fraction
    val magnitude = 10.0.pow(floor(log10(n)) - 1)
    return formatNumber(round(n / magnitude) * magnitude, 0, grouping = true)
}

/** The words in a duration, from the string resources ([EN] is for tests). */
class DurationText(val underMinute: String, val min: String, val hMin: String) {
    companion object {
        val EN = DurationText("under a minute", "%1\$d min", "%1\$d h %2\$02d min")
    }
}

@Composable
fun rememberDurationText(): DurationText {
    val under = stringResource(Res.string.duration_under_minute)
    val min = stringResource(Res.string.duration_min)
    val hMin = stringResource(Res.string.duration_h_min)
    return remember(under, min, hMin) { DurationText(under, min, hMin) }
}

/** "under a minute", "12 min", "1 h 05 min". */
fun formatDuration(ms: Long, words: DurationText): String {
    val min = ms / 60_000
    return when {
        min < 1 -> words.underMinute
        min < 60 -> words.min.replace("%1\$d", min.toString())
        else -> words.hMin.replace("%1\$d", (min / 60).toString()).replace("%2\$02d", (min % 60).toString().padStart(2, '0'))
    }
}

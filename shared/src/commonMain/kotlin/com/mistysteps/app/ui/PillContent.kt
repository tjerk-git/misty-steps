package com.mistysteps.app.ui

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.area_one_in
import com.mistysteps.app.resources.pill_place_line
import com.mistysteps.app.resources.pill_total_caption
import com.mistysteps.app.stats.MistBuckets
import com.mistysteps.app.stats.PillPick
import com.mistysteps.app.stats.Stats
import org.jetbrains.compose.resources.getString

/** What the pill at the top of the map says: a big [value], a small [caption] and, in a known place, a second line [place]. */
data class PillContent(val value: String, val caption: String, val place: String?)

private fun buckets(areaM2: Double): String =
    formatNumber(MistBuckets.readable(MistBuckets.count(areaM2)).toDouble(), 0, grouping = true)

/**
 * "7.800.000" over "buckets of mist cleared" over "in Leeuwarden (0,066%)": the mist you cleared in your city or town, with the
 * place on a line of its own so it never breaks in an odd spot. Outside a known place, or without a fix, it is the buckets for
 * everything you cleared and no second line.
 */
suspend fun pillContent(stats: Stats): PillContent {
    val caption = getString(Res.string.pill_total_caption)
    val place = PillPick.place(stats)
    val name = place?.name
    val fraction = place?.fraction
    if (place == null || name == null || fraction == null) return PillContent(buckets(stats.clearedM2), caption, null)
    val oneIn = getString(Res.string.area_one_in, "%s")
    // Non-breaking spaces inside the share, so "(1 op 54.000.000)" is never split across two lines.
    val share = formatShare(fraction) { oneIn.replace("%s", it) }.replace(' ', '\u00A0')
    return PillContent(buckets(place.clearedM2), caption, getString(Res.string.pill_place_line, name, share))
}

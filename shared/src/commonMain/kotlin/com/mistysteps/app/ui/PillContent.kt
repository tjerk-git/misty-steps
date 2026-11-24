package com.mistysteps.app.ui

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.area_one_in
import com.mistysteps.app.resources.pill_place_caption
import com.mistysteps.app.resources.pill_total_caption
import com.mistysteps.app.stats.MistBuckets
import com.mistysteps.app.stats.PillPick
import com.mistysteps.app.stats.Stats
import org.jetbrains.compose.resources.getString

/** What the pill at the top of the map says: a big [value] and a small [caption]. */
data class PillContent(val value: String, val caption: String)

private fun buckets(areaM2: Double): String =
    formatNumber(MistBuckets.readable(MistBuckets.count(areaM2)).toDouble(), 0, grouping = true)

/**
 * "7.800.000" over "buckets of mist cleared in Leeuwarden (0,066%)": the mist you cleared in your city or town. Outside a known
 * place, or without a fix, it is the buckets for everything you cleared, with a plainer caption.
 */
suspend fun pillContent(stats: Stats): PillContent {
    val place = PillPick.place(stats)
    val name = place?.name
    val fraction = place?.fraction
    if (place == null || name == null || fraction == null) {
        return PillContent(buckets(stats.clearedM2), getString(Res.string.pill_total_caption))
    }
    val oneIn = getString(Res.string.area_one_in, "%s")
    val share = formatShare(fraction) { oneIn.replace("%s", it) }
    return PillContent(buckets(place.clearedM2), getString(Res.string.pill_place_caption, name, share))
}

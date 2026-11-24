package com.mistysteps.app.stats

import kotlin.math.floor

/** Which place the pill at the top of the map talks about, and how often it is recounted. */
object PillPick {
    /** Your city or town if its share is known, else the country if known, else null (the pill then shows the total). */
    fun place(stats: Stats): AreaStat? =
        stats.city?.takeIf { it.fraction != null } ?: stats.country?.takeIf { it.fraction != null }
}

/** What the pill's text depends on: how much ground is cleared, and roughly where you stand. Same key, same text. */
data class PillKey(val version: Long, val cellLat: Long?, val cellLon: Long?)

object PillRefresh {
    /** At most one recount this often (and only when the [PillKey] changed). */
    const val INTERVAL_MS = 10_000L

    // About 50 m: GPS jitter while standing still stays inside one cell.
    private const val CELL_LAT_DEG = 0.00045
    private const val CELL_LON_DEG = 0.00075

    /** [version] counts changes to the cleared ground; [lat]/[lon] are where you are, or null without a fix. */
    fun key(version: Long, lat: Double?, lon: Double?): PillKey =
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) PillKey(version, null, null)
        else PillKey(version, floor(lat / CELL_LAT_DEG).toLong(), floor(lon / CELL_LON_DEG).toLong())
}

package com.mistysteps.app.stats

/** Which place the pill at the top of the map talks about, and how often it is recounted. */
object PillPick {
    /** Your city or town if its share is known, else the country if known, else null (the pill then shows the total). */
    fun place(stats: Stats): AreaStat? =
        stats.city?.takeIf { it.fraction != null } ?: stats.country?.takeIf { it.fraction != null }
}

object PillRefresh {
    /** The pill is recounted this often while the map is visible. */
    const val INTERVAL_MS = 10_000L
}

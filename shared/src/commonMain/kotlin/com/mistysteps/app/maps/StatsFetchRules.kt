package com.mistysteps.app.maps

/**
 * The installed packs that still need their neighbourhoods-and-streets file: the catalog lists one for the province, the
 * installed map is of the catalog's year (an older map gets its stats file together with its update) and the pack has none yet.
 */
fun statsToFetch(entries: List<PackEntry>, installedYear: (String) -> Int?, hasStats: (String) -> Boolean): List<PackEntry> =
    entries.filter { it.stats != null && installedYear(it.id) == it.year && !hasStats(it.id) }

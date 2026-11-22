package com.mistysteps.app.maps

/** A file on the maps server, with what the app needs to check it after downloading. */
data class FileRef(val url: String, val bytes: Long, val sha256: String)

/** One province's pack in the catalog. [stats] is the neighbourhoods and streets file that comes with it (older catalogs have none). */
data class PackEntry(
    val id: String,
    val name: String,
    val year: Int,
    val bbox: List<Double>,
    val map: FileRef,
    val stats: FileRef?,
) {
    /** Everything a download of this province fetches: the map and its stats file. This is the size people are shown. */
    val totalBytes: Long get() = map.bytes + (stats?.bytes ?: 0L)
}

package com.mistysteps.app.maps

/** A file on the maps server, with what the app needs to check it after downloading. */
data class FileRef(val url: String, val bytes: Long, val sha256: String)

/** One province's pack in the catalog. [stats] is the neighbourhoods and streets file (not published yet). */
data class PackEntry(
    val id: String,
    val name: String,
    val year: Int,
    val bbox: List<Double>,
    val map: FileRef,
    val stats: FileRef?,
)

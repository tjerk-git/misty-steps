package com.mistysteps.app.stats

import java.io.File
import java.util.zip.ZipFile

/**
 * One neighbourhoods-and-streets file (see [StatsPackFormat]). Entries are read one at a time, so asking for one
 * neighbourhood's streets never parses the whole province. Nothing here throws: a missing, damaged or newer file
 * simply has no [index] and gives null for every read.
 */
class ZipStatsPack(private val file: File) {
    /** What `index.json` says, or null when the file is missing, not a zip, damaged or of another format. */
    val index: StatsIndex? by lazy { read(StatsPackFormat.INDEX_ENTRY)?.let(StatsPackFormat::parseIndex) }

    /** The neighbourhoods of the town [cityId], or null when this file has none (or cannot be read). */
    fun hoods(cityId: Long): List<Boundary>? =
        if (index == null) null else read(StatsPackFormat.hoodsEntry(cityId))?.let(StatsPackFormat::parseHoods)

    /** The streets of one neighbourhood, or null when this file has no entry for it (or cannot be read). */
    fun streets(osmType: String, osmId: Long): List<Street>? =
        if (index == null) null else read(StatsPackFormat.streetsEntry(osmType, osmId))?.let(StatsPackFormat::parseStreets)

    private fun read(entry: String): String? = runCatching {
        ZipFile(file).use { zip ->
            val e = zip.getEntry(entry) ?: return null
            zip.getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }.getOrNull()
}

/**
 * The neighbourhoods-and-streets files of every installed province pack. [files] says which files exist right now
 * (the screens pass `ProvincePacks::statsFiles`). Packs are opened lazily and remembered by file and modification time.
 */
class StatsPacks(private val files: () -> List<File>) {
    private val packs = HashMap<String, ZipStatsPack>()

    @Synchronized
    private fun all(): List<ZipStatsPack> = files().map { f -> packs.getOrPut("${f.path}:${f.lastModified()}") { ZipStatsPack(f) } }

    /** The neighbourhoods of the town [cityId] from the first pack that lists it, or null. */
    fun hoodsFor(cityId: Long): List<Boundary>? =
        all().firstNotNullOfOrNull { pack -> if (pack.index?.cities?.contains(cityId) == true) pack.hoods(cityId) else null }

    /** The streets of a neighbourhood from the first pack that has an entry for it, or null. */
    fun streetsFor(osmType: String, osmId: Long): List<Street>? =
        all().firstNotNullOfOrNull { pack -> pack.streets(osmType, osmId) }
}

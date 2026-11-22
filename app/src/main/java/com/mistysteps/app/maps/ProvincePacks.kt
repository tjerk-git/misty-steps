package com.mistysteps.app.maps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The map packs on this phone, in `<root>/<year>/<province id>/map.pmtiles` (root is `filesDir/maps/nl`).
 * What is installed comes from the files; "downloading" and "failed" are remembered while the app runs.
 * [version] ticks on every change so screens can reload; [installedVersion] only when the files change, for the map.
 */
class ProvincePacks(private val root: File) {
    private val transient = ConcurrentHashMap<String, PackState>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /**
     * Ticks only when the map files on disk change (a pack installed, removed or replaced), not on download progress.
     * The map reloads its whole style on this, so it must stay quiet while a download reports every 250 ms.
     */
    private val _installedVersion = MutableStateFlow(0)
    val installedVersion: StateFlow<Int> = _installedVersion

    fun packDir(year: Int, id: String) = File(root, "$year/$id")

    private fun mapPath(year: Int, id: String) = File(packDir(year, id), "map.pmtiles")

    /**
     * Which years of each province's map are on disk, read from the files once and kept until a pack is installed, replaced
     * or removed. The screens ask for every province's state on every download progress tick, on the main thread; listing
     * folders each time made every tick cost tens of milliseconds on a phone with slow storage.
     */
    private class Scan(val years: Map<String, List<Int>>, val withStats: Set<String>) // withStats: "<year>/<id>"

    @Volatile private var installed: Scan? = null

    /** How many times the folders were listed; for tests. */
    @Volatile internal var scans = 0
        private set

    private fun scanned(): Scan = installed ?: scan().also { installed = it }

    private fun installedYears(): Map<String, List<Int>> = scanned().years

    private fun scan(): Scan {
        scans++
        val found = HashMap<String, MutableList<Int>>()
        val withStats = HashSet<String>()
        for (yearDir in root.listFiles().orEmpty()) {
            val year = yearDir.name.toIntOrNull() ?: continue
            for (idDir in yearDir.listFiles().orEmpty()) {
                if (idDir.isDirectory && File(idDir, "map.pmtiles").isFile) {
                    found.getOrPut(idDir.name) { mutableListOf() } += year
                    if (File(idDir, STATS_FILE).isFile) withStats += "$year/${idDir.name}"
                }
            }
        }
        return Scan(found.mapValues { it.value.sortedDescending() }, withStats)
    }

    /** The files changed: list them again next time they are asked for. */
    private fun filesChanged() {
        installed = null
        _installedVersion.value++
    }

    private fun years(id: String): List<Int> = installedYears()[id].orEmpty()

    /** The newest installed year for [id], or null. */
    fun installedYear(id: String): Int? = years(id).firstOrNull()

    fun mapFile(id: String): File? = installedYear(id)?.let { mapPath(it, id) }

    /** True when the newest installed pack of [id] has its neighbourhoods-and-streets file. Cached like the rest. */
    fun hasStats(id: String): Boolean = installedYear(id)?.let { "$it/$id" in scanned().withStats } == true

    /** The neighbourhoods-and-streets files of the installed packs (newest year of each province, sorted by id). */
    fun statsFiles(): List<File> =
        installedYears().keys.sorted().filter(::hasStats).mapNotNull { id -> installedYear(id)?.let { File(packDir(it, id), STATS_FILE) } }

    /** The installed map files, one per province (its newest year), sorted by province id. */
    fun mapFiles(): List<File> {
        return installedYears().keys.sorted().mapNotNull { mapFile(it) }
    }

    fun state(entry: PackEntry): PackState {
        transient[entry.id]?.let { return it }
        val installed = installedYear(entry.id) ?: return PackState.NotDownloaded
        return if (entry.year > installed) PackState.UpdateAvailable(installed, entry.year) else PackState.Ready(installed)
    }

    fun markDownloading(id: String, bytes: Long, total: Long) = setTransient(id, PackState.Downloading(bytes, total))

    fun markFailed(id: String, reason: Reason) = setTransient(id, PackState.Failed(reason))

    fun clearTransient(id: String) {
        transient.remove(id)
        _version.value++
        filesChanged()
    }

    /** Deletes every year of [id]'s pack, partial downloads included. */
    fun remove(id: String) {
        transient.remove(id)
        root.listFiles().orEmpty().filter { it.name.toIntOrNull() != null }.forEach { File(it, id).deleteRecursively() }
        _version.value++
        filesChanged()
    }

    /** After a new year's pack is in place: delete the older ones. Does nothing until [year]'s file exists. */
    fun removeOlderThan(year: Int, id: String) {
        if (!mapPath(year, id).isFile) return
        years(id).filter { it < year }.forEach { packDir(it, id).deleteRecursively() }
        _version.value++
        filesChanged()
    }

    private fun setTransient(id: String, state: PackState) {
        transient[id] = state
        _version.value++
    }

    companion object {
        /** The file next to a pack's `map.pmtiles` that holds its neighbourhoods and streets. */
        const val STATS_FILE = "stats.zip"
    }
}

package com.mistysteps.app.maps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The map packs on this phone, in `<root>/<year>/<province id>/map.pmtiles` (root is `filesDir/maps/nl`).
 * What is installed comes from the files; "downloading" and "failed" are remembered while the app runs.
 * [version] ticks on every change so screens and the map can reload.
 */
class ProvincePacks(private val root: File) {
    private val transient = ConcurrentHashMap<String, PackState>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun packDir(year: Int, id: String) = File(root, "$year/$id")

    private fun mapPath(year: Int, id: String) = File(packDir(year, id), "map.pmtiles")

    private fun years(id: String): List<Int> =
        (root.listFiles().orEmpty().mapNotNull { it.name.toIntOrNull() }).filter { mapPath(it, id).isFile }.sortedDescending()

    /** The newest installed year for [id], or null. */
    fun installedYear(id: String): Int? = years(id).firstOrNull()

    fun mapFile(id: String): File? = installedYear(id)?.let { mapPath(it, id) }

    /** The installed map files, one per province (its newest year), sorted by province id. */
    fun mapFiles(): List<File> {
        val ids = root.listFiles().orEmpty().filter { it.name.toIntOrNull() != null }
            .flatMap { year -> year.listFiles().orEmpty().filter { it.isDirectory }.map { it.name } }
            .toSortedSet()
        return ids.mapNotNull { mapFile(it) }
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
    }

    /** Deletes every year of [id]'s pack, partial downloads included. */
    fun remove(id: String) {
        transient.remove(id)
        root.listFiles().orEmpty().filter { it.name.toIntOrNull() != null }.forEach { File(it, id).deleteRecursively() }
        _version.value++
    }

    /** After a new year's pack is in place: delete the older ones. Does nothing until [year]'s file exists. */
    fun removeOlderThan(year: Int, id: String) {
        if (!mapPath(year, id).isFile) return
        years(id).filter { it < year }.forEach { packDir(it, id).deleteRecursively() }
        _version.value++
    }

    private fun setTransient(id: String, state: PackState) {
        transient[id] = state
        _version.value++
    }
}

package com.mistysteps.app.stats

import android.content.Context
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.offline.CityIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AreaStat(
    val label: String,
    val name: String,
    val clearedM2: Double,
    /** Null when the border isn't known. */
    val totalM2: Double?,
) {
    val fraction: Double? get() = totalM2?.takeIf { it > 0 }?.let { (clearedM2 / it).coerceIn(0.0, 1.0) }
}

data class Stats(
    val clearedM2: Double,
    val distanceM: Double,
    val city: AreaStat?,
    val country: AreaStat?,
    val world: AreaStat,
    /** Set when city/country aren't known: a calm note, not an error. */
    val note: String?,
)

/** Your neighbourhood and how much of it is cleared. Streets are counted separately ([StatsRepository.streets]). */
data class NeighbourhoodStat(val area: AreaStat, val boundary: Boundary)

/** Result of looking up the neighbourhood: [stat] when found, otherwise maybe a [note] to show. */
data class NeighbourhoodResult(val stat: NeighbourhoodStat?, val note: String?)

/**
 * One per app, so borders parsed from disk stay in memory between visits to the stats page.
 *
 * Works without any network: town and country borders come from the index shipped with the app
 * ([CityIndex]), neighbourhoods and streets only from what earlier versions saved ([Neighbourhoods]).
 */
class StatsRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val hoods = Neighbourhoods(appContext)
    private val index: CityIndex? by lazy {
        runCatching { CityIndex.load(appContext) }.onFailure { android.util.Log.e("Stats", "index", it) }.getOrNull()
    }

    init {
        // City and country borders that earlier versions fetched from Nominatim; the index replaces them.
        for (dir in listOf("city", "country")) File(appContext.filesDir, "boundaries/$dir").deleteRecursively()
    }

    /** Cells from the last [compute], reused by [neighbourhood]. */
    @Volatile private var lastCells: Pair<Int, ClearedCells>? = null

    companion object {
        const val OUTSIDE = "City and country stats only cover the Netherlands for now."

        @Volatile private var instance: StatsRepository? = null

        fun get(context: Context): StatsRepository =
            instance ?: synchronized(this) {
                instance ?: StatsRepository(context.applicationContext).also { instance = it }
            }
    }

    /** [lat]/[lon]: where you are now, or null if unknown. */
    suspend fun compute(lat: Double?, lon: Double?): Stats = withContext(Dispatchers.IO) {
        val cells = ClearedCells.from(FogStore.loadAllPoints())
        lastCells = FogStore.version.value to cells
        val world = AreaStat("World", "Earth", cells.areaM2, WORLD_AREA_M2)

        var city: AreaStat? = null
        var country: AreaStat? = null
        var note: String? = null
        val idx = index
        if (lat == null || lon == null) {
            note = "Start wandering once so we know where you are."
        } else if (idx != null) {
            idx.cityAt(lat, lon)?.let { city = stat("City", it, cells) }
            if (idx.country.contains(lat, lon)) country = stat("Country", idx.country, cells)
            if (city == null && country == null) note = OUTSIDE
        }
        Stats(cells.areaM2, FogStore.distanceMeters.value, city, country, world, note)
    }

    /** Your neighbourhood, if an earlier version saved the neighbourhoods of your town. */
    suspend fun neighbourhood(lat: Double, lon: Double): NeighbourhoodResult = withContext(Dispatchers.IO) {
        val city = index?.cityAt(lat, lon) ?: return@withContext NeighbourhoodResult(null, null)
        val all = hoods.saved(city) ?: return@withContext NeighbourhoodResult(null, null)
        val hood = Neighbourhoods.pick(all, city, lat, lon) ?: return@withContext NeighbourhoodResult(null, null)
        val cells = lastCells?.takeIf { it.first == FogStore.version.value }?.second
            ?: ClearedCells.from(FogStore.loadAllPoints())
        NeighbourhoodResult(NeighbourhoodStat(stat("Neighbourhood", hood, cells), hood), null)
    }

    /** Street progress in [hood], or null if its streets were never saved on this phone. */
    suspend fun streets(hood: Boundary): StreetStats? = withContext(Dispatchers.IO) {
        val streets = hoods.streets(hood) ?: return@withContext null
        val pad = 0.001 // ~100 m, so streets on the edge see trails just outside
        val trail = FogStore.loadAllPoints().filter {
            it.lat in hood.minLat - pad..hood.maxLat + pad && it.lon in hood.minLon - pad..hood.maxLon + pad
        }
        val flat = DoubleArray(trail.size * 2).also { a ->
            trail.forEachIndexed { i, p -> a[2 * i] = p.lat; a[2 * i + 1] = p.lon }
        }
        StreetCoverage.compute(streets, flat) { la, lo -> hood.contains(la, lo) }
    }

    private fun stat(label: String, b: Boundary, cells: ClearedCells) = AreaStat(
        label = label,
        name = b.name,
        clearedM2 = if (b.hasArea) cells.areaInside(b) else 0.0,
        totalM2 = if (b.hasArea) b.areaM2 else null,
    )
}

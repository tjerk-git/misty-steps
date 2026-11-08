package com.mistysteps.app.stats

import android.content.Context
import com.mistysteps.app.data.FogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** Set when city/country couldn't be looked up. */
    val problem: String?,
)

/** Your neighbourhood and how much of it is cleared. Streets are counted separately ([StatsRepository.streets]). */
data class NeighbourhoodStat(val area: AreaStat, val boundary: Boundary)

/** Result of looking up the neighbourhood: [stat] when found, otherwise maybe a [problem] to show. */
data class NeighbourhoodResult(val stat: NeighbourhoodStat?, val problem: String?)

/** One per app, so borders parsed from disk stay in memory between visits to the stats page. */
class StatsRepository private constructor(context: Context) {
    private val boundaries = BoundaryProvider(context.applicationContext)
    private val hoods = Neighbourhoods(context.applicationContext)

    /** Cells from the last [compute], reused by [neighbourhood]. */
    @Volatile private var lastCells: Pair<Int, ClearedCells>? = null

    companion object {
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
        var problem: String? = null
        if (lat == null || lon == null) {
            problem = "Start wandering once so we know where you are."
        } else {
            try {
                boundaries.find(lat, lon, BoundaryProvider.Level.CITY)?.let {
                    city = stat("City", it, cells)
                }
                boundaries.find(lat, lon, BoundaryProvider.Level.COUNTRY)?.let {
                    country = stat("Country", it, cells)
                }
            } catch (e: Exception) {
                problem = "Couldn't look up city and country borders. Are you online?"
            }
        }
        Stats(cells.areaM2, FogStore.distanceMeters.value, city, country, world, problem)
    }

    /**
     * Your neighbourhood. Slower than [compute] the first time (it fetches the city's
     * neighbourhood borders), so the stats page shows it once it's ready.
     */
    suspend fun neighbourhood(lat: Double, lon: Double): NeighbourhoodResult = withContext(Dispatchers.IO) {
        val hood = try {
            val city = boundaries.find(lat, lon, BoundaryProvider.Level.CITY)
                ?: return@withContext NeighbourhoodResult(null, null)
            hoods.find(city, lat, lon) ?: return@withContext NeighbourhoodResult(null, null)
        } catch (e: Exception) {
            return@withContext NeighbourhoodResult(null, "Couldn't look up your neighbourhood. Are you online?")
        }
        val cells = lastCells?.takeIf { it.first == FogStore.version.value }?.second
            ?: ClearedCells.from(FogStore.loadAllPoints())
        NeighbourhoodResult(NeighbourhoodStat(stat("Neighbourhood", hood, cells), hood), null)
    }

    /** Street progress in [hood]. Blocking network the first time; throws if OpenStreetMap can't be reached. */
    suspend fun streets(hood: Boundary): StreetStats = withContext(Dispatchers.IO) {
        val streets = hoods.streets(hood)
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

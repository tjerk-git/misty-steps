package com.mistysteps.app.stats

import com.mistysteps.app.data.PointStore
import com.mistysteps.app.data.Session
import com.mistysteps.app.data.SessionStore
import com.mistysteps.app.offline.CityIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The stats counted from what is stored on the device: the cleared area from every spot, the distance from the walks
 * and, with a [CityIndex], your town and country. No neighbourhood or street data: those need files an earlier
 * Android version saved, so [neighbourhood] and [streets] have nothing to say here.
 */
class LocalStatsSource(
    private val points: PointStore,
    private val walks: SessionStore,
    /** The country index; loaded when first needed (it is a few MB to read). */
    private val index: Lazy<CityIndex?>,
) : StatsSource {

    override suspend fun compute(lat: Double?, lon: Double?): Stats = withContext(Dispatchers.Default) {
        val cells = ClearedCells.from(points.loadAll())
        val world = AreaStat(AreaKind.WORLD, null, cells.areaM2, WORLD_AREA_M2)
        var city: AreaStat? = null
        var country: AreaStat? = null
        var note: StatsNote? = null
        val idx = index.value
        if (lat == null || lon == null) {
            note = StatsNote.UNKNOWN_PLACE
        } else if (idx != null) {
            idx.cityAt(lat, lon)?.let { city = stat(AreaKind.CITY, it, cells) }
            if (idx.country.contains(lat, lon)) country = stat(AreaKind.COUNTRY, idx.country, cells)
            if (city == null && country == null) note = StatsNote.OUTSIDE
        }
        Stats(cells.areaM2, walks.loadSessions(null).sumOf { it.distanceM }, city, country, world, note)
    }

    override suspend fun neighbourhood(lat: Double, lon: Double) = NeighbourhoodResult(null, null)

    override suspend fun streets(hood: Boundary): StreetStats? = null

    override suspend fun sessions(): List<Session> = withContext(Dispatchers.Default) { walks.loadSessions(RECENT) }

    private fun stat(kind: AreaKind, b: Boundary, cells: ClearedCells) = AreaStat(
        kind = kind,
        name = b.name,
        clearedM2 = if (b.hasArea) cells.areaInside(b) else 0.0,
        totalM2 = if (b.hasArea) b.areaM2 else null,
    )

    private companion object {
        const val RECENT = 30
    }
}

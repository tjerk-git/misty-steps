package com.mistysteps.app.stats

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.area_city
import com.mistysteps.app.resources.area_country
import com.mistysteps.app.resources.area_neighbourhood
import com.mistysteps.app.resources.area_world
import com.mistysteps.app.resources.stats_note_outside
import com.mistysteps.app.resources.stats_note_unknown
import org.jetbrains.compose.resources.StringResource

/** What an [AreaStat] covers. Its label is shown above the name. */
enum class AreaKind(val label: StringResource) {
    WORLD(Res.string.area_world),
    COUNTRY(Res.string.area_country),
    CITY(Res.string.area_city),
    NEIGHBOURHOOD(Res.string.area_neighbourhood),
}

/** A calm note shown instead of city and country stats. */
enum class StatsNote(val text: StringResource) {
    OUTSIDE(Res.string.stats_note_outside),
    UNKNOWN_PLACE(Res.string.stats_note_unknown),
}

data class AreaStat(
    val kind: AreaKind,
    /** Null for the world: it's shown as "Earth". */
    val name: String?,
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
    val note: StatsNote?,
)

/** Your neighbourhood and how much of it is cleared. Streets are counted separately ([StatsRepository.streets]). */
data class NeighbourhoodStat(val area: AreaStat, val boundary: Boundary)

/** Result of looking up the neighbourhood: [stat] when found, otherwise maybe a [note] to show. */
data class NeighbourhoodResult(val stat: NeighbourhoodStat?, val note: StatsNote?)

/** Where the stats page gets its numbers; each platform counts them its own way. Suspend calls do their own threading. */
interface StatsSource {
    suspend fun compute(lat: Double?, lon: Double?): Stats

    suspend fun neighbourhood(lat: Double, lon: Double): NeighbourhoodResult

    /** Streets of [hood] and how many you walked; null when street data isn't there. */
    suspend fun streets(hood: Boundary): StreetStats?

    /** Recent walks, newest first. */
    suspend fun sessions(): List<com.mistysteps.app.data.Session>
}

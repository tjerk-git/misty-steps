package com.mistysteps.app.stats

import android.content.Context
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The stats page's numbers on Android: [StatsRepository] and the walk log in [FogStore]. */
class AndroidStatsSource(context: Context) : StatsSource {
    private val repo = StatsRepository.get(context)

    override suspend fun compute(lat: Double?, lon: Double?): Stats = repo.compute(lat, lon)

    override suspend fun neighbourhood(lat: Double, lon: Double): NeighbourhoodResult = repo.neighbourhood(lat, lon)

    override suspend fun streets(hood: Boundary): StreetStats? = repo.streets(hood)

    override suspend fun sessions(): List<Session> = withContext(Dispatchers.IO) { FogStore.loadSessions() }
}

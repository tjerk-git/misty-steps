package com.mistysteps.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.window.ComposeUIViewController
import com.mistysteps.app.data.Session
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.stats.AreaKind
import com.mistysteps.app.stats.AreaStat
import com.mistysteps.app.stats.Boundary
import com.mistysteps.app.stats.NeighbourhoodResult
import com.mistysteps.app.stats.Stats
import com.mistysteps.app.stats.StatsSource
import com.mistysteps.app.stats.StreetStats
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController

/** Made-up numbers, so the shared stats page can be seen on iOS before the iOS app counts real ones. */
private object DemoStatsSource : StatsSource {
    override suspend fun compute(lat: Double?, lon: Double?) = Stats(
        clearedM2 = 1_850_000.0,
        distanceM = 23_400.0,
        city = AreaStat(AreaKind.CITY, "Leeuwarden", 1_850_000.0, 74_000_000.0),
        country = AreaStat(AreaKind.COUNTRY, "Netherlands", 1_850_000.0, 41_500_000_000.0),
        world = AreaStat(AreaKind.WORLD, null, 1_850_000.0, 510_000_000_000_000.0),
        note = null,
    )

    override suspend fun neighbourhood(lat: Double, lon: Double) = NeighbourhoodResult(null, null)

    override suspend fun streets(hood: Boundary): StreetStats? = null

    override suspend fun sessions(): List<Session> = listOf(
        Session(1_794_950_000_000, 1_794_950_000_000, 1_794_951_900_000, 3_200.0, 160, null, null, 420_000.0),
        Session(1_794_860_000_000, 1_794_860_000_000, 1_794_861_100_000, 1_900.0, 95, null, null, 150_000.0),
    )
}

/** The shared stats page with demo numbers; the cards that need the platform are placeholders. */
fun statsDemoViewController(versionName: String, onClose: () -> Unit): UIViewController = ComposeUIViewController {
    MistyTheme {
        StatsScreen(
            source = DemoStatsSource,
            pins = MutableStateFlow(emptyList<Pin>()),
            here = 53.2012 to 5.7936,
            versionName = versionName,
            showBattery = false,
            slots = StatsSlots(
                mapsCard = { Text("(maps card: iOS later)") },
                backupCard = { _ -> Text("(backup card: iOS later)") },
                usageCard = { UsageCountsCard(optedIn = false, onOptedInChange = {}, versionName = versionName) },
                onFeedback = {},
            ),
            onClose = onClose,
            onOpenWalk = {},
            onOpenAbout = {},
            onOpenPin = {},
        )
    }
}

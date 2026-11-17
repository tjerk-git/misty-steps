package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.stats.AndroidStatsSource

/** The shared stats page on Android: the numbers from [AndroidStatsSource], plus the cards that need files and downloads. */
@Composable
fun AndroidStatsScreen(
    here: Pair<Double, Double>?,
    onClose: () -> Unit,
    onOpenWalk: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPin: (Long) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val context = LocalContext.current
    val source = remember { AndroidStatsSource(context) }
    val catalog by rememberCatalog()
    StatsScreen(
        source = source,
        pins = PinStore.pins,
        here = here,
        versionName = BuildConfig.VERSION_NAME,
        showBattery = BatteryLog.enabled,
        slots = StatsSlots(
            mapsCard = { YourMapsCard(catalog, onOpenPicker) },
            backupCard = { onImported -> BackupCard(onImported = onImported) },
            usageCard = { UsageCountsSwitch() },
            onFeedback = { sendFeedback(context) },
        ),
        onClose = onClose,
        onOpenWalk = onOpenWalk,
        onOpenAbout = onOpenAbout,
        onOpenPin = onOpenPin,
    )
}

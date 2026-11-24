package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.stats.AndroidStatsSource

/** The shared stats page on Android: the numbers from [AndroidStatsSource]. The settings are on the "You" page. */
@Composable
fun AndroidStatsScreen(
    here: Pair<Double, Double>?,
    onClose: () -> Unit,
    onOpenWalk: (Long) -> Unit,
    onOpenPin: (Long) -> Unit,
) {
    val context = LocalContext.current
    val source = remember { AndroidStatsSource(context) }
    StatsScreen(
        source = source,
        pins = PinStore.pins,
        here = here,
        showBattery = BatteryLog.enabled,
        onClose = onClose,
        onOpenWalk = onOpenWalk,
        onOpenPin = onOpenPin,
    )
}

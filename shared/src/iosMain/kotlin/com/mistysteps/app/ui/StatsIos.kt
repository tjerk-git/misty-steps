package com.mistysteps.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.mistysteps.app.data.PointStore
import com.mistysteps.app.data.SessionStore
import com.mistysteps.app.data.BackupHost
import com.mistysteps.app.maps.PackEntry
import com.mistysteps.app.maps.PackManager
import com.mistysteps.app.offline.CityIndex
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.stats.LocalStatsSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfFile
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** The country index shipped in the app bundle (`nl-index.json`), or null when it cannot be read. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun bundledCityIndex(): CityIndex? {
    val path = NSBundle.mainBundle.pathForResource("nl-index", "json") ?: return null
    val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return null
    return runCatching { CityIndex.parse(text) }.getOrNull()
}

/**
 * The stats page counted from the trails on this device. [hereLat]/[hereLon] are where you are now (or null);
 * the maps card opens the province picker through [onOpenPicker].
 */
fun statsViewController(
    points: PointStore,
    sessions: SessionStore,
    hereLat: Double?,
    hereLon: Double?,
    versionName: String,
    pins: StateFlow<List<Pin>>,
    backup: BackupHost,
    packs: PackManager,
    provinces: List<PackEntry>?,
    onOpenPicker: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenWalk: (Long) -> Unit,
    onClose: () -> Unit,
): UIViewController = ComposeUIViewController {
    MistyTheme {
        val source = remember { LocalStatsSource(points, sessions, lazy { bundledCityIndex() }) }
        val defaults = NSUserDefaults.standardUserDefaults
        var optedIn by remember { mutableStateOf(defaults.boolForKey("analytics_opt_in")) }
        StatsScreen(
            source = source,
            pins = pins,
            here = if (hereLat != null && hereLon != null) hereLat to hereLon else null,
            versionName = versionName,
            showBattery = false,
            slots = StatsSlots(
                mapsCard = { YourMapsCard(provinces, packs, onOpenPicker) },
                backupCard = { onImported -> BackupCard(backup, onImported) },
                usageCard = {
                    UsageCountsCard(
                        optedIn = optedIn,
                        onOptedInChange = {
                            optedIn = it
                            defaults.setBool(it, forKey = "analytics_opt_in")
                            defaults.setBool(true, forKey = "analytics_asked")
                        },
                        versionName = versionName,
                    )
                },
                onFeedback = {
                    val mail = "mailto:$FEEDBACK_EMAIL?subject=" + com.mistysteps.app.util.percentEncode("Misty Steps feedback")
                    NSURL.URLWithString(mail)?.let { UIApplication.sharedApplication.openURL(it) }
                },
            ),
            onClose = onClose,
            onOpenWalk = onOpenWalk,
            onOpenAbout = onOpenAbout,
            onOpenPin = {},
        )
    }
}

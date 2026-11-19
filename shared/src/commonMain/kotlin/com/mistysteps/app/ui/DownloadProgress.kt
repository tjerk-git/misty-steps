package com.mistysteps.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.maps.DownloadMeter
import com.mistysteps.app.maps.PackEntry
import com.mistysteps.app.maps.PackManager
import com.mistysteps.app.maps.PackState
import com.mistysteps.app.maps.formatSize
import com.mistysteps.app.maps.formatSpeed
import com.mistysteps.app.maps.percent
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.download_detail
import com.mistysteps.app.resources.download_eta_min
import com.mistysteps.app.resources.download_eta_sec
import com.mistysteps.app.resources.download_pill
import com.mistysteps.app.resources.download_speed
import com.mistysteps.app.resources.download_starting
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

private val clock = TimeSource.Monotonic.markNow()

/** Measures one download's speed while it is on screen; each call with new bytes is one sample. */
@Composable
private fun rememberMeter(id: String, state: PackState.Downloading): DownloadMeter {
    val meter = remember(id) { DownloadMeter() }
    meter.update(clock.elapsedNow().inWholeMilliseconds, state.bytes)
    return meter
}

/** "12 MB of 58 MB, 3.2 MB/s, about 15 s left", or "Starting…" until the speed is known. */
@Composable
private fun detailText(state: PackState.Downloading, meter: DownloadMeter): String {
    val speed = meter.bytesPerSecond
    if (speed <= 0) return stringResource(Res.string.download_starting)
    val perSecond = stringResource(Res.string.download_speed, formatSpeed(speed))
    val left = meter.secondsLeft(state.bytes, state.total)
    val eta = when {
        left == null -> ""
        left < 90 -> " · " + stringResource(Res.string.download_eta_sec, left)
        else -> " · " + stringResource(Res.string.download_eta_min, (left + 59) / 60)
    }
    return stringResource(Res.string.download_detail, formatSize(state.bytes), formatSize(state.total), perSecond) + eta
}

/** A progress bar with the percentage and the detail line under it: the picker's rows and the map's pill use this. */
@Composable
fun DownloadProgress(id: String, state: PackState.Downloading, modifier: Modifier = Modifier) {
    val meter = rememberMeter(id, state)
    Column(modifier) {
        LinearProgressIndicator(
            progress = { if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Ink,
            trackColor = Color(0xFFD5DAE8),
        )
        Spacer(Modifier.height(4.dp))
        Text("${percent(state)}% · " + detailText(state, meter), color = Dusk, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** The province that is downloading now, if any. */
fun downloading(provinces: List<PackEntry>?, packs: PackManager): Pair<PackEntry, PackState.Downloading>? {
    for (entry in provinces.orEmpty()) {
        val state = packs.state(entry)
        if (state is PackState.Downloading) return entry to state
    }
    return null
}

/** A small card over the map while a province downloads, so leaving the picker does not leave you guessing. Tap opens the maps. */
@Composable
fun DownloadPill(provinces: List<PackEntry>?, packs: PackManager, onClick: () -> Unit, modifier: Modifier = Modifier) {
    packs.version.collectAsState().value // read again on every progress step
    val (entry, state) = downloading(provinces, packs) ?: return
    Surface(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Frost,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(Res.string.download_pill, entry.name), color = Ink, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            DownloadProgress(entry.id, state)
        }
    }
}

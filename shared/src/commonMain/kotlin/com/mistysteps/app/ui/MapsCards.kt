package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.data_download
import com.mistysteps.app.resources.maps_add
import com.mistysteps.app.resources.maps_streets_ready
import com.mistysteps.app.resources.maps_data_year
import com.mistysteps.app.resources.maps_downloading
import com.mistysteps.app.resources.maps_failed
import com.mistysteps.app.resources.maps_no_space
import com.mistysteps.app.resources.maps_remove
import com.mistysteps.app.resources.maps_update
import com.mistysteps.app.resources.maps_update_app
import com.mistysteps.app.resources.not_now
import com.mistysteps.app.resources.offer_card
import com.mistysteps.app.resources.picker_later
import com.mistysteps.app.resources.picker_offline
import org.jetbrains.compose.resources.stringResource
import com.mistysteps.app.maps.PackEntry
import com.mistysteps.app.maps.PackManager
import com.mistysteps.app.maps.percent
import com.mistysteps.app.maps.PackState
import com.mistysteps.app.maps.Province
import com.mistysteps.app.maps.Reason
import com.mistysteps.app.maps.formatSize

/** "No map of Friesland yet. Download (62 MB)" with Download and Not now. */
@Composable
fun OfferCard(province: Province, entry: PackEntry, onDownload: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Frost, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(Res.string.offer_card, province.name, formatSize(entry.totalBytes)), color = Ink, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNotNow) { Text(stringResource(Res.string.not_now), color = Dusk) }
                TextButton(onClick = onDownload) { Text(stringResource(Res.string.data_download), color = Ember) }
            }
        }
    }
}

/** The provinces on this phone with their state, Update and Remove, and a button to add more. */
@Composable
fun YourMapsCard(provinces: List<PackEntry>?, packs: PackManager, onAdd: () -> Unit) {
    packs.version.collectAsState().value // recompose on every change
    val needsAppUpdate by packs.needsAppUpdate.collectAsState()
    val rows = provinces.orEmpty().map { it to packs.state(it) }.filter { it.second !is PackState.NotDownloaded }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            for ((entry, state) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Ink, fontSize = 16.sp)
                        Text(statusText(state, entry), color = if (state is PackState.Failed) Ember else Dusk, fontSize = 12.sp)
                        if ((state is PackState.Ready || state is PackState.UpdateAvailable) && packs.hasStats(entry.id)) {
                            Text(stringResource(Res.string.maps_streets_ready), color = Dusk, fontSize = 12.sp)
                        }
                    }
                    if (state is PackState.UpdateAvailable) {
                        TextButton(onClick = { packs.download(entry, unmeteredOnly = false) }) {
                            Text(stringResource(Res.string.maps_update), color = Ink)
                        }
                    }
                    TextButton(onClick = { packs.remove(entry.id) }) {
                        Text(stringResource(Res.string.maps_remove), color = Ember)
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(if (provinces == null) Res.string.picker_offline else Res.string.picker_later),
                    color = Dusk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (needsAppUpdate) {
                Text(stringResource(Res.string.maps_update_app), color = Ember, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (provinces != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.maps_add), color = Ink)
                }
            }
        }
    }
}

@Composable
private fun statusText(state: PackState, entry: PackEntry): String = when (state) {
    is PackState.Ready -> stringResource(Res.string.maps_data_year, state.year)
    is PackState.UpdateAvailable -> stringResource(Res.string.maps_data_year, state.installedYear)
    is PackState.Downloading -> stringResource(Res.string.maps_downloading, "${percent(state)}%")
    is PackState.Failed -> stringResource(if (state.reason == Reason.NO_SPACE) Res.string.maps_no_space else Res.string.maps_failed)
    PackState.NotDownloaded -> formatSize(entry.totalBytes)
}

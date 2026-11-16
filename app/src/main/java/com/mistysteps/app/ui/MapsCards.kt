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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.R
import com.mistysteps.app.maps.Catalog
import com.mistysteps.app.maps.MapsGraph
import com.mistysteps.app.maps.PackDownloads
import com.mistysteps.app.maps.PackEntry
import com.mistysteps.app.maps.PackState
import com.mistysteps.app.maps.Province
import com.mistysteps.app.maps.Reason
import com.mistysteps.app.maps.formatSize
import androidx.compose.ui.graphics.Color

/** The catalog: the cached copy straight away, then a refresh when online. Null while there is none. */
@Composable
fun rememberCatalog(): State<Catalog?> = produceState(MapsGraph.catalogStore.cached()) {
    value = MapsGraph.catalogStore.refresh() ?: value
}

/** "No map of Friesland yet. Download (62 MB)" with Download and Not now. */
@Composable
fun OfferCard(province: Province, entry: PackEntry, onDownload: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Frost, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.offer_card, province.name, formatSize(entry.map.bytes)), color = Ink, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now), color = Dusk) }
                TextButton(onClick = onDownload) { Text(stringResource(R.string.data_download), color = Ember) }
            }
        }
    }
}

/** The provinces on this phone with their state, Update and Remove, and a button to add more. */
@Composable
fun YourMapsCard(catalog: Catalog?, onAdd: () -> Unit) {
    val context = LocalContext.current
    val packs = MapsGraph.packs
    packs.version.collectAsState().value // recompose on every change
    val needsAppUpdate by MapsGraph.catalogStore.needsAppUpdate.collectAsState()
    val rows = catalog?.provinces.orEmpty().map { it to packs.state(it) }.filter { it.second !is PackState.NotDownloaded }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            for ((entry, state) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Ink, fontSize = 16.sp)
                        Text(statusText(state, entry), color = if (state is PackState.Failed) Ember else Dusk, fontSize = 12.sp)
                    }
                    if (state is PackState.UpdateAvailable) {
                        TextButton(onClick = { PackDownloads.enqueue(context, entry, unmeteredOnly = false) }) {
                            Text(stringResource(R.string.maps_update), color = Ink)
                        }
                    }
                    TextButton(onClick = { PackDownloads.cancel(context, entry.id); packs.remove(entry.id) }) {
                        Text(stringResource(R.string.maps_remove), color = Ember)
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(if (catalog == null) R.string.picker_offline else R.string.picker_later),
                    color = Dusk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (needsAppUpdate) {
                Text(stringResource(R.string.maps_update_app), color = Ember, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (catalog != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.maps_add), color = Ink)
                }
            }
        }
    }
}

@Composable
private fun statusText(state: PackState, entry: PackEntry): String = when (state) {
    is PackState.Ready -> stringResource(R.string.maps_data_year, state.year)
    is PackState.UpdateAvailable -> stringResource(R.string.maps_data_year, state.installedYear)
    is PackState.Downloading -> stringResource(R.string.maps_downloading, percent(state))
    is PackState.Failed -> stringResource(if (state.reason == Reason.NO_SPACE) R.string.maps_no_space else R.string.maps_failed)
    PackState.NotDownloaded -> formatSize(entry.map.bytes)
}

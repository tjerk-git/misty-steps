package com.mistysteps.app.ui

import android.content.Context
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.R
import com.mistysteps.app.maps.Catalog
import com.mistysteps.app.maps.DownloadPolicy
import com.mistysteps.app.maps.PackDownloads
import com.mistysteps.app.maps.PackState
import com.mistysteps.app.maps.PickerState
import com.mistysteps.app.maps.ProvincePacks
import com.mistysteps.app.maps.formatSize

/**
 * "Which provinces do you want to explore?" A check box and size per province, the total, Download and Skip.
 * [preselected] ticks provinces up front (an existing user's most-walked province); [existingUser] adds the
 * line about the online map being gone. Mobile data asks first for downloads of 50 MB or more.
 */
@Composable
fun ProvincePickerScreen(
    catalog: Catalog?,
    packs: ProvincePacks,
    preselected: Set<String>,
    existingUser: Boolean,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    packs.version.collectAsState().value // recompose when a download starts or finishes
    // Saved as a list: it survives rotation and the app being killed in the background.
    var ticked by rememberSaveable { mutableStateOf(preselected.toList()) }
    var askAboutData by rememberSaveable { mutableStateOf(false) }
    val state = PickerState(catalog?.provinces.orEmpty(), ticked.toSet())
    val toDownload = catalog?.let { state.toDownload(packs) }.orEmpty()
    val downloadBytes = toDownload.sumOf { it.map.bytes }

    fun start(unmeteredOnly: Boolean) {
        toDownload.forEach { PackDownloads.enqueue(context, it, unmeteredOnly) }
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.picker_title),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = Ink,
            )
            if (existingUser) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.picker_existing), fontSize = 14.sp, lineHeight = 20.sp, color = Dusk)
            }
            Spacer(Modifier.height(16.dp))

            if (catalog == null) {
                Text(stringResource(R.string.picker_offline), fontSize = 16.sp, color = Dusk)
                Spacer(Modifier.weight(1f))
            } else {
                Surface(
                    Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Frost,
                    shadowElevation = 2.dp,
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        items(state.options.size) { i ->
                            val entry = state.options[i]
                            val packState = packs.state(entry)
                            val busy = packState is PackState.Ready || packState is PackState.Downloading
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        ticked = state.toggle(entry.id).selected.toList()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.id in state.selected || busy,
                                    onCheckedChange = null,
                                    enabled = !busy,
                                    colors = CheckboxDefaults.colors(checkedColor = Ink),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, color = Ink, fontSize = 16.sp)
                                    val note = when (packState) {
                                        is PackState.Ready -> stringResource(R.string.maps_data_year, packState.year)
                                        is PackState.Downloading -> stringResource(R.string.maps_downloading, percent(packState))
                                        else -> null
                                    }
                                    if (note != null) Text(note, color = Dusk, fontSize = 12.sp)
                                }
                                Text(formatSize(entry.map.bytes), color = Dusk, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (catalog != null) {
                Text(
                    stringResource(R.string.picker_total, formatSize(state.totalBytes)),
                    color = Ink,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val metered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
                        if (DownloadPolicy.needsDataWarning(metered, downloadBytes)) askAboutData = true else start(unmeteredOnly = false)
                    },
                    enabled = toDownload.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                ) {
                    Text(pluralStringResource(R.plurals.picker_download, toDownload.size, toDownload.size), fontSize = 16.sp)
                }
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.picker_skip), color = Dusk)
            }
            Text(
                stringResource(R.string.picker_later),
                color = Dusk,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (askAboutData) {
        AlertDialog(
            onDismissRequest = { askAboutData = false },
            text = { Text(stringResource(R.string.data_warning, formatSize(downloadBytes))) },
            confirmButton = {
                TextButton(onClick = { askAboutData = false; start(unmeteredOnly = false) }) {
                    Text(stringResource(R.string.data_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { askAboutData = false; start(unmeteredOnly = true) }) {
                    Text(stringResource(R.string.data_wait_wifi))
                }
            },
        )
    }
}

internal fun percent(state: PackState.Downloading): Int =
    if (state.total > 0) (state.bytes * 100 / state.total).toInt().coerceIn(0, 100) else 0

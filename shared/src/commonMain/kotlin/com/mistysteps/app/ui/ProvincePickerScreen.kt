package com.mistysteps.app.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.data_download
import com.mistysteps.app.resources.data_wait_wifi
import com.mistysteps.app.resources.data_warning
import com.mistysteps.app.resources.maps_data_year
import com.mistysteps.app.resources.maps_failed
import com.mistysteps.app.resources.picker_continue
import com.mistysteps.app.resources.picker_keep_going
import com.mistysteps.app.resources.picker_existing
import com.mistysteps.app.resources.picker_later
import com.mistysteps.app.resources.picker_offline
import com.mistysteps.app.resources.picker_skip
import com.mistysteps.app.resources.picker_title
import com.mistysteps.app.resources.picker_total
import com.mistysteps.app.resources.picker_download
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import com.mistysteps.app.maps.DownloadPolicy
import com.mistysteps.app.maps.PackManager
import com.mistysteps.app.maps.PackEntry
import com.mistysteps.app.maps.PackState
import com.mistysteps.app.maps.PickerState
import com.mistysteps.app.maps.formatSize

/**
 * "Which provinces do you want to explore?" A check box and size per province, the total, Download and Skip.
 * [preselected] ticks provinces up front (an existing user's most-walked province); [existingUser] adds the
 * line about the online map being gone. Mobile data asks first for downloads of 50 MB or more.
 */
@Composable
fun ProvincePickerScreen(
    provinces: List<PackEntry>?,
    packs: PackManager,
    preselected: Set<String>,
    existingUser: Boolean,
    onDone: () -> Unit,
) {
    PlatformBackHandler(onBack = onDone)
    packs.version.collectAsState().value // recompose when a download starts or finishes
    // Saved as a list: it survives rotation and the app being killed in the background.
    var ticked by rememberSaveable { mutableStateOf(preselected.toList()) }
    var askAboutData by rememberSaveable { mutableStateOf(false) }
    val state = PickerState(provinces.orEmpty(), ticked.toSet())
    val toDownload = if (provinces != null) state.toDownload(packs::state) else emptyList()
    val downloadBytes = toDownload.sumOf { it.totalBytes }

    // After Download the page stays, showing each province's progress; Continue then leaves it while the downloads go on.
    var started by rememberSaveable { mutableStateOf(false) }
    val inProgress = started || state.options.any { packs.state(it) is PackState.Downloading }

    fun start(unmeteredOnly: Boolean) {
        toDownload.forEach { packs.download(it, unmeteredOnly) }
        started = true
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
                stringResource(Res.string.picker_title),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = Ink,
            )
            if (existingUser) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.picker_existing), fontSize = 14.sp, lineHeight = 20.sp, color = Dusk)
            }
            Spacer(Modifier.height(16.dp))

            if (provinces == null) {
                Text(stringResource(Res.string.picker_offline), fontSize = 16.sp, color = Dusk)
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
                                    when (packState) {
                                        is PackState.Ready -> Text(stringResource(Res.string.maps_data_year, packState.year), color = Dusk, fontSize = 12.sp)
                                        is PackState.Downloading -> DownloadProgress(entry.id, packState, Modifier.padding(end = 12.dp))
                                        is PackState.Failed -> Text(stringResource(Res.string.maps_failed), color = Ember, fontSize = 12.sp)
                                        else -> {}
                                    }
                                }
                                Text(formatSize(entry.totalBytes), color = Dusk, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (provinces != null) {
                Text(
                    stringResource(Res.string.picker_total, formatSize(state.totalBytes)),
                    color = Ink,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (inProgress) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                    ) {
                        Text(stringResource(Res.string.picker_continue), fontSize = 16.sp)
                    }
                    Text(
                        stringResource(Res.string.picker_keep_going),
                        color = Dusk,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                } else {
                    Button(
                        onClick = {
                            if (DownloadPolicy.needsDataWarning(packs.isMetered(), downloadBytes)) askAboutData = true else start(unmeteredOnly = false)
                        },
                        enabled = toDownload.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                    ) {
                        Text(pluralStringResource(Res.plurals.picker_download, toDownload.size, toDownload.size), fontSize = 16.sp)
                    }
                }
            }
            if (!inProgress) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.picker_skip), color = Dusk)
                }
            }
            Text(
                stringResource(Res.string.picker_later),
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
            text = { Text(stringResource(Res.string.data_warning, formatSize(downloadBytes))) },
            confirmButton = {
                TextButton(onClick = { askAboutData = false; start(unmeteredOnly = false) }) {
                    Text(stringResource(Res.string.data_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { askAboutData = false; start(unmeteredOnly = true) }) {
                    Text(stringResource(Res.string.data_wait_wifi))
                }
            },
        )
    }
}


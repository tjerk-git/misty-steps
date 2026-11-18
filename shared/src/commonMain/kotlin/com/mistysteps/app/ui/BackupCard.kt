package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.data.BackupCounts
import com.mistysteps.app.data.BackupFailure
import com.mistysteps.app.data.BackupHost
import com.mistysteps.app.data.PendingImport
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.backup_add_body
import com.mistysteps.app.resources.backup_add_button
import com.mistysteps.app.resources.backup_add_failed
import com.mistysteps.app.resources.backup_add_title
import com.mistysteps.app.resources.backup_added
import com.mistysteps.app.resources.backup_counts
import com.mistysteps.app.resources.backup_export
import com.mistysteps.app.resources.backup_import
import com.mistysteps.app.resources.backup_intro
import com.mistysteps.app.resources.backup_pins
import com.mistysteps.app.resources.backup_points
import com.mistysteps.app.resources.backup_read_failed
import com.mistysteps.app.resources.backup_save_failed
import com.mistysteps.app.resources.backup_saved
import com.mistysteps.app.resources.backup_stop_to_import
import com.mistysteps.app.resources.backup_title
import com.mistysteps.app.resources.backup_walks
import com.mistysteps.app.resources.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Save all your trails to a file, or add trails from a file: for backups, a new phone, or moving between installs.
 * The [host] does the file picking and the storing; this card shows busy, results and the "add these trails?" question.
 */
@Composable
fun BackupCard(host: BackupHost, onImported: () -> Unit) {
    val scope = rememberCoroutineScope()
    val recording by host.recording.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }

    /** Runs [work], keeping the buttons off meanwhile, and shows [failed] when it throws. */
    fun run(failed: suspend () -> String, work: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = (e as? BackupFailure)?.message ?: failed()
            }
            busy = false
        }
    }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(Res.string.backup_title).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.backup_intro), color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        run({ getString(Res.string.backup_save_failed) }) {
                            host.export()?.let { message = getString(Res.string.backup_saved, backupCountsText(it)) }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text(stringResource(Res.string.backup_export)) }
                OutlinedButton(
                    onClick = { run({ getString(Res.string.backup_read_failed) }) { pending = host.pickImport() } },
                    enabled = !busy && !recording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text(stringResource(Res.string.backup_import)) }
            }
            if (recording) {
                Text(stringResource(Res.string.backup_stop_to_import), color = Dusk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            message?.let { Text(it, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }

    pending?.let { backup ->
        val counts by produceState("", backup) { value = backupCountsText(backup.counts) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(Res.string.backup_add_title)) },
            text = { Text(stringResource(Res.string.backup_add_body, formatDate(backup.exportedAt), counts)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    run({ getString(Res.string.backup_add_failed) }) {
                        val added = host.confirmImport(backup)
                        onImported()
                        message = getString(Res.string.backup_added, backupCountsText(added))
                    }
                }) { Text(stringResource(Res.string.backup_add_button)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** "1.662 points, 3 walks and 1 pin": what a backup holds, in the phone's language and number format. */
suspend fun backupCountsText(c: BackupCounts): String {
    fun n(v: Int) = formatNumber(v.toDouble(), 0, grouping = true)
    return getString(
        Res.string.backup_counts,
        getPluralString(Res.plurals.backup_points, c.points, n(c.points)),
        getPluralString(Res.plurals.backup_walks, c.walks, n(c.walks)),
        getPluralString(Res.plurals.backup_pins, c.pins, n(c.pins)),
    )
}

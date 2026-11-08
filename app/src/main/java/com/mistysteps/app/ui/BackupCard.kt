package com.mistysteps.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.Backup
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.record.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Save all your trails to a file, or add trails from a file: for backups, a new phone, or moving
 * between the F-Droid app and the Dev build. Uses Android's file picker, so no extra permissions.
 */
@Composable
fun BackupCard(onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recording by RecordingService.recording.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Backup?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val backup = FogStore.exportBackup(BuildConfig.VERSION_NAME, Characters.selected.value?.id)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backup.toJson().toByteArray()) }
                    backup.points.size
                }
                "Saved ${NumberFormat.getIntegerInstance().format(count)} spots"
            } catch (e: Exception) {
                "Couldn't save the file"
            }
            busy = false
            toast(msg)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    Backup.fromJson(text)
                }
            } catch (e: IllegalArgumentException) {
                toast(e.message ?: "This file isn't a Misty Steps backup.")
            } catch (e: Exception) {
                toast("Couldn't read the file")
            }
            busy = false
        }
    }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text("YOUR TRAILS", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(4.dp))
            Text(
                "Save all your trails and walks to a file, or add them from one: for a backup or a new phone. " +
                    "They're yours: the file is plain, readable JSON.",
                color = Ink, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("misty-steps-backup-$day.json")
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    enabled = !busy && !recording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text("Import") }
            }
            if (recording) {
                Text("Stop wandering to import.", color = Dusk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    pendingImport?.let { backup ->
        val n = NumberFormat.getIntegerInstance()
        val from = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(backup.exportedAt))
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Add these trails?") },
            text = {
                Text(
                    "This backup from $from has ${n.format(backup.points.size)} spots and " +
                        "${n.format(backup.sessions.size)} walks. They'll be added to the trails on this phone. " +
                        "Nothing here is deleted, and spots you already have are skipped."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    busy = true
                    scope.launch {
                        val msg = try {
                            val r = withContext(Dispatchers.IO) { FogStore.importBackup(backup) }
                            // A fresh phone also takes the wanderer from the backup.
                            if (r.wasEmpty) backup.character?.let { id -> Characters.all.firstOrNull { it.id == id }?.let(Characters::select) }
                            onImported()
                            "Added ${n.format(r.spots)} spots, ${n.format(r.walks)} walks and ${n.format(r.pins)} pins"
                        } catch (e: Exception) {
                            "Couldn't import the trails"
                        }
                        busy = false
                        toast(msg)
                    }
                }) { Text("Add trails") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

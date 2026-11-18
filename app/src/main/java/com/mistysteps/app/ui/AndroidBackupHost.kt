package com.mistysteps.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.Backup
import com.mistysteps.app.data.BackupCounts
import com.mistysteps.app.data.BackupException
import com.mistysteps.app.data.BackupFailure
import com.mistysteps.app.data.BackupHost
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PendingImport
import com.mistysteps.app.record.RecordingService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The backup card's platform side on Android: the system file picker (no extra permissions) and the stored trails. */
private class AndroidBackupHost(private val context: Context) : BackupHost {
    var exportLauncher: ManagedActivityResultLauncher<String, Uri?>? = null
    var importLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null
    private var waiting: CompletableDeferred<Uri?>? = null

    fun deliver(uri: Uri?) {
        waiting?.complete(uri)
        waiting = null
    }

    private suspend fun ask(launch: () -> Unit): Uri? {
        val answer = CompletableDeferred<Uri?>()
        waiting = answer
        launch()
        return answer.await()
    }

    override val recording: StateFlow<Boolean> get() = RecordingService.recording

    override suspend fun export(): BackupCounts? {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val uri = ask { exportLauncher!!.launch("misty-steps-backup-$day.json") } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val backup = FogStore.exportBackup(BuildConfig.VERSION_NAME, Characters.selected.value?.id)
                    .copy(characterNames = Characters.allCustomNames())
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backup.toJson().toByteArray()) }
                BackupCounts(backup.points.size, backup.sessions.size, backup.pins.size)
            } catch (e: Exception) {
                throw BackupFailure()
            }
        }
    }

    override suspend fun pickImport(): PendingImport? {
        val uri = ask { importLauncher!!.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                val backup = Backup.fromJson(text)
                PendingImport(backup.exportedAt, BackupCounts(backup.points.size, backup.sessions.size, backup.pins.size), backup)
            } catch (e: BackupException) {
                throw BackupFailure(getString(e.problem.res))
            } catch (e: Exception) {
                throw BackupFailure()
            }
        }
    }

    override suspend fun confirmImport(pending: PendingImport): BackupCounts {
        val backup = pending.payload as Backup
        val r = try {
            withContext(Dispatchers.IO) { FogStore.importBackup(backup) }
        } catch (e: Exception) {
            throw BackupFailure()
        }
        // A fresh phone also takes the wanderer from the backup.
        if (r.wasEmpty) backup.character?.let { id -> Characters.all.firstOrNull { it.id == id }?.let(Characters::select) }
        // Names fill in where you haven't named a character yet; nothing you named is overwritten.
        backup.characterNames.forEach { (id, name) -> if (Characters.customName(id) == null) Characters.setName(id, name) }
        return BackupCounts(r.spots, r.walks, r.pins)
    }
}

/** A [BackupHost] bound to this screen's file pickers. */
@Composable
fun rememberAndroidBackupHost(): BackupHost {
    val context = LocalContext.current
    val host = remember { AndroidBackupHost(context) }
    host.exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { host.deliver(it) }
    host.importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { host.deliver(it) }
    return host
}

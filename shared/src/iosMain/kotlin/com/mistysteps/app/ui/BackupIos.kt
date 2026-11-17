package com.mistysteps.app.ui

import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.Backup
import com.mistysteps.app.data.BackupCounts
import com.mistysteps.app.data.BackupException
import com.mistysteps.app.data.BackupFailure
import com.mistysteps.app.data.BackupHost
import com.mistysteps.app.data.PendingImport
import com.mistysteps.app.data.Session
import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.pin.Pin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Files, as the iOS app does them: the save and open dialogs. Callbacks, not suspend functions. */
interface BackupFiles {
    /** Asks where to save [text] as [fileName]: true when saved, false when it failed, null when the person cancelled. */
    fun askWhereToSave(fileName: String, text: String, done: (Boolean?) -> Unit)

    /** Asks for a file and reads it as text: null when the person cancelled; an empty string when it can't be read. */
    fun askForFile(done: (String?) -> Unit)
}

/** The stored trails, walks and pins, as the iOS app keeps them. */
interface BackupData {
    fun points(): List<TrailPoint>

    fun sessions(): List<Session>

    fun pins(): List<Pin>

    /** True when nothing is stored yet, so a backup's settings (the character) can be used too. */
    fun isEmpty(): Boolean

    /** Adds spots and walks that are not here yet; returns how many of each were added (pins count 0). */
    fun importData(points: List<TrailPoint>, sessions: List<Session>): BackupCounts

    /** Adds pins that are not here yet; returns how many. */
    fun importPins(pins: List<Pin>): Int

    /** Reads everything again into the models the screens draw from. */
    fun reload()
}

/** The backup card on iOS: [files] for the dialogs, [data] for what is stored. */
class IosBackupHost(
    private val files: BackupFiles,
    private val data: BackupData,
    private val versionName: String,
) : BackupHost {
    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording

    /** Swift says whether a walk is being recorded. */
    fun setRecording(value: Boolean) { _recording.value = value }

    private fun today(): String {
        val f = NSDateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = NSLocale("en_US_POSIX")
        return f.stringFromDate(NSDate())
    }

    override suspend fun export(): BackupCounts? {
        val sessions = data.sessions()
        val backup = Backup(
            exportedAt = (NSDate().timeIntervalSince1970 * 1000).toLong(),
            appVersion = versionName,
            distanceM = sessions.sumOf { it.distanceM },
            character = Characters.selected.value?.id,
            sessions = sessions,
            points = data.points(),
            pins = data.pins(),
            characterNames = Characters.allCustomNames(),
        )
        val saved = suspendCoroutine<Boolean?> { cont -> files.askWhereToSave("misty-steps-backup-${today()}.json", backup.toJson()) { cont.resume(it) } }
            ?: return null
        if (!saved) throw BackupFailure()
        return BackupCounts(backup.points.size, backup.sessions.size, backup.pins.size)
    }

    override suspend fun pickImport(): PendingImport? {
        val text = suspendCoroutine<String?> { cont -> files.askForFile { cont.resume(it) } } ?: return null
        try {
            if (text.isEmpty()) throw BackupFailure()
            val backup = Backup.fromJson(text)
            return PendingImport(backup.exportedAt, BackupCounts(backup.points.size, backup.sessions.size, backup.pins.size), backup)
        } catch (e: BackupException) {
            throw BackupFailure(getString(e.problem.res))
        } catch (e: BackupFailure) {
            throw e
        } catch (e: Exception) {
            throw BackupFailure()
        }
    }

    override suspend fun confirmImport(pending: PendingImport): BackupCounts {
        val backup = pending.payload as Backup
        try {
            val wasEmpty = data.isEmpty()
            val added = data.importData(backup.points, backup.sessions)
            val pins = data.importPins(backup.pins)
            // A fresh phone also takes the wanderer from the backup.
            if (wasEmpty) backup.character?.let { id -> Characters.all.firstOrNull { it.id == id }?.let(Characters::select) }
            // Names fill in where you haven't named a character yet; nothing you named is overwritten.
            backup.characterNames.forEach { (id, name) -> if (Characters.customName(id) == null) Characters.setName(id, name) }
            data.reload()
            return BackupCounts(added.points, added.walks, pins)
        } catch (e: Exception) {
            throw BackupFailure()
        }
    }
}

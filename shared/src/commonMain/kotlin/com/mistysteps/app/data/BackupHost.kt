package com.mistysteps.app.data

import kotlinx.coroutines.flow.StateFlow

/** How much a backup holds, or how much an import added. */
class BackupCounts(val points: Int, val walks: Int, val pins: Int)

/** A backup file that was read and checked, waiting for the person to say yes. [payload] belongs to the [BackupHost]. */
class PendingImport(val exportedAt: Long, val counts: BackupCounts, val payload: Any)

/** Something went wrong; [message] is ready to show, or null when the screen has a general text for it. */
class BackupFailure(override val message: String? = null) : Exception(message)

/**
 * What the backup card needs from the platform: asking for a file, reading and writing it, and applying a backup to
 * the stored trails. Calls suspend while the person picks a file; the card does all the showing.
 */
interface BackupHost {
    /** True while a walk is being recorded; importing waits until it stops. */
    val recording: StateFlow<Boolean>

    /** Asks where to save, then writes the backup. Null when the person cancelled; throws [BackupFailure] when saving failed. */
    suspend fun export(): BackupCounts?

    /** Asks for a file and checks it. Null when the person cancelled; throws [BackupFailure] when it can't be used. */
    suspend fun pickImport(): PendingImport?

    /** Adds the backup to what is on this device and returns what was added. Throws [BackupFailure] when it fails. */
    suspend fun confirmImport(pending: PendingImport): BackupCounts
}

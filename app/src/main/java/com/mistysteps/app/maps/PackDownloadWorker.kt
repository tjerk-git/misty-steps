package com.mistysteps.app.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Starts and stops the background download of a province's map. One job per province. */
object PackDownloads {
    fun enqueue(context: Context, entry: PackEntry, unmeteredOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<PackDownloadWorker>()
            .setInputData(workDataOf(PackDownloadWorker.KEY_ID to entry.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: tapping Download twice does not start a second job for the same province.
        WorkManager.getInstance(context).enqueueUniqueWork("pack-${entry.id}", ExistingWorkPolicy.KEEP, request)
        MapsGraph.packs.markDownloading(entry.id, 0, entry.totalBytes)
    }

    /**
     * Fetches just the neighbourhoods-and-streets file of an installed pack, quietly: only on an unmetered connection, with
     * no notification and no progress. For people who downloaded their province before stats files existed.
     */
    fun enqueueStats(context: Context, entry: PackEntry) {
        val request = OneTimeWorkRequestBuilder<StatsDownloadWorker>()
            .setInputData(workDataOf(PackDownloadWorker.KEY_ID to entry.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        // KEEP: asking again while one is waiting or running does nothing.
        WorkManager.getInstance(context).enqueueUniqueWork("stats-${entry.id}", ExistingWorkPolicy.KEEP, request)
    }

    /** Queues the quiet fetch for every installed pack of an up-to-date map that lacks its stats file. */
    fun enqueueMissingStats(context: Context, catalog: Catalog) {
        val packs = MapsGraph.packs
        for (entry in statsToFetch(catalog.provinces, packs::installedYear, packs::hasStats)) enqueueStats(context, entry)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("pack-$id")
        MapsGraph.packs.clearTransient(id)
    }
}

class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        // Anything unexpected must end in a state the user can see, never a download that is "downloading" forever.
        return try {
            download(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "download of $id failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                MapsGraph.packs.markFailed(id, Reason.NETWORK)
                Result.failure()
            }
        }
    }

    private suspend fun download(id: String): Result {
        val entry = MapsGraph.catalogStore.cached()?.entry(id) ?: return Result.failure()
        val packs = MapsGraph.packs
        val dest = File(packs.packDir(entry.year, id), "map.pmtiles")
        dest.parentFile?.mkdirs()

        val have = File(dest.path + ".part").takeIf { it.exists() }?.length() ?: 0L
        val free = StatFs(applicationContext.filesDir.path).availableBytes
        if (!DownloadPolicy.hasSpaceFor(entry.totalBytes - have, free)) {
            packs.markFailed(id, Reason.NO_SPACE)
            return Result.failure()
        }

        // The progress notification is a courtesy. From the background (a retry after a lost connection) Android 12+
        // refuses to start a foreground service: carry on as a plain background job then.
        try {
            setForeground(foregroundInfo(entry, have))
        } catch (e: Exception) {
            Log.w(TAG, "download continues without a notification", e)
        }
        packs.markDownloading(id, have, entry.totalBytes)
        val installer = PackInstaller(HttpDownloader("${applicationContext.packageName}/${BuildConfig.VERSION_NAME}"))
        var lastTick = 0L
        val result = withContext(Dispatchers.IO) {
            installer.install(entry.map, dest) { bytes ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastTick >= PROGRESS_EVERY_MS) {
                    lastTick = now
                    packs.markDownloading(id, bytes, entry.totalBytes)
                    notify(entry, bytes)
                }
            }
        }
        return when (result) {
            is InstallResult.Done -> {
                // The neighbourhoods and streets file comes with the map when the catalog lists one.
                entry.stats?.let { stats ->
                    withContext(Dispatchers.IO) {
                        // The stats file counts toward the same progress: it follows the map in the one total shown.
                        installer.install(stats, File(packs.packDir(entry.year, id), ProvincePacks.STATS_FILE)) { bytes ->
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTick >= PROGRESS_EVERY_MS) {
                                lastTick = now
                                packs.markDownloading(id, entry.map.bytes + bytes, entry.totalBytes)
                            }
                        }
                    }
                }
                packs.clearTransient(id)
                packs.removeOlderThan(entry.year, id)
                Result.success()
            }
            is InstallResult.Failed -> {
                Log.w(TAG, "install of $id failed: ${result.reason} (${result.cause})")
                when {
                // A cut connection is retried by WorkManager with backoff; give up after a few tries.
                result.reason == Reason.NETWORK && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> {
                    packs.markFailed(id, result.reason)
                    Result.failure()
                }
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(KEY_ID)
        val entry = id?.let { MapsGraph.catalogStore.cached()?.entry(it) }
        return foregroundInfo(entry, 0)
    }

    private fun foregroundInfo(entry: PackEntry?, bytes: Long): ForegroundInfo {
        val notification = build(entry, bytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(entry: PackEntry, bytes: Long) {
        applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(entry, bytes))
    }

    private fun build(entry: PackEntry?, bytes: Long): android.app.Notification {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.maps_downloads_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val total = entry?.totalBytes ?: 0L
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.maps_downloading_notif, entry?.name.orEmpty()))
            .setProgress(100, if (total > 0) (bytes * 100 / total).toInt() else 0, total == 0L)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val KEY_ID = "id"
        private const val TAG = "PackDownloadWorker"
        private const val CHANNEL_ID = "map-downloads"
        private const val NOTIFICATION_ID = 42
        private const val PROGRESS_EVERY_MS = 250L
        private const val MAX_ATTEMPTS = 5
    }
}

/** Downloads only the stats file of an installed pack (see [PackDownloads.enqueueStats]). Silent: a failure just means no neighbourhood card yet. */
class StatsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(PackDownloadWorker.KEY_ID) ?: return Result.failure()
        val entry = MapsGraph.catalogStore.cached()?.entry(id) ?: return Result.success()
        val stats = entry.stats ?: return Result.success()
        val packs = MapsGraph.packs
        // Only for a map that is still of the catalog's year and does not have the file yet; otherwise there is nothing to do.
        if (packs.installedYear(id) != entry.year || packs.hasStats(id)) return Result.success()
        val dest = File(packs.packDir(entry.year, id), ProvincePacks.STATS_FILE)
        val installer = PackInstaller(HttpDownloader("${applicationContext.packageName}/${BuildConfig.VERSION_NAME}"))
        return try {
            when (val result = withContext(Dispatchers.IO) { installer.install(stats, dest) }) {
                is InstallResult.Done -> {
                    packs.clearTransient(id) // announces the change: the file list is read again
                    Result.success()
                }
                is InstallResult.Failed -> {
                    Log.w(TAG, "stats of $id failed: ${result.reason} (${result.cause})")
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "stats of $id failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "StatsDownloadWorker"
        const val MAX_ATTEMPTS = 5
    }
}

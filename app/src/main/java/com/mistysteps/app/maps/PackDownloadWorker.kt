package com.mistysteps.app.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
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
        MapsGraph.packs.markDownloading(entry.id, 0, entry.map.bytes)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("pack-$id")
        MapsGraph.packs.clearTransient(id)
    }
}

class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val entry = MapsGraph.catalogStore.cached()?.entry(id) ?: return Result.failure()
        val packs = MapsGraph.packs
        val dest = File(packs.packDir(entry.year, id), "map.pmtiles")
        dest.parentFile?.mkdirs()

        val have = File(dest.path + ".part").takeIf { it.exists() }?.length() ?: 0L
        val free = StatFs(applicationContext.filesDir.path).availableBytes
        if (!DownloadPolicy.hasSpaceFor(entry.map.bytes - have, free)) {
            packs.markFailed(id, Reason.NO_SPACE)
            return Result.failure()
        }

        setForeground(foregroundInfo(entry, have))
        packs.markDownloading(id, have, entry.map.bytes)
        val installer = PackInstaller(HttpDownloader("${applicationContext.packageName}/${BuildConfig.VERSION_NAME}"))
        var lastTick = 0L
        val result = withContext(Dispatchers.IO) {
            installer.install(entry.map, dest) { bytes ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastTick >= PROGRESS_EVERY_MS) {
                    lastTick = now
                    packs.markDownloading(id, bytes, entry.map.bytes)
                    notify(entry, bytes)
                }
            }
        }
        return when (result) {
            is InstallResult.Done -> {
                // The neighbourhoods and streets file comes with plan 4; it is only fetched when the catalog has one.
                entry.stats?.let { stats ->
                    withContext(Dispatchers.IO) { installer.install(stats, File(packs.packDir(entry.year, id), "stats.zip")) }
                }
                packs.clearTransient(id)
                packs.removeOlderThan(entry.year, id)
                Result.success()
            }
            is InstallResult.Failed -> when {
                // A cut connection is retried by WorkManager with backoff; give up after a few tries.
                result.reason == Reason.NETWORK && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> {
                    packs.markFailed(id, result.reason)
                    Result.failure()
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
        val total = entry?.map?.bytes ?: 0L
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
        private const val CHANNEL_ID = "map-downloads"
        private const val NOTIFICATION_ID = 42
        private const val PROGRESS_EVERY_MS = 250L
        private const val MAX_ATTEMPTS = 5
    }
}

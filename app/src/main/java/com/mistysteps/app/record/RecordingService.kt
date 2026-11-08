package com.mistysteps.app.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mistysteps.app.MainActivity
import com.mistysteps.app.R
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.debug.Battery
import com.mistysteps.app.debug.BatteryLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that listens to GPS while you walk, so the fog keeps clearing
 * with the screen off. Fixes are handed to [FogStore], which saves them locally.
 */
class RecordingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    /** What the notification and GPS interval are currently set up for. */
    private var vehicleMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission(this)) {
            bailOut()
            return START_NOT_STICKY
        }
        if (_recording.value) return START_NOT_STICKY

        // Only ever started from the visible app. If Android refuses (e.g. we somehow got
        // started from the background), give up quietly instead of crashing.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            startListening()
        } catch (e: Exception) { // ForegroundServiceStartNotAllowedException, SecurityException
            Log.w(TAG, "Could not start recording", e)
            locationManager.removeUpdates(this)
            bailOut()
            return START_NOT_STICKY
        }
        val session = FogStore.beginWalk(Battery.percent(this))
        BatteryLog.walkStarted(session)
        _recording.value = true
        // Not sticky: a background restart can't legally use location, so let the user restart it.
        return START_NOT_STICKY
    }

    /**
     * Stop without recording. We were started with startForegroundService(), so Android
     * insists we call startForeground() before stopping, or it kills the app. Location type
     * isn't allowed without the permission, so use a type that needs none.
     */
    private fun bailOut() {
        try {
            val type = when {
                Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                else -> 0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not satisfy startForeground before stopping", e)
        }
        stopSelf()
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private fun startListening() {
        // Registering works even while GPS is switched off; fixes start once it's on.
        if (LocationManager.GPS_PROVIDER in locationManager.allProviders) {
            // In a vehicle we only need to notice when you slow down, so ask less often.
            val interval = if (vehicleMode) 5_000L else 2_000L
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, interval, if (vehicleMode) 0f else 5f, this, Looper.getMainLooper(),
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        FogStore.onLocation(location)
        BatteryLog.onFix()
        val vehicle = FogStore.inVehicle.value
        if (vehicle != vehicleMode) {
            vehicleMode = vehicle
            BatteryLog.setInVehicle(vehicle)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            // Re-registering the same listener replaces the old request.
            startListening()
        }
    }

    @Deprecated("Required on API < 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        if (_recording.value) BatteryLog.walkEnded(FogStore.endWalk(Battery.percent(this)))
        FogStore.flush()
        _recording.value = false
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (vehicleMode) R.string.notif_text_vehicle else R.string.notif_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "wandering"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.mistysteps.app.STOP"

        private val _recording = MutableStateFlow(false)
        val recording: StateFlow<Boolean> = _recording.asStateFlow()

        fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}

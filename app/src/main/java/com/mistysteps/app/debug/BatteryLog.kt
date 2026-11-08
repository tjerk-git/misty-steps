package com.mistysteps.app.debug

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.data.Session
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** A reading of the battery, straight from Android. Fields are null when the phone doesn't report them. */
data class BatterySample(
    val levelPct: Int?,
    val chargeUah: Int?,
    val currentUa: Int?,
    val voltageMv: Int?,
    val tempC: Float?,
    val plugged: Boolean,
)

object Battery {
    fun read(context: Context): BatterySample {
        val bm = context.getSystemService(BatteryManager::class.java)
        val sticky: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun prop(id: Int): Int? = bm?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return BatterySample(
            levelPct = if (level >= 0 && scale > 0) level * 100 / scale else prop(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            chargeUah = prop(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Int.MIN_VALUE },
            voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 },
            tempC = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
        )
    }

    fun percent(context: Context): Int? = try { read(context).levelPct } catch (e: Exception) { null }
}

/**
 * Debug builds only: writes battery readings and a walk log to `files/debug/` on the phone,
 * so test runs can be pulled off with `debug/pull.sh` and compared. Nothing leaves the phone
 * on its own. Release builds skip all of this.
 *
 * - `battery.csv`: one row a minute while the app is doing something (map on screen or a walk
 *   recording), plus one row for every state change (event column).
 * - `sessions.csv`: one row per finished walk.
 */
object BatteryLog {
    private const val TAG = "BatteryLog"
    private const val SAMPLE_MS = 60_000L

    val enabled = BuildConfig.DEBUG

    private lateinit var app: Context
    private lateinit var dir: File
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    // State, main thread only.
    private var mapVisible = false
    private var recording = false
    private var inVehicle = false
    private var sessionId: Long? = null
    private var fixes = 0
    private var lastSampleAt = 0L
    private var ticking = false

    fun init(context: Context) {
        if (!enabled) return
        app = context.applicationContext
        dir = app.filesDir.resolve("debug")
        sample("app_start")
    }

    /** The map is on screen (fog animating) or not. */
    fun setMapVisible(visible: Boolean) {
        if (!enabled || visible == mapVisible) return
        mapVisible = visible
        sample(if (visible) "map_shown" else "map_hidden")
        updateTicking()
    }

    fun walkStarted(s: Session) {
        if (!enabled) return
        recording = true
        sessionId = s.id
        fixes = 0
        sample("walk_start")
        updateTicking()
    }

    fun walkEnded(s: Session?) {
        if (!enabled) return
        // Each row carries the state from then on, so flip it before sampling.
        recording = false
        inVehicle = false
        sample("walk_end")
        sessionId = null
        updateTicking()
    }

    /** A walk's final numbers, once its new ground has been counted. */
    fun walkFinished(s: Session) {
        if (enabled) appendSession(s)
    }

    /** Clearing paused because you seem to be in a vehicle (GPS asks less often then). */
    fun setInVehicle(value: Boolean) {
        if (!enabled || value == inVehicle) return
        inVehicle = value
        sample(if (value) "vehicle" else "own_power")
    }

    /**
     * Called for every GPS fix. The minute timer runs on uptime, which stops while the phone
     * sleeps; fixes keep waking us during a walk, so sample here too.
     */
    fun onFix() {
        if (!enabled) return
        fixes++
        if (SystemClock.elapsedRealtime() - lastSampleAt >= SAMPLE_MS) sample("tick")
    }

    private val tick = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - lastSampleAt >= SAMPLE_MS - 1_000) sample("tick")
            main.postDelayed(this, SAMPLE_MS)
        }
    }

    private fun updateTicking() {
        val want = mapVisible || recording
        if (want == ticking) return
        ticking = want
        if (want) main.postDelayed(tick, SAMPLE_MS) else main.removeCallbacks(tick)
    }

    /** Each row is the state from this moment on (until the next row). */
    private fun sample(event: String) {
        lastSampleAt = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val b = try { Battery.read(app) } catch (e: Exception) { null }
        val screenOn = app.getSystemService(PowerManager::class.java)?.isInteractive
        val saver = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode
        val row = listOf(
            iso.format(Date(now)), now, event, sessionId ?: "",
            b?.levelPct ?: "", b?.chargeUah ?: "", b?.currentUa ?: "", b?.voltageMv ?: "", b?.tempC ?: "",
            b?.plugged.bit(), screenOn.bit(), recording.bit(), mapVisible.bit(), saver.bit(), fixes, inVehicle.bit(),
        ).joinToString(",")
        fixes = 0
        append("battery.csv", BATTERY_HEADER, row)
    }

    private fun appendSession(s: Session) {
        val end = s.endTime ?: System.currentTimeMillis()
        val row = listOf(
            s.id, iso.format(Date(s.startTime)), iso.format(Date(end)), (end - s.startTime) / 1000,
            "%.1f".format(Locale.US, s.distanceM), s.points, s.batteryStart ?: "", s.batteryEnd ?: "",
            s.newAreaM2?.let { "%.0f".format(Locale.US, it) } ?: "",
        ).joinToString(",")
        append("sessions.csv", SESSIONS_HEADER, row)
    }

    private fun append(name: String, header: String, row: String) {
        io.execute {
            try {
                dir.mkdirs()
                val f = dir.resolve(name)
                // Columns changed since this file was started: keep the old one aside.
                if (f.exists() && f.bufferedReader().use { it.readLine() } != header) {
                    f.renameTo(dir.resolve("$name.${System.currentTimeMillis()}.old"))
                }
                val fresh = !f.exists()
                FileWriter(f, true).use { w ->
                    if (fresh) w.append(header).append('\n')
                    w.append(row).append('\n')
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not write $name", e)
            }
        }
    }

    private fun Boolean?.bit() = when (this) { true -> "1"; false -> "0"; null -> "" }

    private const val BATTERY_HEADER =
        "time,epoch_ms,event,session_id,level_pct,charge_uah,current_ua,voltage_mv,temp_c," +
            "plugged,screen_on,recording,map_visible,power_save,gps_fixes,in_vehicle"
    private const val SESSIONS_HEADER =
        "session_id,start,end,duration_s,distance_m,points,battery_start_pct,battery_end_pct,new_area_m2"
}

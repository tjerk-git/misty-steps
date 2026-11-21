package com.mistysteps.app.fog

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.map.MapProjection

/**
 * The mist as a transparent view above the map. It does not take touches (they go to the map) and draws through
 * [projection], so it follows the map; call [moved] when the camera moves.
 *
 * It also watches how long the app's frames take while the map is visible. A phone that can't keep up (old, hot,
 * or on battery saver, which caps the CPU) gets a cheaper mist, so touches and scrolling stay responsive.
 */
class FogView(context: Context, private val projection: MapProjection) : View(context) {
    private val painter = FogPainter()
    private val governor = FogGovernor()
    private val handler = Handler(Looper.getMainLooper())
    private var window: Window? = null
    private var overrideAt = 0L

    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        // Called on [handler], the main thread. The first frame of a window includes setting everything up: skip it.
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val now = SystemClock.uptimeMillis()
        val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
        if (governor.onFrame(ms, now)) apply()
        if (BuildConfig.DEBUG && now - overrideAt > 1000) { overrideAt = now; apply() }
    }

    private val saverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = checkSaver()
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) = painter.draw(canvas, projection, this)

    fun moved() = invalidate()

    /** Keeps the mist drifting; call when the map is visible. */
    fun startDrifting() {
        painter.startDrifting(this)
        watchFrames()
    }

    fun stopDrifting() {
        painter.stopDrifting()
        unwatchFrames()
    }

    fun release() {
        unwatchFrames()
        painter.detach()
    }

    private fun watchFrames() {
        if (window != null) return
        val w = activityWindow() ?: return
        window = w
        w.addOnFrameMetricsAvailableListener(frameListener, handler)
        context.registerReceiver(saverReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        checkSaver()
    }

    private fun unwatchFrames() {
        val w = window ?: return
        window = null
        w.removeOnFrameMetricsAvailableListener(frameListener)
        runCatching { context.unregisterReceiver(saverReceiver) }
    }

    private fun checkSaver() {
        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        if (governor.setPowerSave(saver, SystemClock.uptimeMillis())) apply()
    }

    private fun apply() {
        val level = debugLevel() ?: governor.level
        if (BuildConfig.DEBUG && painter.level != level) android.util.Log.d("FogLevel", "$level")
        painter.level = level
    }

    /** Debug builds only: `adb shell settings put global misty_fog_level 0|1|2` pins the level, 3 or unset follows the governor. */
    private fun debugLevel(): FogLevel? {
        if (!BuildConfig.DEBUG) return null
        val v = Settings.Global.getInt(context.contentResolver, "misty_fog_level", 3)
        return FogLevel.entries.getOrNull(v)
    }

    private fun activityWindow(): Window? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c.window
            c = c.baseContext
        }
        return null
    }
}

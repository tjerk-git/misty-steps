package com.mistysteps.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Debug builds only: puts the phone somewhere else, for testing (for example the "No map of ... yet" offer in another province).
 *
 *   adb shell appops set com.potloodgum.mistysteps.debug android:mock_location allow   (once)
 *   adb shell am broadcast -a com.potloodgum.mistysteps.debug.MOCK --ed lat 51.9851 --ed lon 5.8987 [--ed walk 1.4]
 *   adb shell am broadcast -a com.potloodgum.mistysteps.debug.MOCK --ez stop true
 *
 * `walk` is a speed in metres per second, heading north. The fix repeats every second while the app process lives.
 */
class MockLocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("stop", false)) { Mock.stop(context); return }
        if (!intent.hasExtra("lat") || !intent.hasExtra("lon")) { Log.w(TAG, "needs --ed lat and --ed lon"); return }
        Mock.start(context, intent.getDoubleExtra("lat", 0.0), intent.getDoubleExtra("lon", 0.0), intent.getDoubleExtra("walk", 0.0))
    }

    private object Mock {
        private val handler = Handler(Looper.getMainLooper())
        private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        private var running: Runnable? = null

        fun start(context: Context, lat: Double, lon: Double, walk: Double) {
            stop(context)
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                providers.forEach { p ->
                    runCatching { manager.removeTestProvider(p) }
                    manager.addTestProvider(p, false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE)
                    manager.setTestProviderEnabled(p, true)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "not allowed: run adb shell appops set ${context.packageName} android:mock_location allow", e)
                return
            }
            var currentLat = lat
            val step = object : Runnable {
                override fun run() {
                    currentLat += walk / 111_320.0
                    providers.forEach { p ->
                        val l = Location(p).apply {
                            latitude = currentLat; longitude = lon; accuracy = 5f; altitude = 0.0
                            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                            if (walk > 0) { speed = walk.toFloat(); bearing = 0f }
                        }
                        runCatching { manager.setTestProviderLocation(p, l) }
                    }
                    handler.postDelayed(this, 1000)
                }
            }
            running = step
            step.run()
            Log.i(TAG, "mocking $lat,$lon walk=$walk")
        }

        fun stop(context: Context) {
            running?.let { handler.removeCallbacks(it) }
            running = null
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            providers.forEach { runCatching { manager.removeTestProvider(it) } }
        }
    }

    private companion object { const val TAG = "MockLocation" }
}

package com.mistysteps.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Fix(
    val lat: Double,
    val lon: Double,
    val bearing: Float?,
    val speed: Float?,
    val accuracy: Float?,
    val time: Long,
)

object FixRules {
    const val MAX_AGE_MS = 10_000L

    /** The newest fix wins; at the same moment the more accurate one does. */
    fun prefer(current: Fix?, candidate: Fix): Fix = when {
        current == null -> candidate
        candidate.time > current.time -> candidate
        candidate.time < current.time -> current
        (candidate.accuracy ?: Float.MAX_VALUE) < (current.accuracy ?: Float.MAX_VALUE) -> candidate
        else -> current
    }

    fun isStale(fix: Fix, now: Long): Boolean = now - fix.time > MAX_AGE_MS
}

/**
 * Where you are, for the map: the character, the camera and pins use it. It listens to GPS itself while the map
 * is open; [offer] lets the recording service hand over its fixes too, so both feed the same value.
 */
class LocationFeed(context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix

    private var listening = false

    /** The newest platform fix, for code that needs its timestamps ([com.mistysteps.app.pin.PinRules.freshestFix]). */
    @Volatile var lastLocation: Location? = null
        private set

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = offer(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Required on API < 29") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
    }

    fun offer(location: Location) {
        if (lastLocation == null || location.time >= lastLocation!!.time) lastLocation = location
        val candidate = Fix(
            location.latitude, location.longitude,
            if (location.hasBearing()) location.bearing else null,
            if (location.hasSpeed()) location.speed else null,
            if (location.hasAccuracy()) location.accuracy else null,
            location.time,
        )
        _fix.value = FixRules.prefer(_fix.value, candidate)
    }

    /** Starts listening; the caller has checked the location permission. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (listening) return
        listening = true
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener)
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::offer)
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        manager.removeUpdates(listener)
    }
}

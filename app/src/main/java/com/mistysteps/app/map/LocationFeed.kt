package com.mistysteps.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where you are, for the map: the character, the camera and pins use it. It listens to GPS itself while the map
 * is open; [offer] lets the recording service hand over its fixes too, so both feed the same value.
 */
class LocationFeed(context: Context) : LocationSource {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _fix = MutableStateFlow<Fix?>(null)
    override val fix: StateFlow<Fix?> = _fix

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
    override fun start() {
        if (listening) return
        listening = true
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener)
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::offer)
        }
    }

    override fun stop() {
        if (!listening) return
        listening = false
        manager.removeUpdates(listener)
    }
}

package com.mistysteps.app.ui

import android.content.Context
import android.location.Location
import android.widget.FrameLayout
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.fog.FogView
import com.mistysteps.app.map.LatLon
import com.mistysteps.app.eraser.EraserController
import com.mistysteps.app.eraser.FogErasedStore
import com.mistysteps.app.eraser.onTouch
import com.mistysteps.app.map.LocationFeed
import com.mistysteps.app.map.MarkersView
import com.mistysteps.app.map.MistyMap
import com.mistysteps.app.map.TrailLayer
import com.mistysteps.app.maps.MapsGraph
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.pin.PinRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Owns the map screen's map: the MapLibre map (drawing the downloaded province packs), your trail on it, the mist and
 * your pins and character above it, and where you are. The map screen talks only to this class.
 *
 * Camera: when the app opens it starts a little zoomed out on your character and glides after it as you walk
 * ("following"). Dragging the map with one finger stops following; the location button picks it up again.
 */
class MapController(context: Context) {
    private val feed = LocationFeed(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val map = MistyMap(context, MapsGraph.packs) { feed.fix.value?.let { LatLon(it.lat, it.lon) } }
    private val fog = FogView(context, map.projection)
    private val markers = MarkersView(context, map.projection) { feed.fix.value }
    private val trail = TrailLayer()

    /** The magic eraser: its touch handling sits on the map; the screen decides when it is active and what it may do. */
    val eraser = EraserController(map.projection, FogErasedStore, context.resources.displayMetrics.density, map::setOneFingerGestures, System::currentTimeMillis)

    /** The map with the mist and the markers stacked on it. */
    val mapView = FrameLayout(context).apply {
        addView(map.mapView)
        addView(fog)
        addView(markers)
    }

    private var onPinTap: ((Pin) -> Unit)? = null
    private var onClearedLongPress: ((Double, Double) -> Unit)? = null
    private var onFogLongPress: (() -> Unit)? = null

    private var resumed = false
    private var covered = false

    init {
        map.onCameraMove = { fog.moved(); markers.moved() }
        map.addStyleListener { trail.install(it) }
        trail.watch(scope)
        map.onTouch = { eraser.onTouch(it) }
        map.onTap = { x, y -> if (eraser.active) true else markers.pinAt(x, y)?.let { onPinTap?.invoke(it); true } ?: false }
        map.onLongPress = { x, y ->
          if (eraser.active) true else {
            val at = map.projection.fromScreen(x, y)
            if (PinRules.isCleared(FogStore.index, at.lat, at.lon)) onClearedLongPress?.invoke(at.lat, at.lon) else onFogLongPress?.invoke()
            true
          }
        }
        map.onCreate(null)
    }

    fun resume(hasPermission: Boolean) {
        resumed = true
        map.onStart()
        map.onResume()
        updateDrift()
        if (hasPermission) enableLocation()
    }

    fun pause() {
        resumed = false
        map.onPause()
        map.onStop()
        feed.stop()
        updateDrift()
    }

    /** True while another screen hides the map, so the mist needn't keep animating. */
    fun setCovered(value: Boolean) {
        covered = value
        updateDrift()
    }

    private fun updateDrift() {
        val visible = resumed && !covered
        if (visible) {
            fog.startDrifting()
            markers.startTicking()
        } else {
            fog.stopDrifting()
            markers.stopTicking()
        }
        BatteryLog.setMapVisible(visible)
    }

    fun enableLocation() = feed.start()

    /** Where you are, or null before the first fix. */
    fun myLocation(): LatLon? = feed.fix.value?.let { LatLon(it.lat, it.lon) }

    /** The newest platform fix, with its timestamps. */
    fun lastFix(): Location? = feed.lastLocation

    /** Returns false if there's no fix yet. */
    fun flyToMe(): Boolean = map.flyToMe()

    /** Looks at a place on the map; stops following you. */
    fun flyTo(lat: Double, lon: Double, zoom: Double = 16.0) = map.flyTo(lat, lon, zoom)

    /** Redraws what sits above the map (the mist, pins) after the pins or the cleared ground changed. */
    fun refresh() {
        fog.moved()
        markers.moved()
    }

    /** What happens when you tap a pin, or long-press on cleared ground or in the fog. */
    fun setPinCallbacks(onTap: (Pin) -> Unit, onCleared: (Double, Double) -> Unit, onFog: () -> Unit) {
        onPinTap = onTap
        onClearedLongPress = onCleared
        onFogLongPress = onFog
    }

    fun destroy() {
        markers.stopTicking()
        fog.release()
        map.onDestroy()
        scope.cancel()
    }
}

package com.mistysteps.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.mistysteps.app.character.CharacterOverlay
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Mercator
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.fog.FogOverlay
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.pin.PinOverlay
import com.mistysteps.app.trail.TrailOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Owns the osmdroid map, its fog, your trail and the "you are here" marker.
 *
 * Camera: when the app opens it starts a little zoomed out on your character and glides
 * after it as you walk ("following"). Dragging the map with one finger stops following;
 * the location button picks it up again. Pinch and double-tap zoom keep following.
 */
class MapController(context: Context) {
    private val prefs = context.getSharedPreferences("camera", Context.MODE_PRIVATE)

    val fog = FogOverlay()

    val mapView: MapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        val ts = MapView.getTileSystem()
        setScrollableAreaLimitLatitude(ts.maxLatitude, ts.minLatitude, 0)
        minZoomLevel = 3.0
        maxZoomLevel = 19.5
        overlays.add(fog)
    }

    /** The steps you leave behind, on top of the fog. */
    private val trail = TrailOverlay(context.resources.displayMetrics.density).also {
        mapView.overlays.add(it)
    }

    /** Your pins: above the fog and trail, below your character. */
    private val pins = PinOverlay(context.resources.displayMetrics.density).also {
        mapView.overlays.add(it)
    }

    val me = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).also {
        it.isDrawAccuracyEnabled = false
        mapView.overlays.add(it)
    }

    /** Your pixel-art wanderer, drawn on top of everything. */
    private val character = CharacterOverlay(me, context.resources.displayMetrics.density).also {
        mapView.overlays.add(it)
    }

    // Declared before init, which sets them.
    private var following = false
    /** Set when we start on the world view: fly down to you on the first fix. */
    private var flyInPending = false

    init {
        // Start where you last were, zoomed out a little. Without any known place, show the world;
        // the camera flies in once the first fix arrives.
        val place = FogStore.lastKnown?.let { GeoPoint(it.first, it.second) }
            ?: if (prefs.contains("zoom")) {
                GeoPoint(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble())
            } else null
        if (place != null) {
            mapView.controller.setZoom(OPEN_ZOOM)
            mapView.controller.setCenter(place)
        } else {
            mapView.controller.setZoom(3.0)
            mapView.controller.setCenter(GeoPoint(30.0, 0.0))
            flyInPending = true
        }
        watchForDrags(context)
    }

    private var resumed = false
    private var covered = false
    private var pausedAt = 0L

    fun resume(hasPermission: Boolean) {
        resumed = true
        mapView.onResume()
        // Coming back after a while counts as opening the app again: zoom out and find you.
        if (pausedAt != 0L && SystemClock.elapsedRealtime() - pausedAt > REOPEN_AFTER_MS &&
            mapView.zoomLevelDouble > OPEN_ZOOM
        ) {
            mapView.controller.setZoom(OPEN_ZOOM)
        }
        startFollowing()
        updateDrift()
        if (hasPermission) enableLocation()
    }

    fun pause() {
        resumed = false
        pausedAt = SystemClock.elapsedRealtime()
        saveCamera()
        stopFollowLoop()
        me.disableMyLocation()
        updateDrift()
        mapView.onPause()
    }

    /** True while another screen hides the map, so the fog needn't keep animating. */
    fun setCovered(value: Boolean) {
        covered = value
        updateDrift()
    }

    private fun updateDrift() {
        val visible = resumed && !covered
        if (visible) fog.startDrifting(mapView) else fog.stopDrifting()
        BatteryLog.setMapVisible(visible)
    }

    fun enableLocation() {
        if (me.isMyLocationEnabled) return
        me.enableMyLocation()
    }

    /** Returns false if there's no fix yet. */
    fun flyToMe(): Boolean {
        val loc = me.myLocation ?: return false
        val zoom = maxOf(mapView.zoomLevelDouble, OPEN_ZOOM)
        fly(loc, zoom, 800L)
        startFollowing()
        return true
    }

    // ---- following your character ------------------------------------------------------

    private var flyingUntil = 0L
    private var lastFollowAt = 0L
    private var followLoopRunning = false
    /** True once the camera has caught up; it then waits for a real move before gliding again. */
    private var caughtUp = false

    /**
     * Where the camera really is, in mercator units. The map itself only keeps whole pixels,
     * so the small per-frame steps of a slow glide would be rounded away; we keep the exact
     * position here and hand the map the rounded result.
     */
    private var camX = Double.NaN
    private var camY = Double.NaN

    private fun startFollowing() {
        following = true
        caughtUp = false
        if (resumed && !followLoopRunning) {
            followLoopRunning = true
            lastFollowAt = SystemClock.uptimeMillis()
            mapView.post(followTick)
        }
    }

    private fun stopFollowLoop() {
        followLoopRunning = false
        mapView.removeCallbacks(followTick)
    }

    private fun fly(to: GeoPoint, zoom: Double, ms: Long) {
        flyingUntil = SystemClock.uptimeMillis() + ms + 100
        mapView.controller.animateTo(to, zoom, ms)
    }

    /**
     * Eases the camera towards your character a little every frame, so it drifts after you
     * instead of jumping with every GPS fix. Checks only a few times a second once settled.
     */
    private val followTick = object : Runnable {
        override fun run() {
            if (!followLoopRunning) return
            if (!following || !resumed) {
                followLoopRunning = false
                return
            }
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastFollowAt).coerceIn(1, 100)
            lastFollowAt = now
            var settled = true
            val target = me.myLocation
            val w = mapView.width
            val h = mapView.height
            if (target != null && now >= flyingUntil && w > 0 && h > 0) {
                val c = mapView.mapCenter
                val size = TileSystem.MapSize(mapView.zoomLevelDouble)
                // Someone else moved the map (pinch zoom, a fly): start from where it is now.
                val mx = Mercator.x(c.longitude)
                val my = Mercator.y(c.latitude)
                if (camX.isNaN() || hypot((camX - mx) * size, (camY - my) * size) > 3) {
                    camX = mx
                    camY = my
                }
                var dxM = Mercator.x(target.longitude) - camX
                if (dxM > 0.5) dxM -= 1.0
                if (dxM < -0.5) dxM += 1.0
                val dyM = Mercator.y(target.latitude) - camY
                val off = hypot(dxM * size, dyM * size)
                when {
                    flyInPending -> {
                        flyInPending = false
                        fly(GeoPoint(target), OPEN_ZOOM, 1500L)
                        settled = false
                    }
                    // Far away (first fix, or you travelled): fly there instead of sliding across the world.
                    off > 2.5 * maxOf(w, h) -> {
                        fly(GeoPoint(target), maxOf(mapView.zoomLevelDouble, OPEN_ZOOM), 1500L)
                        settled = false
                    }
                    // Caught up and you've barely moved (GPS jitter): stay put, no redraws.
                    caughtUp && off < START_PX -> Unit
                    off > SETTLED_PX -> {
                        var f = 1 - exp(-dt / FOLLOW_TAU_MS)
                        // The last bit: land exactly on the target and stop.
                        if (off * (1 - f) < SETTLED_PX) f = 1.0
                        camX = (camX + dxM * f).mod(1.0)
                        camY += dyM * f
                        mapView.controller.setCenter(GeoPoint(Mercator.lat(camY), Mercator.lon(camX)))
                        settled = f >= 1.0
                    }
                }
                caughtUp = settled
            }
            // The glide is slow, so ~30 fps looks just as smooth as 60 and costs half.
            mapView.postDelayed(this, if (settled) 250L else 33L)
        }
    }

    /** A one-finger drag means you want to look around: stop following. */
    @SuppressLint("ClickableViewAccessibility") // only watches; the map still gets every event
    private fun watchForDrags(context: Context) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var multi = false
        mapView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; multi = false }
                MotionEvent.ACTION_POINTER_DOWN -> multi = true
                MotionEvent.ACTION_MOVE ->
                    if (!multi && following && hypot(e.x - downX, e.y - downY) > slop) following = false
            }
            false
        }
    }

    fun refresh() = mapView.invalidate()

    /** What happens when you tap a pin, or long-press on cleared ground or in the fog. */
    fun setPinCallbacks(onTap: (Pin) -> Unit, onCleared: (Double, Double) -> Unit, onFog: () -> Unit) {
        pins.onPinTap = onTap
        pins.onClearedLongPress = onCleared
        pins.onFogLongPress = onFog
    }

    /** Looks at a place on the map; stops following you. */
    fun flyTo(lat: Double, lon: Double, zoom: Double = 17.0) {
        following = false
        fly(GeoPoint(lat, lon), zoom, 800L)
    }

    private fun saveCamera() {
        val c = mapView.mapCenter
        prefs.edit()
            .putFloat("zoom", mapView.zoomLevelDouble.toFloat())
            .putFloat("lat", c.latitude.toFloat())
            .putFloat("lon", c.longitude.toFloat())
            .apply()
    }

    fun destroy() {
        stopFollowLoop()
        mapView.onDetach()
    }

    private companion object {
        /** How far out the camera starts when you open the app. */
        const val OPEN_ZOOM = 15.5

        /** Back after this long counts as opening the app again. */
        const val REOPEN_AFTER_MS = 5 * 60_000L

        /** Camera catches up with your character with this time constant: slow and floaty. */
        const val FOLLOW_TAU_MS = 900.0

        /** Closer than this (screen pixels) counts as centred: no more redraws. */
        const val SETTLED_PX = 0.5

        /** Once centred, the camera only starts gliding again when you're this far (screen pixels) off. */
        const val START_PX = 8.0
    }
}

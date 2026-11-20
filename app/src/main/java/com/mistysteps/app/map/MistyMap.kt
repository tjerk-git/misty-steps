package com.mistysteps.app.map

import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Mercator
import com.mistysteps.app.maps.MapStyle
import com.mistysteps.app.maps.ProvincePacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.hypot

/**
 * The map: a MapLibre view that draws the installed province packs, reloads its style when packs come or go
 * (keeping the camera), starts where you last were, and follows [target] (your position) as it moves.
 *
 * The map is drawn in a TextureView, so it is composited together with the views above it (the fog).
 * MapLibre's zoom levels are one lower than osmdroid's for the same scale, hence the zoom constants.
 */
class MistyMap(context: Context, private val packs: ProvincePacks, private val target: () -> LatLon?) {
    private val prefs = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
    private val template: String = context.assets.open("maps/misty-style.json").bufferedReader().use { it.readText() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var packWatcher: Job? = null

    val mapView: MapView
    private var map: MapLibreMap? = null

    /** The projection of the map for things drawn above it; sizes are 0 until the map is ready. */
    val projection: MapProjection = object : MapProjection {
        private val live get() = map?.let { MapLibreProjection(it, mapView) }
        override val width get() = mapView.width
        override val height get() = mapView.height
        override val zoom get() = map?.cameraPosition?.zoom ?: 0.0
        override val worldSizePx get() = live?.worldSizePx ?: 1.0
        override fun toScreen(lat: Double, lon: Double) = live?.toScreen(lat, lon) ?: ScreenPoint(0f, 0f)
        override fun fromScreen(x: Float, y: Float) = live?.fromScreen(x, y) ?: LatLon(0.0, 0.0)
        override fun metersToPixels(meters: Double, lat: Double) = live?.metersToPixels(meters, lat) ?: 0f
    }

    private val styleListeners = mutableListOf<(Style) -> Unit>()

    /** [listener] runs after every style load: layers added to the map (the trail) have to be added again each time. */
    fun addStyleListener(listener: (Style) -> Unit) {
        styleListeners += listener
        map?.style?.let(listener)
    }

    /** Called on every camera move, for things drawn above the map. */
    var onCameraMove: (() -> Unit)? = null

    /** A tap or a long-press on the map at screen position (x, y); return true when it was used. */
    var onTap: ((Float, Float) -> Boolean)? = null
    var onLongPress: ((Float, Float) -> Boolean)? = null

    private var following = false
    private var flyInPending = false
    private var resumed = false
    private var pausedAt = 0L
    private var flyingUntil = 0L
    private var lastFollowAt = 0L
    private var followRunning = false
    private val glide = FollowGlide()

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
        watchForDrags(ViewConfiguration.get(context).scaledTouchSlop)
    }

    fun onCreate(state: Bundle?) {
        mapView.onCreate(state)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setMinZoomPreference(MIN_ZOOM)
            m.setMaxZoomPreference(MAX_ZOOM)
            m.addOnCameraMoveListener { onCameraMove?.invoke() }
            m.addOnMapClickListener { at -> m.projection.toScreenLocation(at).let { onTap?.invoke(it.x, it.y) } ?: false }
            m.addOnMapLongClickListener { at -> m.projection.toScreenLocation(at).let { onLongPress?.invoke(it.x, it.y) } ?: false }
            restoreCamera(m)
            applyStyle()
            packWatcher = scope.launch { packs.installedVersion.collect { applyStyle() } }
            if (resumed) startFollowing()
        }
    }

    fun onStart() = mapView.onStart()

    fun onResume() {
        resumed = true
        mapView.onResume()
        // Coming back after a while counts as opening the app again: zoom out and find you.
        val m = map
        if (m != null && pausedAt != 0L && SystemClock.elapsedRealtime() - pausedAt > REOPEN_AFTER_MS && m.cameraPosition.zoom > OPEN_ZOOM) {
            m.moveCamera(CameraUpdateFactory.zoomTo(OPEN_ZOOM))
        }
        startFollowing()
    }

    fun onPause() {
        resumed = false
        pausedAt = SystemClock.elapsedRealtime()
        saveCamera()
        stopFollowing()
        mapView.onPause()
    }

    fun onStop() = mapView.onStop()

    fun onDestroy() {
        packWatcher?.cancel()
        scope.coroutineContext[Job]?.cancel()
        mapView.onDestroy()
    }

    fun onSaveInstanceState(state: Bundle) = mapView.onSaveInstanceState(state)

    /** Draws the packs that are installed now; the camera stays where it is. */
    private fun applyStyle() {
        map?.setStyle(Style.Builder().fromJson(MapStyle.build(template, packs.mapFiles()))) { style ->
            styleListeners.forEach { it(style) }
        }
    }

    /** Looks at a place; stops following you. */
    fun flyTo(lat: Double, lon: Double, zoom: Double = 16.0) {
        following = false
        fly(LatLng(lat, lon), zoom, 800L)
    }

    /** Returns false if there is no position yet. */
    fun flyToMe(): Boolean {
        val me = target() ?: return false
        fly(LatLng(me.lat, me.lon), maxOf(map?.cameraPosition?.zoom ?: OPEN_ZOOM, OPEN_ZOOM), 800L)
        startFollowing()
        return true
    }

    private fun fly(to: LatLng, zoom: Double, ms: Long) {
        flyingUntil = SystemClock.uptimeMillis() + ms + 100
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(to, zoom), ms.toInt())
    }

    // ---- camera: where it starts, and following you --------------------------------------

    private fun restoreCamera(m: MapLibreMap) {
        val place = FogStore.lastKnown?.let { LatLng(it.first, it.second) }
            ?: if (prefs.contains("zoom")) LatLng(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble()) else null
        m.cameraPosition = if (place != null) {
            CameraPosition.Builder().target(place).zoom(OPEN_ZOOM).build()
        } else {
            // No known place: the world, and fly down to you on the first fix.
            flyInPending = true
            CameraPosition.Builder().target(LatLng(30.0, 0.0)).zoom(WORLD_ZOOM).build()
        }
    }

    private fun saveCamera() {
        val c = map?.cameraPosition ?: return
        val at = c.target ?: return
        prefs.edit().putFloat("zoom", c.zoom.toFloat()).putFloat("lat", at.latitude.toFloat()).putFloat("lon", at.longitude.toFloat()).apply()
    }

    private fun startFollowing() {
        following = true
        glide.reset()
        if (resumed && !followRunning && map != null) {
            followRunning = true
            lastFollowAt = SystemClock.uptimeMillis()
            mapView.post(followTick)
        }
    }

    private fun stopFollowing() {
        followRunning = false
        mapView.removeCallbacks(followTick)
    }

    private val followTick = object : Runnable {
        override fun run() {
            val m = map
            if (!followRunning || m == null || !following || !resumed) {
                followRunning = false
                return
            }
            val now = SystemClock.uptimeMillis()
            val dt = now - lastFollowAt
            lastFollowAt = now
            var settled = true
            val me = target()
            val center = m.cameraPosition.target
            if (me != null && center != null && now >= flyingUntil && mapView.width > 0 && mapView.height > 0) {
                val world = MapLibreProjection(m, mapView).worldSizePx
                if (flyInPending) {
                    flyInPending = false
                    fly(LatLng(me.lat, me.lon), OPEN_ZOOM, 1500L)
                    settled = false
                } else {
                    when (val step = glide.step(
                        Mercator.x(center.longitude), Mercator.y(center.latitude),
                        Mercator.x(me.lon), Mercator.y(me.lat), world, mapView.width, mapView.height, dt,
                    )) {
                        is FollowStep.Fly -> {
                            fly(LatLng(me.lat, me.lon), maxOf(m.cameraPosition.zoom, OPEN_ZOOM), 1500L)
                            settled = false
                        }
                        is FollowStep.Glide -> {
                            m.moveCamera(CameraUpdateFactory.newLatLng(LatLng(Mercator.lat(step.y), Mercator.lon(step.x))))
                            settled = step.settled
                        }
                        FollowStep.Hold -> Unit
                    }
                }
            }
            // The glide is slow, so about 30 frames a second looks as smooth as 60 and costs half.
            mapView.postDelayed(this, if (settled) 250L else 33L)
        }
    }

    /** A one-finger drag means you want to look around: stop following. */
    private fun watchForDrags(slop: Int) {
        var downX = 0f
        var downY = 0f
        var multi = false
        mapView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; multi = false }
                MotionEvent.ACTION_POINTER_DOWN -> multi = true
                MotionEvent.ACTION_MOVE -> if (!multi && following && hypot(e.x - downX, e.y - downY) > slop) following = false
            }
            false // only watches; the map still gets every event
        }
    }

    companion object {
        /** How far out the camera starts when you open the app. */
        const val OPEN_ZOOM = 14.5
        const val WORLD_ZOOM = 2.0
        const val MIN_ZOOM = 2.0
        const val MAX_ZOOM = 19.0

        /** Back after this long counts as opening the app again. */
        const val REOPEN_AFTER_MS = 5 * 60_000L
    }
}

/** [MapProjection] over a MapLibre map. */
class MapLibreProjection(private val map: MapLibreMap, private val view: android.view.View) : MapProjection {
    override val width get() = view.width
    override val height get() = view.height
    override val zoom get() = map.cameraPosition.zoom

    /**
     * The world's width in screen pixels: the earth's circumference over the metres one MapLibre pixel covers at the equator,
     * times the screen density (MapLibre counts in density-independent pixels).
     */
    override val worldSizePx get() = EARTH_CIRCUMFERENCE / map.projection.getMetersPerPixelAtLatitude(0.0) * view.resources.displayMetrics.density

    override fun toScreen(lat: Double, lon: Double): ScreenPoint {
        val p = map.projection.toScreenLocation(LatLng(lat, lon))
        return ScreenPoint(p.x, p.y)
    }

    override fun fromScreen(x: Float, y: Float): LatLon {
        val at = map.projection.fromScreenLocation(PointF(x, y))
        return LatLon(at.latitude, at.longitude)
    }

    /** Worked out from the world size, like [MercatorProjection], so it agrees with the fog's scale. */
    override fun metersToPixels(meters: Double, lat: Double): Float =
        (meters * worldSizePx / (EARTH_CIRCUMFERENCE * kotlin.math.cos(Math.toRadians(lat)))).toFloat()

    private companion object {
        const val EARTH_CIRCUMFERENCE = 40_075_016.686
    }
}

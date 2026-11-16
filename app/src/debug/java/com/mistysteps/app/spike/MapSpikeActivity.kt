package com.mistysteps.app.spike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.mistysteps.app.maps.MapStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Throwaway probe for the offline map plan (debug builds only). Shows every `*.pmtiles` in
 * `Android/data/<app id>/files/spike/` with the Misty style, and a transparent view above the map
 * that draws fixed coordinates, to see whether it stays glued to the streets while panning.
 *
 *   adb shell am start -n com.potloodgum.mistysteps.debug/com.mistysteps.app.spike.MapSpikeActivity
 */
class MapSpikeActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var overlay: SyncOverlay
    lateinit var styleJson: String
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        val packs = getExternalFilesDir("spike")?.listFiles { f -> f.extension == "pmtiles" }?.sortedBy { it.name }.orEmpty()
        Log.i(TAG, "packs: ${packs.joinToString { "${it.name} ${it.length()} bytes" }}")
        val template = assets.open("maps/misty-style.json").bufferedReader().use { it.readText() }
        styleJson = MapStyle.build(template, packs)

        val root = FrameLayout(this)
        mapView = MapView(this)
        overlay = SyncOverlay(this)
        root.addView(mapView)
        root.addView(overlay)
        setContentView(root)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson)) { Log.i(TAG, "style loaded") }
            map.cameraPosition = CameraPosition.Builder().target(OLDEHOVE).zoom(15.0).build()
            overlay.map = map
            map.addOnCameraMoveListener { overlay.invalidate() }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); overlay.start() }
    override fun onPause() { overlay.stop(); mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        const val TAG = "MapSpike"
        val OLDEHOVE = LatLng(53.2014, 5.7936)
    }
}

/**
 * Sits above the map and is not clickable, so touches reach the map. Draws a 50 m circle at the
 * Oldehove and a 3 x 3 grid of crosses at fixed coordinates, through the map's own projection.
 * Redraws from the camera listener and on every frame.
 */
class SyncOverlay(context: Context) : View(context) {
    var map: MapLibreMap? = null
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66B4536A; style = Paint.Style.FILL }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f }
    private var running = false
    private var last = 0L
    private var frameMs = 0L

    init { isClickable = false; isFocusable = false }

    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = frameTimeNanos / 1_000_000
            frameMs = now - last
            last = now
            invalidate()
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() { running = true; Choreographer.getInstance().postFrameCallback(tick) }
    fun stop() { running = false }

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        val projection = map.projection
        val center = projection.toScreenLocation(MapSpikeActivity.OLDEHOVE)
        val metersPerPixel = projection.getMetersPerPixelAtLatitude(MapSpikeActivity.OLDEHOVE.latitude)
        canvas.drawCircle(center.x, center.y, (50 / metersPerPixel).toFloat(), ring)
        for (dy in -1..1) for (dx in -1..1) {
            val p = projection.toScreenLocation(LatLng(MapSpikeActivity.OLDEHOVE.latitude + dy * 0.002, MapSpikeActivity.OLDEHOVE.longitude + dx * 0.003))
            canvas.drawLine(p.x - 20, p.y, p.x + 20, p.y, cross)
            canvas.drawLine(p.x, p.y - 20, p.x, p.y + 20, cross)
        }
        canvas.drawText("frame ${frameMs} ms  zoom ${"%.1f".format(map.cameraPosition.zoom)}", 24f, 120f, text)
    }
}

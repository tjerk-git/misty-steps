package com.mistysteps.app.spike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.mistysteps.app.maps.MapStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

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
        // --ei texture 1: draw the map into a TextureView, so it is composited together with the overlay.
        mapView = if (intent.getIntExtra("texture", 0) == 1) MapView(this, MapLibreMapOptions.createFromAttributes(this).textureMode(true)) else MapView(this)
        overlay = SyncOverlay(this)
        root.addView(mapView)
        root.addView(overlay)
        // Probe 4: render a postcard-sized map with the snapshotter from the same style and local packs.
        // Tap the button, or start with --ez snap true (and --ed lat/lon to pick the box). Tap the image to close.
        val image = ImageView(this).apply { visibility = View.GONE; scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.WHITE); setOnClickListener { visibility = View.GONE } }
        val button = Button(this).apply { text = "Snapshot"; setOnClickListener { snapshot(image) } }
        root.addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        root.addView(image)
        setContentView(root)
        if (intent.getBooleanExtra("snap", false)) root.post { snapshot(image) }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                // Blue dots drawn by MapLibre itself at the same coordinates as the overlay's red crosses,
                // so the gap between the two can be measured from a screen recording.
                val points = SyncOverlay.GRID.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                style.addSource(GeoJsonSource("sync", FeatureCollection.fromFeatures(points)))
                style.addLayer(CircleLayer("sync-dots", "sync").withProperties(circleColor("#0000FF"), circleRadius(7f)))
                Log.i(TAG, "style loaded")
            }
            // Optional start position: am start ... --ed lat 53.2 --ed lon 5.8 --ed zoom 15
            val start = LatLng(intent.getDoubleExtra("lat", OLDEHOVE.latitude), intent.getDoubleExtra("lon", OLDEHOVE.longitude))
            map.cameraPosition = CameraPosition.Builder().target(start).zoom(intent.getDoubleExtra("zoom", 15.0)).build()
            overlay.map = map
            // mode 0: redraw from camera moves and every Choreographer frame. mode 1: redraw only when MapLibre
            // has finished drawing a frame. --ei mode 1
            if (intent.getIntExtra("mode", 0) == 0) {
                map.addOnCameraMoveListener { overlay.invalidate() }
            } else {
                overlay.useChoreographer = false; overlay.stop()
                mapView.addOnDidFinishRenderingFrameListener { _, _, _ -> overlay.invalidate() }
            }
        }
    }

    private fun snapshot(into: ImageView) {
        val lat = intent.getDoubleExtra("lat", OLDEHOVE.latitude)
        val lon = intent.getDoubleExtra("lon", OLDEHOVE.longitude)
        // About 400 m across, like a short walk's postcard.
        val bounds = LatLngBounds.from(lat + 0.0018, lon + 0.003, lat - 0.0018, lon - 0.003)
        val options = MapSnapshotter.Options(1080, 1350)
            .withStyleBuilder(Style.Builder().fromJson(styleJson))
            .withRegion(bounds)
            .withPixelRatio(1f)
        val started = SystemClock.elapsedRealtime()
        val snapshotter = MapSnapshotter(this, options)
        snapshotter.start({ snap ->
            Log.i(TAG, "snapshot done in ${SystemClock.elapsedRealtime() - started} ms, ${snap.bitmap.width}x${snap.bitmap.height}")
            into.setImageBitmap(snap.bitmap)
            into.visibility = View.VISIBLE
        }, { error -> Log.e(TAG, "snapshot failed: $error") })
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
    var useChoreographer = true
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66B4536A; style = Paint.Style.FILL }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f }
    private var running = false
    private var last = 0L
    private var frameMs = 0L

    init { isClickable = false; isFocusable = false }

    companion object {
        /** 3 x 3 fixed coordinates around the Oldehove, shared with the map's own dot layer. */
        val GRID: List<LatLng> = (-1..1).flatMap { dy ->
            (-1..1).map { dx -> LatLng(MapSpikeActivity.OLDEHOVE.latitude + dy * 0.002, MapSpikeActivity.OLDEHOVE.longitude + dx * 0.003) }
        }
    }

    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = frameTimeNanos / 1_000_000
            frameMs = now - last
            last = now
            invalidate()
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() { running = true; if (useChoreographer) Choreographer.getInstance().postFrameCallback(tick) }
    fun stop() { running = false }

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        val projection = map.projection
        val center = projection.toScreenLocation(MapSpikeActivity.OLDEHOVE)
        val metersPerPixel = projection.getMetersPerPixelAtLatitude(MapSpikeActivity.OLDEHOVE.latitude)
        canvas.drawCircle(center.x, center.y, (50 / metersPerPixel).toFloat(), ring)
        for (at in GRID) {
            val p = projection.toScreenLocation(at)
            canvas.drawLine(p.x - 20, p.y, p.x + 20, p.y, cross)
            canvas.drawLine(p.x, p.y - 20, p.x, p.y + 20, cross)
        }
        canvas.drawText("frame ${frameMs} ms  zoom ${"%.1f".format(map.cameraPosition.zoom)}", 24f, 120f, text)
    }
}

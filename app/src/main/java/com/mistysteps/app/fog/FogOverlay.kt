package com.mistysteps.app.fog

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.collection.MutableLongSet
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Mercator
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.max

/**
 * Covers the whole map in drifting white mist, with soft holes punched out wherever
 * you've walked.
 *
 * Drawing is kept cheap, because it runs many times a second while the map is open:
 *  1. a half-resolution "cleared" alpha mask is rebuilt on the CPU only when the view or the
 *     trail changes (so the GPU only receives it again then);
 *  2. each frame, the veil and two drifting swirl layers are drawn straight onto the map's
 *     (GPU) canvas in an offscreen layer, and the mask is cut out of them, stretched to full size.
 * The mist drifts at [FRAME_MS] and stands still while battery saver is on.
 */
class FogOverlay : Overlay() {

    private val resolution = 0.5f

    private var mask: Bitmap? = null
    private var maskCanvas: Canvas? = null

    // Mask cache key
    private var maskOffX = Double.NaN
    private var maskOffY = Double.NaN
    private var maskK = Double.NaN
    private var maskVersion = -1

    private val basePaint = Paint().apply { color = Color.argb(236, 244, 245, 250) }

    /** The two swirl layers. Generating them takes a moment, so it happens off the main thread. */
    private class Swirl(val shadow: Paint, val light: Paint)
    private var swirl: Swirl? = null // main thread only
    private val shadowScale = 3.0f
    private val lightScale = 1.7f
    private val shadowMatrix = Matrix()
    private val lightMatrix = Matrix()

    init {
        val main = Handler(Looper.getMainLooper())
        Thread({
            fun paint(b: Bitmap) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            val s = Swirl(
                shadow = paint(FogTexture.generate(SWIRL_TILE, seed = 7, tint = Color.rgb(176, 184, 206), maxAlpha = 0.55f, contrast = 1.6f)),
                light = paint(FogTexture.generate(SWIRL_TILE, seed = 23, tint = Color.WHITE, maxAlpha = 0.85f, contrast = 1.8f)),
            )
            main.post {
                swirl = s
                animatedView?.invalidate()
            }
        }, "fog-texture").start()
    }

    /** Soft round brush: fully clear in the middle, feathering out to the edge. */
    private val brushSize = 128
    private val brush: Bitmap = Bitmap.createBitmap(brushSize, brushSize, Bitmap.Config.ARGB_8888).also {
        val c = brushSize / 2f
        Canvas(it).drawCircle(c, c, c, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                c, c, c,
                intArrayOf(Color.BLACK, Color.BLACK, Color.argb(160, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.72f, 0.88f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
    }
    private val brushPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val cutPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val rect = RectF()
    private val layerRect = RectF()
    private val clip = Rect()
    private val refPx = Point()
    private val seen = MutableLongSet()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        if (mapView.width == 0 || mapView.height == 0) return

        // osmdroid may have concatenated a matrix onto the canvas, so work in canvas
        // coordinates: cover whatever part of the canvas is visible, and anchor to the
        // map through the projection's own toPixels().
        if (!canvas.getClipBounds(clip)) return
        val fw = ceil(clip.width() * resolution).toInt()
        val fh = ceil(clip.height() * resolution).toInt()
        if (fw <= 0 || fh <= 0) return
        ensureBuffers(fw, fh)
        val mask = mask!!

        val proj = mapView.projection
        val ref = proj.fromPixels(mapView.width / 2, mapView.height / 2)
        proj.toPixels(ref, refPx)
        // Fog pixels per mercator unit, and the fog-pixel position of mercator (0, 0).
        val k = TileSystem.MapSize(proj.zoomLevel) * resolution
        val offX = (refPx.x - clip.left) * resolution.toDouble() - Mercator.x(ref.longitude) * k
        val offY = (refPx.y - clip.top) * resolution.toDouble() - Mercator.y(ref.latitude) * k

        val version = FogStore.version.value
        if (offX != maskOffX || offY != maskOffY || k != maskK || version != maskVersion) {
            rebuildMask(offX, offY, k, fw, fh)
            maskOffX = offX; maskOffY = offY; maskK = k; maskVersion = version
        }

        updatePalette(mapView)

        // Mist: an even veil plus two swirling layers that follow the map and drift slowly.
        // Everything below runs on the GPU; the layer keeps the cut-out from touching the map.
        if (drifting) driftTime = SystemClock.uptimeMillis() / 1000.0
        val t = driftTime
        layerRect.set(clip)
        val save = canvas.saveLayer(layerRect, null)
        canvas.drawPaint(basePaint)
        swirl?.let {
            layer(canvas, it.shadow, shadowMatrix, shadowScale, offX + t * 5.0, offY + t * 1.6)
            layer(canvas, it.light, lightMatrix, lightScale, offX - t * 3.5, offY + t * 2.4)
        }
        rect.set(
            clip.left.toFloat(), clip.top.toFloat(),
            clip.left + fw / resolution, clip.top + fh / resolution,
        )
        canvas.drawBitmap(mask, null, rect, cutPaint)
        canvas.restoreToCount(save)
    }

    /** Seconds of drift shown; frozen while the mist stands still (battery saver). */
    private var driftTime = SystemClock.uptimeMillis() / 1000.0
    private var drifting = true

    // ---- colour through the day -----------------------------------------------------------

    private var paletteAt = Long.MIN_VALUE
    private var shadowFilter: LightingColorFilter? = null
    private var lightFilter: LightingColorFilter? = null

    /** Re-tints the mist once a minute, following the sun. */
    private fun updatePalette(mapView: MapView) {
        val now = System.currentTimeMillis()
        if (now - paletteAt < 60_000) {
            applyFilters()
            return
        }
        paletteAt = now
        val (lat, lon) = FogStore.lastKnown ?: mapView.mapCenter.let { it.latitude to it.longitude }
        val colors = MistPalette.at(now, lat, lon)
        basePaint.color = colors.base
        shadowFilter = LightingColorFilter(colors.shadowTint, 0)
        lightFilter = LightingColorFilter(colors.lightTint, 0)
        applyFilters()
    }

    private fun applyFilters() {
        val s = swirl ?: return
        if (s.shadow.colorFilter !== shadowFilter) s.shadow.colorFilter = shadowFilter
        if (s.light.colorFilter !== lightFilter) s.light.colorFilter = lightFilter
    }

    /** One swirl layer. [tx]/[ty] are in half-resolution fog pixels; drawn at full size in canvas pixels. */
    private fun layer(c: Canvas, paint: Paint, m: Matrix, scale: Float, tx: Double, ty: Double) {
        val period = SWIRL_TILE * scale.toDouble()
        val s = scale / resolution
        m.setScale(s, s)
        m.postTranslate(
            clip.left + (tx.mod(period) / resolution).toFloat(),
            clip.top + (ty.mod(period) / resolution).toFloat(),
        )
        paint.shader.setLocalMatrix(m)
        c.drawPaint(paint)
    }

    /** Paints every cleared spot into the mask. Fog pixel = mercator * k + off. */
    private fun rebuildMask(offX: Double, offY: Double, k: Double, fw: Int, fh: Int) {
        val maskCanvas = maskCanvas!!
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Pad the lookup so spots just off-screen still bleed in.
        val pad = Mercator.metersToUnits(FogStore.START_CLEAR_RADIUS_M, 80.0) + 4 / k
        val minX = -offX / k - pad
        val maxX = (fw - offX) / k + pad
        val minY = -offY / k - pad
        val maxY = (fh - offY) / k + pad

        // When zoomed out, spots shrink below a pixel; keep them visible as faint specks
        // and skip ones landing on an already-drawn pixel.
        val minRadiusPx = 1.2f
        seen.clear()

        FogStore.index.forEachBucketIn(minX, minY, maxX, maxY) { b ->
            val d = b.data
            for (i in 0 until b.size) {
                val j = 3 * i
                val px = (d[j] * k + offX).toFloat()
                val py = (d[j + 1] * k + offY).toFloat()
                val r = max((d[j + 2] * k).toFloat(), minRadiusPx)
                if (px + r < 0 || py + r < 0 || px - r > fw || py - r > fh) continue
                if (r < 3f) {
                    val key = (px.toInt().toLong() shl 32) or (py.toInt().toLong() and 0xffffffffL)
                    if (!seen.add(key)) continue
                    maskCanvas.drawCircle(px, py, r, dotPaint)
                } else {
                    rect.set(px - r, py - r, px + r, py + r)
                    maskCanvas.drawBitmap(brush, null, rect, brushPaint)
                }
            }
        }
    }

    private fun ensureBuffers(fw: Int, fh: Int) {
        val current = mask
        if (current != null && current.width == fw && current.height == fh) return
        current?.recycle()
        mask = Bitmap.createBitmap(fw, fh, Bitmap.Config.ALPHA_8).also { maskCanvas = Canvas(it) }
        maskVersion = -1
    }

    // ---- gentle animation -------------------------------------------------------------

    private var animatedView: MapView? = null
    private val tick = object : Runnable {
        override fun run() {
            val v = animatedView ?: return
            // Battery saver on: let the mist stand still; check again now and then.
            val saver = v.context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            drifting = !saver
            if (drifting) {
                v.invalidate()
                v.postDelayed(this, FRAME_MS)
            } else {
                v.postDelayed(this, 5_000)
            }
        }
    }

    /** Keep the mist drifting while [mapView] is visible. */
    fun startDrifting(mapView: MapView) {
        stopDrifting()
        animatedView = mapView
        mapView.postDelayed(tick, FRAME_MS)
    }

    fun stopDrifting() {
        animatedView?.removeCallbacks(tick)
        animatedView = null
    }

    override fun onDetach(mapView: MapView?) {
        stopDrifting()
        mask?.recycle(); mask = null
        super.onDetach(mapView)
    }

    private companion object {
        /** ~12 frames a second: the drift is slow enough that more frames only cost battery. */
        const val FRAME_MS = 80L
        const val SWIRL_TILE = 256
    }
}

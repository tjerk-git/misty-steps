package com.mistysteps.app.trail

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Mercator
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * The trail of little steps you leave behind: bright for the walk you're on,
 * faint for earlier walks. Drawn above the fog, inside the clearings it made.
 */
class TrailOverlay(private val density: Float) : Overlay() {

    private fun dots(color: Int, dotDp: Float, gapDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dotDp * density
        // A near-zero dash with round caps draws a row of dots.
        pathEffect = DashPathEffect(floatArrayOf(0.01f, gapDp * density), 0f)
    }

    private val currentPaint = dots(0xFFB4536A.toInt(), dotDp = 4.5f, gapDp = 9f)
    private val pastPaint = dots(0x8C5B6B8C.toInt(), dotDp = 3f, gapDp = 8f)

    /** Skip spots closer than this to the last one drawn; they'd only add noise. */
    private val minStepPx = 2f * density

    private val current = Path()
    private val past = Path()
    private val clip = Rect()
    private val refPx = Point()

    // Path cache key
    private var cacheOffX = Double.NaN
    private var cacheOffY = Double.NaN
    private var cacheK = Double.NaN
    private var cacheVersion = -1

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        if (!canvas.getClipBounds(clip)) return

        // Same anchoring as the fog: canvas pixel = mercator * k + off.
        val proj = mapView.projection
        val ref = proj.fromPixels(mapView.width / 2, mapView.height / 2)
        proj.toPixels(ref, refPx)
        val k = TileSystem.MapSize(proj.zoomLevel)
        val offX = refPx.x - Mercator.x(ref.longitude) * k
        val offY = refPx.y - Mercator.y(ref.latitude) * k

        val version = FogStore.version.value
        if (offX != cacheOffX || offY != cacheOffY || k != cacheK || version != cacheVersion) {
            rebuild(offX, offY, k)
            cacheOffX = offX; cacheOffY = offY; cacheK = k; cacheVersion = version
        }
        canvas.drawPath(past, pastPaint)
        canvas.drawPath(current, currentPaint)
    }

    private fun rebuild(offX: Double, offY: Double, k: Double) {
        current.rewind()
        past.rewind()
        val pad = 16.0 / k
        val minX = (clip.left - offX) / k - pad
        val maxX = (clip.right - offX) / k + pad
        val minY = (clip.top - offY) / k - pad
        val maxY = (clip.bottom - offY) / k + pad
        val now = FogStore.session?.id

        for (seg in FogStore.trails.segments) {
            if (seg.size < 2) continue
            if (seg.maxX < minX || seg.minX > maxX || seg.maxY < minY || seg.minY > maxY) continue
            val path = if (now != null && seg.sessionId == now) current else past
            val xy = seg.xy
            var lx = (xy[0] * k + offX).toFloat()
            var ly = (xy[1] * k + offY).toFloat()
            path.moveTo(lx, ly)
            val lastI = seg.size - 1
            for (i in 1..lastI) {
                val x = (xy[2 * i] * k + offX).toFloat()
                val y = (xy[2 * i + 1] * k + offY).toFloat()
                val dx = x - lx
                val dy = y - ly
                if (i != lastI && dx * dx + dy * dy < minStepPx * minStepPx) continue
                path.lineTo(x, y)
                lx = x; ly = y
            }
        }
    }
}

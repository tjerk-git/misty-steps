package com.mistysteps.app.pin

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import com.mistysteps.app.character.PixelArt
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PinStore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws your pins as little pixel icons (dots when zoomed out), opens one when tapped, and
 * turns a long-press on cleared ground into a new pin.
 */
class PinOverlay(private val density: Float) : Overlay() {

    var onPinTap: ((Pin) -> Unit)? = null
    var onClearedLongPress: ((lat: Double, lon: Double) -> Unit)? = null
    var onFogLongPress: (() -> Unit)? = null

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = PixelArt.OUTLINE
        strokeWidth = density
    }
    private val point = Point()
    private val dst = RectF()
    private val drop = OvershootInterpolator(2.5f)

    private val iconPx get() = PinSprites.SIZE * PIXEL_DP * density

    /** Which pin is dropping in, and since when. */
    private var droppingId: Long? = null
    private var dropStart = 0L

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        val mode = PinRules.mode(mapView.zoomLevelDouble)
        if (mode == PinMode.HIDDEN) return
        val pins = PinStore.pins.value
        if (pins.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        val placed = PinStore.justPlaced.value
        if (placed != null && placed != droppingId) {
            droppingId = placed
            dropStart = now
        }
        var animating = false

        val size = iconPx
        val w = mapView.width
        val h = mapView.height
        // Oldest first, so newer pins are drawn on top.
        for (i in pins.indices.reversed()) {
            val pin = pins[i]
            mapView.projection.toPixels(GeoPoint(pin.lat, pin.lon), point)
            if (point.x < -size || point.x > w + size || point.y < -size || point.y > h + 2 * size) continue
            if (mode == PinMode.DOT) {
                val r = DOT_DP * density
                dotPaint.color = PinSprites.dotColor(pin.label)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, dotPaint)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, ringPaint)
                continue
            }
            var lift = 0f
            if (pin.id == droppingId) {
                val t = ((now - dropStart) / DROP_MS.toFloat()).coerceIn(0f, 1f)
                lift = DROP_DP * density * (1 - drop.getInterpolation(t))
                if (t < 1f) animating = true
            }
            canvas.drawOval(
                point.x - size * 0.25f, point.y - size * 0.06f,
                point.x + size * 0.25f, point.y + size * 0.06f, shadowPaint,
            )
            val left = point.x - size / 2
            val top = point.y - size * 0.95f - lift
            dst.set(left, top, left + size, top + size)
            canvas.drawBitmap(PinSprites.bitmap(pin.label), null, dst, pixelPaint)
        }
        if (animating) mapView.postInvalidateOnAnimation()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val mode = PinRules.mode(mapView.zoomLevelDouble)
        if (mode == PinMode.HIDDEN) return false
        val size = iconPx
        val p = Point()
        val hit = PinRules.pickPin(PinStore.pins.value, e.x, e.y, PinRules.TAP_RADIUS_DP * density) { pin ->
            mapView.projection.toPixels(GeoPoint(pin.lat, pin.lon), p)
            // Icons stand above their spot; dots sit on it.
            val cy = if (mode == PinMode.ICON) p.y - size * 0.45f else p.y.toFloat()
            p.x.toFloat() to cy
        } ?: return false
        onPinTap?.invoke(hit)
        return true
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val where = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        if (PinRules.isCleared(FogStore.index, where.latitude, where.longitude)) {
            onClearedLongPress?.invoke(where.latitude, where.longitude)
        } else {
            onFogLongPress?.invoke()
        }
        return true
    }

    private companion object {
        /** Each sprite pixel is this many dp on screen. */
        const val PIXEL_DP = 2f
        const val DOT_DP = 4f
        const val DROP_DP = 12f
        const val DROP_MS = 400L
    }
}

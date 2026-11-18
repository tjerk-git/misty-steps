package com.mistysteps.app.pin

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.animation.OvershootInterpolator
import com.mistysteps.app.character.PixelArt
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.map.MapProjection

/**
 * Draws your pins on top of the map through [MapProjection]: pixel icons when zoomed in, coloured dots when zoomed out,
 * with a small drop-in for a pin just placed. Taps are found with [hitTest].
 */
class PinPainter(private val density: Float) {
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = PixelArt.OUTLINE
        strokeWidth = density
    }
    private val dst = RectF()
    private val drop = OvershootInterpolator(2.5f)

    private val iconPx get() = PinSprites.SIZE * PIXEL_DP * density

    /** Which pin is dropping in, and since when. */
    private var droppingId: Long? = null
    private var dropStart = 0L

    fun draw(canvas: Canvas, proj: MapProjection, host: View) {
        val mode = PinRules.mode(proj.zoom + ZOOM_OFFSET)
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
        val w = proj.width
        val h = proj.height
        // Oldest first, so newer pins are drawn on top.
        for (i in pins.indices.reversed()) {
            val pin = pins[i]
            val p = proj.toScreen(pin.lat, pin.lon)
            if (p.x < -size || p.x > w + size || p.y < -size || p.y > h + 2 * size) continue
            if (mode == PinMode.DOT) {
                val r = DOT_DP * density
                dotPaint.color = PinSprites.dotColor(pin.label)
                canvas.drawCircle(p.x, p.y, r, dotPaint)
                canvas.drawCircle(p.x, p.y, r, ringPaint)
                continue
            }
            var lift = 0f
            if (pin.id == droppingId) {
                val t = ((now - dropStart) / DROP_MS.toFloat()).coerceIn(0f, 1f)
                lift = DROP_DP * density * (1 - drop.getInterpolation(t))
                if (t < 1f) animating = true
            }
            canvas.drawOval(p.x - size * 0.25f, p.y - size * 0.06f, p.x + size * 0.25f, p.y + size * 0.06f, shadowPaint)
            val left = p.x - size / 2
            val top = p.y - size * 0.95f - lift
            dst.set(left, top, left + size, top + size)
            canvas.drawBitmap(PinSprites.bitmap(pin.label), null, dst, pixelPaint)
        }
        if (animating) host.postInvalidateOnAnimation()
    }

    /** The pin under a tap at ([x], [y]), if any. */
    fun hitTest(proj: MapProjection, x: Float, y: Float): Pin? {
        val mode = PinRules.mode(proj.zoom + ZOOM_OFFSET)
        if (mode == PinMode.HIDDEN) return null
        val size = iconPx
        return PinRules.pickPin(PinStore.pins.value, x, y, PinRules.TAP_RADIUS_DP * density) { pin ->
            val p = proj.toScreen(pin.lat, pin.lon)
            // Icons stand above their spot; dots sit on it.
            p.x to (if (mode == PinMode.ICON) p.y - size * 0.45f else p.y)
        }
    }

    private companion object {
        /** Each sprite pixel is this many dp on screen. */
        const val PIXEL_DP = 2f
        const val DOT_DP = 4f
        const val DROP_DP = 12f
        const val DROP_MS = 400L

        /** `PinRules.mode` thinks in osmdroid zoom levels; MapLibre's are one lower for the same scale. */
        const val ZOOM_OFFSET = 1.0
    }
}

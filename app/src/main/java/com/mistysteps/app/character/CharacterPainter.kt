package com.mistysteps.app.character

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Trails
import com.mistysteps.app.map.Fix
import com.mistysteps.app.map.MapProjection

/**
 * Draws your chosen character where you are, walking while you move and facing the way you're heading, with the ring that
 * shows how far one step clears the mist. Position comes from [fix]; drawing goes through [MapProjection].
 */
class CharacterPainter(private val density: Float, private val fix: () -> Fix?) {
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33B4536A; style = Paint.Style.FILL }
    private val ringEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3B4536A.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * density
    }
    private val dst = RectF()
    private val flip = Matrix()

    private var facingLeft = false
    private var lastPos: Fix? = null
    private var lastMoveAt = 0L

    fun draw(canvas: Canvas, proj: MapProjection) {
        val character = Characters.selected.value ?: return
        val where = fix() ?: return
        val now = SystemClock.uptimeMillis()
        val walking = updateMotion(where, now)

        val p = proj.toScreen(where.lat, where.lon)
        // The clearing radius, so you can see how much mist a step takes away.
        val ring = proj.metersToPixels(FogStore.CLEAR_RADIUS_M, where.lat)
        canvas.drawCircle(p.x, p.y, ring, ringFill)
        canvas.drawCircle(p.x, p.y, ring, ringEdge)

        val size = Characters.SIZE * PIXEL_DP * density
        // Feet on the spot, so the sprite stands slightly above the point.
        val left = p.x - size / 2
        val top = p.y - size * 0.92f

        canvas.drawOval(p.x - size * 0.28f, p.y - size * 0.07f, p.x + size * 0.28f, p.y + size * 0.07f, shadowPaint)

        val frame = Characters.frameBitmapAt(character, walking, now)
        dst.set(left, top, left + size, top + size)
        if (facingLeft) {
            canvas.save()
            flip.setScale(-1f, 1f, p.x, 0f)
            canvas.concat(flip)
            canvas.drawBitmap(frame, null, dst, pixelPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(frame, null, dst, pixelPaint)
        }
    }

    /** Works out whether we're walking and which way we face. */
    private fun updateMotion(f: Fix, now: Long): Boolean {
        val prev = lastPos
        if (prev == null) {
            lastPos = f
        } else if (Trails.approxDistanceM(prev.lat, prev.lon, f.lat, f.lon) >= maxOf(5.0, (f.accuracy ?: 0f) * 0.5)) {
            val dLon = f.lon - prev.lon
            if (kotlin.math.abs(dLon) > 1e-7) facingLeft = dLon < 0
            lastPos = f
            lastMoveAt = now
        }
        val bearing = f.bearing
        val speed = f.speed
        if (bearing != null && speed != null && speed > 0.8f) facingLeft = bearing > 180f
        val moving = speed != null && speed > 0.6f
        return moving || now - lastMoveAt < 3_000
    }

    private companion object {
        /** Each sprite pixel is this many dp on screen. */
        const val PIXEL_DP = 2.6f
    }
}

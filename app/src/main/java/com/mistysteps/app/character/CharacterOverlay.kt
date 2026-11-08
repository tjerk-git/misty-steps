package com.mistysteps.app.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.location.Location
import android.os.SystemClock
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Draws your chosen character where you are, walking while you move and facing the
 * way you're heading. Position comes from osmdroid's [MyLocationNewOverlay], whose own
 * marker is hidden.
 */
class CharacterOverlay(private val me: MyLocationNewOverlay, private val density: Float) : Overlay() {

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val point = Point()
    private val dst = RectF()
    private val flip = Matrix()

    private var facingLeft = false
    private var lastPos: Location? = null
    private var lastMoveAt = 0L

    init {
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        me.setPersonIcon(blank)
        me.setDirectionArrow(blank, blank)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        val character = Characters.selected.value ?: return
        val where = me.myLocation ?: return
        val now = SystemClock.uptimeMillis()
        val walking = updateMotion(me.lastFix, now)

        mapView.projection.toPixels(where, point)
        val size = Characters.SIZE * PIXEL_DP * density
        // Feet on the spot, so the sprite stands slightly above the point.
        val left = point.x - size / 2
        val top = point.y - size * 0.92f

        canvas.drawOval(point.x - size * 0.28f, point.y - size * 0.07f, point.x + size * 0.28f, point.y + size * 0.07f, shadowPaint)

        val frame = Characters.frameAt(character, walking, now)
        dst.set(left, top, left + size, top + size)
        if (facingLeft) {
            canvas.save()
            flip.setScale(-1f, 1f, point.x.toFloat(), 0f)
            canvas.concat(flip)
            canvas.drawBitmap(frame, null, dst, pixelPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(frame, null, dst, pixelPaint)
        }
    }

    /** Works out whether we're walking and which way we face. */
    private fun updateMotion(fix: Location?, now: Long): Boolean {
        if (fix == null) return false
        val prev = lastPos
        if (prev == null) {
            lastPos = fix
        } else if (prev.distanceTo(fix) >= maxOf(5f, fix.accuracy * 0.5f)) {
            val dLon = fix.longitude - prev.longitude
            if (kotlin.math.abs(dLon) > 1e-7) facingLeft = dLon < 0
            lastPos = fix
            lastMoveAt = now
        }
        if (fix.hasBearing() && fix.hasSpeed() && fix.speed > 0.8f) {
            facingLeft = fix.bearing > 180f
        }
        val moving = fix.hasSpeed() && fix.speed > 0.6f
        return moving || now - lastMoveAt < 3_000
    }

    private companion object {
        /** Each sprite pixel is this many dp on screen. */
        const val PIXEL_DP = 2.6f
    }
}

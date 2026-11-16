package com.mistysteps.app.map

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.mistysteps.app.character.CharacterPainter
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.pin.PinPainter

/**
 * Your pins and your character, on a transparent view above the map (not clickable: touches go to the map).
 * Redraws when the camera moves and, while visible, about 12 times a second for the walk cycle and the pin drop.
 */
class MarkersView(context: Context, private val projection: MapProjection, fix: () -> Fix?) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val pins = PinPainter(density)
    private val character = CharacterPainter(density, fix)
    private var ticking = false

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        if (projection.width == 0 || projection.height == 0) return
        pins.draw(canvas, projection, this)
        character.draw(canvas, projection)
    }

    fun moved() = invalidate()

    fun pinAt(x: Float, y: Float): Pin? = pins.hitTest(projection, x, y)

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

    fun startTicking() {
        if (ticking) return
        ticking = true
        postDelayed(tick, FRAME_MS)
    }

    fun stopTicking() {
        ticking = false
        removeCallbacks(tick)
    }

    private companion object {
        const val FRAME_MS = 80L
    }
}

package com.mistysteps.app.fog

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.mistysteps.app.map.MapProjection

/**
 * The mist as a transparent view above the map. It does not take touches (they go to the map) and draws through
 * [projection], so it follows the map; call [moved] when the camera moves.
 */
class FogView(context: Context, private val projection: MapProjection) : View(context) {
    private val painter = FogPainter()

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) = painter.draw(canvas, projection, this)

    fun moved() = invalidate()

    /** Keeps the mist drifting; call when the map is visible. */
    fun startDrifting() = painter.startDrifting(this)

    fun stopDrifting() = painter.stopDrifting()

    fun release() = painter.detach()
}

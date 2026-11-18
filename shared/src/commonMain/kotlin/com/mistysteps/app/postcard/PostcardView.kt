package com.mistysteps.app.postcard

import com.mistysteps.app.data.Mercator
import com.mistysteps.app.data.TrailPoint
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Which part of the world a postcard map shows: mercator centre and pixels per mercator unit, for a [w] x [h] picture. */
class PostcardView(val cx: Double, val cy: Double, val scale: Double, val w: Int, val h: Int) {
    fun x(lon: Double) = (w / 2.0 + (Mercator.x(lon) - cx) * scale).toFloat()
    fun y(lat: Double) = (h / 2.0 + (Mercator.y(lat) - cy) * scale).toFloat()

    /** The zoom of a standard 256-pixel-tile map. */
    val zoom get() = ln(scale / 256) / ln(2.0)

    companion object {
        /** Room kept free at the map's edges: about the size of the drawn character (18 px x 6). */
        const val CHARACTER_MARGIN_PX = 120

        /** Fits the whole trail, with room for the clearing around it and the character, into [w] x [h]. */
        fun fit(points: List<TrailPoint>, w: Int, h: Int): PostcardView {
            val lat0 = points.firstOrNull()?.lat ?: 0.0
            val minX = points.minOfOrNull { Mercator.x(it.lon) } ?: 0.5
            val maxX = points.maxOfOrNull { Mercator.x(it.lon) } ?: 0.5
            val minY = points.minOfOrNull { Mercator.y(it.lat) } ?: 0.5
            val maxY = points.maxOfOrNull { Mercator.y(it.lat) } ?: 0.5
            // At least ~400 m across, plus room for the clearing around the trail.
            val minSpan = Mercator.metersToUnits(400.0, lat0)
            val pad = Mercator.metersToUnits(120.0, lat0)
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            val spanX = max(maxX - minX + 2 * pad, minSpan)
            val spanY = max(maxY - minY + 2 * pad, minSpan)
            // Keep the character (drawn above and beside the last spot) inside the frame.
            val usableW = (w - 2 * CHARACTER_MARGIN_PX).coerceAtLeast(w / 2)
            val usableH = (h - 2 * CHARACTER_MARGIN_PX).coerceAtLeast(h / 2)
            var scale = min(usableW / spanX, usableH / spanY)
            // Keep to zoom levels the tiles exist for.
            scale = scale.coerceIn(256 * 2.0.pow(3), 256 * 2.0.pow(17.5))
            return PostcardView(cx, cy, scale, w, h)
        }
    }
}

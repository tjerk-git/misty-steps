package com.mistysteps.app.eraser

import com.mistysteps.app.data.Trails
import com.mistysteps.app.map.LatLon
import kotlin.math.min

/** The numbers and the geometry of the magic eraser, the same on every platform. */
object EraserRules {
    /** The brush is finger sized: this radius on the screen. */
    const val BRUSH_DP = 28f

    /** However far out the map is zoomed, the brush never gets wider than this. */
    const val MAX_RADIUS_M = 100.0

    /** MapLibre zoom below which the eraser does nothing: one swipe must not wipe a province. */
    const val MIN_ZOOM = 14.0

    /** Spots along a stroke sit this fraction of the brush radius apart. */
    const val SPACING_FACTOR = 0.6

    /** How many strokes can be undone while the eraser is in hand. */
    const val UNDO_LIMIT = 10

    /** The free taste: this much drag, once per install. */
    const val TASTE_M = 100.0

    fun canErase(zoom: Double): Boolean = zoom >= MIN_ZOOM

    fun brushRadiusM(brushPx: Float, pixelsPerMeter: Float): Double =
        min(MAX_RADIUS_M, brushPx / pixelsPerMeter.toDouble())

    /**
     * The centres of the round patches that wipe [path]: the first point, then one every
     * [SPACING_FACTOR] times [radiusM] of travel, and the end of the path if it is more than half a spacing further on.
     */
    fun spotsAlong(path: List<LatLon>, radiusM: Double): List<LatLon> {
        if (path.isEmpty()) return emptyList()
        val spacing = SPACING_FACTOR * radiusM
        val out = arrayListOf(path.first())
        var travelled = 0.0
        for (i in 1 until path.size) {
            travelled += distance(path[i - 1], path[i])
            if (travelled >= spacing) {
                out += path[i]
                travelled = 0.0
            }
        }
        if (travelled > spacing / 2) out += path.last()
        return out
    }

    /** [path] cut where its length reaches [allowanceM] (the cut point is interpolated); nothing when the allowance is used up. */
    fun trimToAllowance(path: List<LatLon>, allowanceM: Double): List<LatLon> {
        if (allowanceM <= 0.0 || path.isEmpty()) return emptyList()
        val out = arrayListOf(path.first())
        var left = allowanceM
        for (i in 1 until path.size) {
            val step = distance(path[i - 1], path[i])
            if (step >= left) {
                val t = if (step == 0.0) 0.0 else left / step
                out += LatLon(
                    path[i - 1].lat + (path[i].lat - path[i - 1].lat) * t,
                    path[i - 1].lon + (path[i].lon - path[i - 1].lon) * t,
                )
                return out
            }
            out += path[i]
            left -= step
        }
        return out
    }

    private fun distance(a: LatLon, b: LatLon) = Trails.approxDistanceM(a.lat, a.lon, b.lat, b.lon)
}

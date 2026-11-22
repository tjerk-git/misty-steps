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
        val walker = SpotWalker(radiusM)
        val out = arrayListOf<LatLon>()
        for (p in path) out += walker.add(p)
        out += walker.finish()
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
                out += interpolate(path[i - 1], path[i], if (step == 0.0) 0.0 else left / step)
                return out
            }
            out += path[i]
            left -= step
        }
        return out
    }

    /** The point [t] (0 to 1) of the way from [a] to [b]. */
    fun interpolate(a: LatLon, b: LatLon, t: Double) = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** Metres between two points (flat-earth is plenty over a drag). */
    fun distance(a: LatLon, b: LatLon) = Trails.approxDistanceM(a.lat, a.lon, b.lat, b.lon)
}

/**
 * [EraserRules.spotsAlong] one point at a time, so the mist can come off while the finger is still moving.
 * Feed every point of the drag to [add]; call [finish] when the finger lifts.
 */
class SpotWalker(radiusM: Double) {
    private val spacing = EraserRules.SPACING_FACTOR * radiusM
    private var last: LatLon? = null
    private var travelled = 0.0

    /** Length of the drag so far, in metres. */
    var dragM = 0.0
        private set

    /** The spots this point adds: the first point always, later ones once the finger has gone far enough. */
    fun add(p: LatLon): List<LatLon> {
        val previous = last
        last = p
        if (previous == null) return listOf(p)
        val step = EraserRules.distance(previous, p)
        dragM += step
        travelled += step
        if (travelled < spacing) return emptyList()
        travelled = 0.0
        return listOf(p)
    }

    /** The end of the drag, if it is more than half a spacing past the last spot. */
    fun finish(): List<LatLon> {
        val end = last ?: return emptyList()
        if (travelled <= spacing / 2) return emptyList()
        travelled = 0.0
        return listOf(end)
    }
}

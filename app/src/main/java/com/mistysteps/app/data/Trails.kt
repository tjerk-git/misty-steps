package com.mistysteps.app.data

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The lines you left behind, one or more segments per walk, in mercator space.
 * A walk is split into segments wherever the GPS jumped too far to fill the gap.
 * Main-thread only (the loader builds its own copy off-thread and hands it over).
 */
class Trails {
    class Segment(val sessionId: Long?) {
        /** Interleaved mercator x, y. */
        var xy = DoubleArray(2 * 32)
            private set
        var size = 0
            private set
        var minX = Double.MAX_VALUE; private set
        var minY = Double.MAX_VALUE; private set
        var maxX = -Double.MAX_VALUE; private set
        var maxY = -Double.MAX_VALUE; private set

        fun add(x: Double, y: Double) {
            if (2 * (size + 1) > xy.size) xy = xy.copyOf(xy.size * 2)
            xy[2 * size] = x
            xy[2 * size + 1] = y
            size++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }

    val segments = ArrayList<Segment>()

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    /**
     * Appends a spot. A new segment starts when [newSegment] is set, when the walk changes,
     * or when the spot is too far from the previous one to have been walked in one go.
     */
    fun add(p: TrailPoint, newSegment: Boolean = false) {
        val current = segments.lastOrNull()
        val split = newSegment || current == null || current.sessionId != p.sessionId ||
            approxDistanceM(lastLat, lastLon, p.lat, p.lon) > BREAK_GAP_M
        val seg = if (split) Segment(p.sessionId).also { segments += it } else current!!
        seg.add(Mercator.x(p.lon), Mercator.y(p.lat))
        lastLat = p.lat
        lastLon = p.lon
    }

    /** Appends [other]'s segments (spots recorded while the stored trail was loading). */
    fun addAll(other: Trails) {
        segments += other.segments
        if (other.segments.isNotEmpty()) {
            lastLat = other.lastLat
            lastLon = other.lastLon
        }
    }

    companion object {
        /** A bit more than the largest gap [FogStore] fills in, so filled gaps never split. */
        const val BREAK_GAP_M = 330.0

        /** Good enough over a few hundred metres; NaN start counts as infinitely far. */
        fun approxDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            if (lat1.isNaN()) return Double.POSITIVE_INFINITY
            var dLon = lon2 - lon1
            if (dLon > 180) dLon -= 360
            if (dLon < -180) dLon += 360
            val x = Math.toRadians(dLon) * cos(Math.toRadians((lat1 + lat2) / 2))
            val y = Math.toRadians(lat2 - lat1)
            return sqrt(x * x + y * y) * 6_371_000.0
        }
    }
}

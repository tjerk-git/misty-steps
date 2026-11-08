package com.mistysteps.app.stats

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** A named street: all OSM ways with that name, each a flat [lon0, lat0, lon1, lat1, ...] line. */
class Street(val name: String, val lines: List<DoubleArray>)

data class StreetProgress(val name: String, val walkedFraction: Double, val lengthM: Double)

data class StreetStats(
    val walked: Int,
    val total: Int,
    /** Streets not walked yet, the nearly-done ones first. */
    val toExplore: List<StreetProgress>,
)

/**
 * Which streets of an area you've walked. Each street is sampled every [SAMPLE_M] metres
 * (only the part inside the area counts); a sample is walked when one of your trail spots
 * lies within [NEAR_M]. A street counts as walked once [WALKED_FRACTION] of it is.
 * Pure Kotlin so it can be tested.
 */
object StreetCoverage {
    const val SAMPLE_M = 10.0
    const val NEAR_M = 25.0
    const val WALKED_FRACTION = 0.7
    /** Ignore streets that only touch the area. */
    const val MIN_LENGTH_M = 30.0

    private const val M_PER_DEG = 111_320.0

    /**
     * [trail]: your spots as interleaved lat, lon. [inside]: whether a (lat, lon) is in the area.
     */
    fun compute(streets: List<Street>, trail: DoubleArray, inside: (Double, Double) -> Boolean): StreetStats {
        if (streets.isEmpty()) return StreetStats(0, 0, emptyList())
        val refLat = streets.first().lines.first()[1]
        val kx = M_PER_DEG * cos(Math.toRadians(refLat)) // metres per degree of longitude here
        val grid = Grid(trail, kx)

        val progress = ArrayList<StreetProgress>()
        for (street in streets) {
            var samples = 0
            var near = 0
            for (line in street.lines) {
                for (i in 0 until line.size / 2 - 1) {
                    val lon1 = line[2 * i]; val lat1 = line[2 * i + 1]
                    val lon2 = line[2 * i + 2]; val lat2 = line[2 * i + 3]
                    val dx = (lon2 - lon1) * kx
                    val dy = (lat2 - lat1) * M_PER_DEG
                    val len = sqrt(dx * dx + dy * dy)
                    val steps = maxOf(1, (len / SAMPLE_M).toInt())
                    for (s in 0 until steps) {
                        val f = (s + 0.5) / steps
                        val lat = lat1 + (lat2 - lat1) * f
                        val lon = lon1 + (lon2 - lon1) * f
                        if (!inside(lat, lon)) continue
                        samples++
                        if (grid.near(lat, lon)) near++
                    }
                }
            }
            val length = samples * SAMPLE_M
            if (length < MIN_LENGTH_M) continue
            progress += StreetProgress(street.name, near.toDouble() / samples, length)
        }
        val walked = progress.count { it.walkedFraction >= WALKED_FRACTION }
        val toExplore = progress.filter { it.walkedFraction < WALKED_FRACTION }
            .sortedWith(compareByDescending<StreetProgress> { it.walkedFraction }.thenBy { it.name })
        return StreetStats(walked, progress.size, toExplore)
    }

    /** Trail spots bucketed in [NEAR_M]-sized cells for quick "anything near here?" checks. */
    private class Grid(trail: DoubleArray, private val kx: Double) {
        private val cells = HashMap<Long, MutableList<Int>>()
        private val pts = trail

        init {
            for (i in 0 until trail.size / 2) {
                cells.getOrPut(key(cx(trail[2 * i + 1]), cy(trail[2 * i]))) { ArrayList() }.add(i)
            }
        }

        private fun cx(lon: Double) = floor(lon * kx / NEAR_M).toInt()
        private fun cy(lat: Double) = floor(lat * M_PER_DEG / NEAR_M).toInt()
        private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

        fun near(lat: Double, lon: Double): Boolean {
            val x0 = cx(lon); val y0 = cy(lat)
            for (x in x0 - 1..x0 + 1) for (y in y0 - 1..y0 + 1) {
                val list = cells[key(x, y)] ?: continue
                for (i in list) {
                    val dx = (pts[2 * i + 1] - lon) * kx
                    val dy = (pts[2 * i] - lat) * M_PER_DEG
                    if (dx * dx + dy * dy <= NEAR_M * NEAR_M) return true
                }
            }
            return false
        }
    }
}

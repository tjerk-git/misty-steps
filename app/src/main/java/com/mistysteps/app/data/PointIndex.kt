package com.mistysteps.app.data

import kotlin.math.floor

/**
 * Grid-bucketed store of cleared spots in mercator space, so drawing only has to look
 * at the buckets that are on screen. Each spot is (x, y, radius) in mercator units.
 * Only touched from the main thread.
 */
class PointIndex {
    class Bucket {
        var data = DoubleArray(3 * 16)
        var size = 0 // number of spots

        fun add(x: Double, y: Double, r: Double) {
            if (3 * (size + 1) > data.size) data = data.copyOf(data.size * 2)
            val i = 3 * size
            data[i] = x; data[i + 1] = y; data[i + 2] = r
            size++
        }
    }

    private val buckets = HashMap<Long, Bucket>()
    var count = 0
        private set

    fun add(lat: Double, lon: Double, radiusMeters: Double) {
        val x = Mercator.x(lon)
        val y = Mercator.y(lat)
        val r = Mercator.metersToUnits(radiusMeters, lat)
        buckets.getOrPut(key(cell(x), cell(y))) { Bucket() }.add(x, y, r)
        count++
    }

    /** Copies every spot of [other] into this index. */
    fun addAll(other: PointIndex) {
        for ((k, b) in other.buckets) {
            val mine = buckets.getOrPut(k) { Bucket() }
            for (i in 0 until b.size) mine.add(b.data[3 * i], b.data[3 * i + 1], b.data[3 * i + 2])
            count += b.size
        }
    }

    /** Calls [visit] for every bucket that may contain spots touching the given mercator rectangle. */
    inline fun forEachBucketIn(
        minX: Double, minY: Double, maxX: Double, maxY: Double,
        visit: (Bucket) -> Unit,
    ) {
        // Spots can poke out of their bucket by their radius; pad by one cell.
        val cx0 = cell(minX) - 1
        val cx1 = cell(maxX) + 1
        val cy0 = cell(minY) - 1
        val cy1 = cell(maxY) + 1
        val span = (cx1 - cx0 + 1).toLong() * (cy1 - cy0 + 1)
        if (span > bucketCount() * 2L) {
            forEachBucket { cx, cy, b -> if (cx in cx0..cx1 && cy in cy0..cy1) visit(b) }
        } else {
            for (cx in cx0..cx1) for (cy in cy0..cy1) bucket(cx, cy)?.let(visit)
        }
    }

    fun bucketCount() = buckets.size
    fun bucket(cx: Int, cy: Int): Bucket? = buckets[key(cx, cy)]
    inline fun forEachBucket(visit: (Int, Int, Bucket) -> Unit) {
        for ((k, b) in entries()) visit((k shr 32).toInt(), k.toInt(), b)
    }
    fun entries(): Set<Map.Entry<Long, Bucket>> = buckets.entries

    companion object {
        /** 2^12 cells across the world: roughly 10 km at the equator. */
        private const val CELLS = 4096

        fun cell(v: Double): Int = floor(v * CELLS).toInt()
        fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
    }
}

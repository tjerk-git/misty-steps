package com.mistysteps.app.stats

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * An area's border: polygons, each a list of rings (first = outer, rest = holes).
 * A ring is a flat [lon0, lat0, lon1, lat1, ...] array.
 */
class Boundary(
    val name: String,
    polygons: List<List<DoubleArray>>,
    /** OpenStreetMap object this border belongs to, e.g. "relation" 162081. */
    val osmType: String? = null,
    val osmId: Long = 0,
    /** What sort of area: "quarter", "neighbourhood", "admin10"…; null for city/country lookups. */
    val kind: String? = null,
) {
    /** Key under which earlier versions saved this area's neighbourhoods and streets (Overpass "area" id). */
    val overpassAreaId: Long?
        get() = when (osmType) {
            "relation" -> 3_600_000_000L + osmId
            "way" -> 2_400_000_000L + osmId
            else -> null
        }

    /** Surface area in square metres (spherical earth). */
    val areaM2: Double = polygons.sumOf { rings ->
        rings.mapIndexed { i, ring -> if (i == 0) abs(ringArea(ring)) else -abs(ringArea(ring)) }.sum()
    }.coerceAtLeast(0.0)

    val minLat: Double
    val maxLat: Double
    val minLon: Double
    val maxLon: Double

    // All edges, as (lon1, lat1, lon2, lat2), bucketed by latitude band for fast lookups.
    private val edges: DoubleArray
    private val bands: Array<IntArray>

    init {
        var nEdges = 0
        var aLat = Double.MAX_VALUE; var bLat = -Double.MAX_VALUE
        var aLon = Double.MAX_VALUE; var bLon = -Double.MAX_VALUE
        for (rings in polygons) for (ring in rings) {
            nEdges += ring.size / 2
            for (i in ring.indices step 2) {
                aLon = minOf(aLon, ring[i]); bLon = maxOf(bLon, ring[i])
                aLat = minOf(aLat, ring[i + 1]); bLat = maxOf(bLat, ring[i + 1])
            }
        }
        minLat = aLat; maxLat = bLat; minLon = aLon; maxLon = bLon

        edges = DoubleArray(nEdges * 4)
        var e = 0
        for (rings in polygons) for (ring in rings) {
            val n = ring.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                edges[e++] = ring[2 * i]; edges[e++] = ring[2 * i + 1]
                edges[e++] = ring[2 * j]; edges[e++] = ring[2 * j + 1]
            }
        }

        val lists = Array(BANDS) { ArrayList<Int>() }
        for (k in 0 until nEdges) {
            val y1 = edges[4 * k + 1]
            val y2 = edges[4 * k + 3]
            for (b in band(minOf(y1, y2))..band(maxOf(y1, y2))) lists[b].add(k)
        }
        bands = Array(BANDS) { lists[it].toIntArray() }
    }

    val hasArea get() = areaM2 > 0

    fun contains(lat: Double, lon: Double): Boolean {
        if (!hasArea || lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        var inside = false
        for (k in bands[band(lat)]) {
            val x1 = edges[4 * k]; val y1 = edges[4 * k + 1]
            val x2 = edges[4 * k + 2]; val y2 = edges[4 * k + 3]
            if ((y1 > lat) != (y2 > lat) && lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1) inside = !inside
        }
        return inside
    }

    private fun band(lat: Double): Int {
        if (maxLat <= minLat) return 0
        return floor((lat - minLat) / (maxLat - minLat) * BANDS).toInt().coerceIn(0, BANDS - 1)
    }

    private companion object {
        const val BANDS = 512
        const val R = 6_371_008.8

        /** Signed spherical ring area (same formula as d3-geo / turf). */
        fun ringArea(ring: DoubleArray): Double {
            val n = ring.size / 2
            if (n < 3) return 0.0
            var total = 0.0
            for (i in 0 until n) {
                val j = (i + 1) % n
                val lon1 = Math.toRadians(ring[2 * i]); val lat1 = Math.toRadians(ring[2 * i + 1])
                val lon2 = Math.toRadians(ring[2 * j]); val lat2 = Math.toRadians(ring[2 * j + 1])
                total += (lon2 - lon1) * (2 + sin(lat1) + sin(lat2))
            }
            return total * R * R / 2
        }
    }
}

/** Earth's total surface area. */
const val WORLD_AREA_M2 = 4 * PI * 6_371_008.8 * 6_371_008.8

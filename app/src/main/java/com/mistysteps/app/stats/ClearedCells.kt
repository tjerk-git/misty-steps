package com.mistysteps.app.stats

import androidx.collection.MutableLongSet
import com.mistysteps.app.data.TrailPoint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Turns the overlapping cleared circles into a set of ~10 x 10 m cells, so the cleared
 * area can be counted (and tested against borders) without double-counting overlaps.
 * Rows are fixed slices of latitude; each row's cell width in longitude is chosen so
 * every cell covers about the same ground area.
 */
class ClearedCells private constructor(private val cells: MutableLongSet) {

    val count get() = cells.size
    val areaM2 get() = cells.size * CELL_M * CELL_M

    /** Area (m²) of cells here that [other] doesn't have: the new ground. */
    fun areaNotIn(other: ClearedCells): Double {
        var n = 0
        cells.forEach { if (it !in other.cells) n++ }
        return n * CELL_M * CELL_M
    }

    /** True if the ~10 m cell around this spot is cleared. */
    fun covers(lat: Double, lon: Double): Boolean {
        val row = floor(lat / D_LAT).toInt()
        val latC = (row + 0.5) * D_LAT
        val col = floor(lon / dLon(latC)).toInt()
        return ((row.toLong() shl 32) or (col.toLong() and 0xffffffffL)) in cells
    }

    /** Cleared area (m²) whose cell centres fall inside [b]. */
    fun areaInside(b: Boundary): Double {
        var n = 0
        cells.forEach { key ->
            val row = (key shr 32).toInt()
            val col = key.toInt()
            val lat = (row + 0.5) * D_LAT
            val lon = (col + 0.5) * dLon(lat)
            if (b.contains(lat, lon)) n++
        }
        return n * CELL_M * CELL_M
    }

    companion object {
        const val CELL_M = 10.0
        private const val M_PER_DEG = 111_320.0
        private const val D_LAT = CELL_M / M_PER_DEG

        private fun dLon(lat: Double) = CELL_M / (M_PER_DEG * cos(Math.toRadians(lat)).coerceAtLeast(0.01))

        fun from(points: List<TrailPoint>): ClearedCells {
            val cells = MutableLongSet()
            for (p in points) {
                val r = p.radius
                val row0 = floor((p.lat - r / M_PER_DEG) / D_LAT).toInt()
                val row1 = floor((p.lat + r / M_PER_DEG) / D_LAT).toInt()
                for (row in row0..row1) {
                    val latC = (row + 0.5) * D_LAT
                    val dy = (latC - p.lat) * M_PER_DEG
                    if (dy * dy > r * r) continue
                    val dx = sqrt(r * r - dy * dy)
                    val step = dLon(latC)
                    val halfLon = dx / (M_PER_DEG * cos(Math.toRadians(latC)).coerceAtLeast(0.01))
                    // Cells whose centre lies within the circle's chord on this row.
                    val col0 = kotlin.math.ceil((p.lon - halfLon) / step - 0.5).toInt()
                    val col1 = floor((p.lon + halfLon) / step - 0.5).toInt()
                    for (col in col0..col1) {
                        cells.add((row.toLong() shl 32) or (col.toLong() and 0xffffffffL))
                    }
                }
            }
            return ClearedCells(cells)
        }
    }
}

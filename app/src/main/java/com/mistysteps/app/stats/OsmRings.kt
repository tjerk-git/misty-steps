package com.mistysteps.app.stats

/**
 * Builds closed rings out of the loose way pieces an OpenStreetMap relation is made of.
 * Overpass hands each member way's geometry separately and in no particular order;
 * pieces that share an end node are chained until the ring closes.
 *
 * Coordinates are flat [lon0, lat0, lon1, lat1, ...] arrays, the format [Boundary] uses.
 */
object OsmRings {

    class Member(val role: String, val coords: DoubleArray)

    /** Returns polygons for [Boundary]: each an outer ring followed by the holes inside it. */
    fun polygons(members: List<Member>): List<List<DoubleArray>> {
        val outers = rings(members.filter { it.role != "inner" }.map { it.coords })
        val inners = rings(members.filter { it.role == "inner" }.map { it.coords })
        val polys = outers.map { mutableListOf(it) }
        for (hole in inners) {
            polys.firstOrNull { ringContains(it[0], hole[1], hole[0]) }?.add(hole)
        }
        return polys
    }

    /** Chains pieces into closed rings. Pieces that never close are dropped. */
    fun rings(pieces: List<DoubleArray>): List<DoubleArray> {
        val open = pieces.filter { it.size >= 4 }.toMutableList()
        val out = ArrayList<DoubleArray>()
        while (open.isNotEmpty()) {
            var ring = open.removeAt(0)
            while (!closed(ring)) {
                val i = open.indexOfFirst { startsAtEnd(it, ring) || endsAtEnd(it, ring) }
                if (i < 0) break
                val next = open.removeAt(i)
                ring = if (startsAtEnd(next, ring)) join(ring, next) else join(ring, reversed(next))
            }
            if (closed(ring) && ring.size >= 8) out += ring
        }
        return out
    }

    private fun closed(r: DoubleArray) = r[0] == r[r.size - 2] && r[1] == r[r.size - 1]

    /** Does [piece] start where [ring] ends? */
    private fun startsAtEnd(piece: DoubleArray, ring: DoubleArray): Boolean =
        piece[0] == ring[ring.size - 2] && piece[1] == ring[ring.size - 1]

    /** Does [piece] end where [ring] ends? */
    private fun endsAtEnd(piece: DoubleArray, ring: DoubleArray): Boolean =
        piece[piece.size - 2] == ring[ring.size - 2] && piece[piece.size - 1] == ring[ring.size - 1]

    /** [a] followed by [b] without repeating the shared node. */
    private fun join(a: DoubleArray, b: DoubleArray): DoubleArray {
        val out = a.copyOf(a.size + b.size - 2)
        System.arraycopy(b, 2, out, a.size, b.size - 2)
        return out
    }

    private fun reversed(r: DoubleArray): DoubleArray {
        val n = r.size / 2
        return DoubleArray(r.size).also { out ->
            for (i in 0 until n) {
                out[2 * i] = r[2 * (n - 1 - i)]
                out[2 * i + 1] = r[2 * (n - 1 - i) + 1]
            }
        }
    }

    /** Even-odd test of one ring. */
    fun ringContains(ring: DoubleArray, lat: Double, lon: Double): Boolean {
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val x1 = ring[2 * j]; val y1 = ring[2 * j + 1]
            val x2 = ring[2 * i]; val y2 = ring[2 * i + 1]
            if ((y1 > lat) != (y2 > lat) && lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1) inside = !inside
            j = i
        }
        return inside
    }
}

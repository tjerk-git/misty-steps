package com.mistysteps.app.map

import com.mistysteps.app.data.Mercator
import com.mistysteps.app.data.Trails

/** The trail as GeoJSON lines for the map: one line per segment (a walk is split where the GPS jumped). */
object TrailGeoJson {
    /** (the walk you are on, earlier walks) as GeoJSON feature collections. */
    fun build(segments: List<Trails.Segment>, currentSession: Long?, minStepM: Double = 3.0): Pair<String, String> {
        val (now, past) = segments.partition { currentSession != null && it.sessionId == currentSession }
        return lines(now, minStepM) to lines(past, minStepM)
    }

    /** [segments] as one GeoJSON FeatureCollection of LineStrings; points closer than [minStepM] are thinned, the ends are kept. */
    fun lines(segments: List<Trails.Segment>, minStepM: Double = 3.0): String {
        val out = StringBuilder("""{"type":"FeatureCollection","features":[""")
        var first = true
        for (seg in segments) {
            if (seg.size < 2) continue
            val xy = seg.xy
            val step = Mercator.metersToUnits(minStepM, Mercator.lat(xy[1]))
            if (!first) out.append(',')
            first = false
            out.append("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
            var lx = xy[0]
            var ly = xy[1]
            append(out, lx, ly, firstPoint = true)
            val last = seg.size - 1
            for (i in 1..last) {
                val x = xy[2 * i]
                val y = xy[2 * i + 1]
                if (i != last && Math.abs(x - lx) < step && Math.abs(y - ly) < step) continue
                append(out, x, y, firstPoint = false)
                lx = x
                ly = y
            }
            out.append("]}}")
        }
        return out.append("]}").toString()
    }

    private fun append(out: StringBuilder, x: Double, y: Double, firstPoint: Boolean) {
        if (!firstPoint) out.append(',')
        out.append('[').append(round(Mercator.lon(x))).append(',').append(round(Mercator.lat(y))).append(']')
    }

    /** Seven decimals is about a centimetre. */
    private fun round(v: Double): Double = Math.round(v * 1e7) / 1e7
}

package com.mistysteps.app.stats

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighbourhoodTest {

    private fun line(vararg lonLat: Double) = lonLat

    @Test
    fun ringsAreChainedFromPiecesInAnyOrderAndDirection() {
        // A 1x1 square split into three pieces, one of them reversed and out of order.
        val pieces = listOf(
            line(0.0, 0.0, 1.0, 0.0),
            line(0.0, 1.0, 0.0, 0.0),          // closes the ring
            line(1.0, 1.0, 1.0, 0.0),          // reversed: runs back to (1, 0)
            line(1.0, 1.0, 0.5, 1.0, 0.0, 1.0),
        )
        val rings = OsmRings.rings(pieces)
        assertEquals(1, rings.size)
        assertTrue(OsmRings.ringContains(rings[0], 0.5, 0.5))
        assertTrue(!OsmRings.ringContains(rings[0], 1.5, 0.5))
    }

    @Test
    fun holesGoInsideTheirOuterRing() {
        val outer = OsmRings.Member("outer", line(0.0, 0.0, 4.0, 0.0, 4.0, 4.0, 0.0, 4.0, 0.0, 0.0))
        val inner = OsmRings.Member("inner", line(1.0, 1.0, 2.0, 1.0, 2.0, 2.0, 1.0, 2.0, 1.0, 1.0))
        val polys = OsmRings.polygons(listOf(outer, inner))
        assertEquals(1, polys.size)
        assertEquals(2, polys[0].size)
        val b = Boundary("Donut", polys)
        assertTrue(b.contains(3.0, 3.0))
        assertTrue(!b.contains(1.5, 1.5))
    }

    @Test
    fun picksTheQuarterOverTheSmallerNeighbourhoodAndIgnoresTheTownItself() {
        fun square(name: String, kind: String, size: Double) = Boundary(
            name, listOf(listOf(line(4.88 - size, 52.37 - size, 4.88 + size, 52.37 - size, 4.88 + size, 52.37 + size, 4.88 - size, 52.37 + size, 4.88 - size, 52.37 - size))),
            "relation", 1, kind,
        )
        val city = square("Town", "city", 0.1)
        val all = listOf(
            square("Buurt", "neighbourhood", 0.002),
            square("Wijk", "quarter", 0.005),
            square("Woonplaats", "admin10", 0.1), // as big as the city: not a neighbourhood
        )
        assertEquals("Wijk", Neighbourhoods.pick(all, city, 52.37, 4.88)?.name)
        assertEquals("Buurt", Neighbourhoods.pick(all.filter { it.kind != "quarter" }, city, 52.37, 4.88)?.name)
        assertNull(Neighbourhoods.pick(all.filter { it.kind == "admin10" }, city, 52.37, 4.88))
    }

    @Test
    fun parsesOverpassRelationsAndStreets() {
        val hoods = Neighbourhoods.parseHoods(JSONObject("""
            {"elements":[{"type":"relation","id":42,"tags":{"name":"Jordaan","place":"quarter"},
              "members":[
                {"type":"way","role":"outer","geometry":[{"lat":52.37,"lon":4.86},{"lat":52.37,"lon":4.87},{"lat":52.38,"lon":4.87}]},
                {"type":"way","role":"outer","geometry":[{"lat":52.38,"lon":4.87},{"lat":52.38,"lon":4.86},{"lat":52.37,"lon":4.86}]}
              ]}]}
        """))
        assertEquals(1, hoods.size)
        assertEquals("quarter", hoods[0].kind)
        assertEquals(3_600_000_042L, hoods[0].overpassAreaId)
        assertTrue(hoods[0].contains(52.375, 4.865))

        val streets = Neighbourhoods.parseStreets(JSONObject("""
            {"elements":[
              {"type":"way","tags":{"name":"Lindengracht","highway":"residential"},"geometry":[{"lat":52.37,"lon":4.86},{"lat":52.37,"lon":4.861}]},
              {"type":"way","tags":{"name":"Lindengracht","highway":"residential"},"geometry":[{"lat":52.37,"lon":4.861},{"lat":52.37,"lon":4.862}]},
              {"type":"way","tags":{"name":"Bloemgracht","highway":"residential"},"geometry":[{"lat":52.371,"lon":4.86},{"lat":52.372,"lon":4.86}]}
            ]}
        """))
        assertEquals(listOf("Lindengracht", "Bloemgracht"), streets.map { it.name })
        assertEquals(2, streets[0].lines.size)
    }

    @Test
    fun aStreetCountsOnceMostOfItIsWalked() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(52.37))
        // Two 200 m east-west streets, 100 m apart.
        val a = Street("A-straat", listOf(line(4.86, 52.37, 4.86 + 200 / mPerDegLon, 52.37)))
        val b = Street("B-straat", listOf(line(4.86, 52.3709, 4.86 + 200 / mPerDegLon, 52.3709)))
        // Walked all of A, 8 m off the centre line (GPS), and the first 60 m of B.
        val trail = ArrayList<Double>()
        for (m in 0..200 step 20) { trail += 52.37 + 8 / 111_320.0; trail += 4.86 + m / mPerDegLon }
        for (m in 0..60 step 20) { trail += 52.3709; trail += 4.86 + m / mPerDegLon }
        val st = StreetCoverage.compute(listOf(a, b), trail.toDoubleArray()) { _, _ -> true }
        assertEquals(1, st.walked)
        assertEquals(2, st.total)
        assertEquals("B-straat", st.toExplore.single().name)
        assertEquals(0.4, st.toExplore.single().walkedFraction, 0.1)
    }

    @Test
    fun onlyThePartInsideTheAreaCounts() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(52.37))
        val s = Street("Long", listOf(line(4.86, 52.37, 4.86 + 400 / mPerDegLon, 52.37)))
        val trail = ArrayList<Double>()
        for (m in 0..200 step 20) { trail += 52.37; trail += 4.86 + m / mPerDegLon }
        // Area = the western half; walked all of that half.
        val st = StreetCoverage.compute(listOf(s), trail.toDoubleArray()) { _, lon -> lon < 4.86 + 200 / mPerDegLon }
        assertEquals(1, st.walked)
    }
}

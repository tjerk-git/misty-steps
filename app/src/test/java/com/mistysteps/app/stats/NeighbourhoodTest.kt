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
            name, listOf(listOf(line(5.8 - size, 53.2 - size, 5.8 + size, 53.2 - size, 5.8 + size, 53.2 + size, 5.8 - size, 53.2 + size, 5.8 - size, 53.2 - size))),
            "relation", 1, kind,
        )
        val city = square("Town", "city", 0.1)
        val all = listOf(
            square("Buurt", "neighbourhood", 0.002),
            square("Wijk", "quarter", 0.005),
            square("Woonplaats", "admin10", 0.1), // as big as the city: not a neighbourhood
        )
        assertEquals("Wijk", Neighbourhoods.pick(all, city, 53.2, 5.8)?.name)
        assertEquals("Buurt", Neighbourhoods.pick(all.filter { it.kind != "quarter" }, city, 53.2, 5.8)?.name)
        assertNull(Neighbourhoods.pick(all.filter { it.kind == "admin10" }, city, 53.2, 5.8))
    }

    @Test
    fun parsesOverpassRelationsAndStreets() {
        val hoods = Neighbourhoods.parseHoods(JSONObject("""
            {"elements":[{"type":"relation","id":42,"tags":{"name":"Jordaan","place":"quarter"},
              "members":[
                {"type":"way","role":"outer","geometry":[{"lat":53.20,"lon":5.78},{"lat":53.20,"lon":5.79},{"lat":53.21,"lon":5.79}]},
                {"type":"way","role":"outer","geometry":[{"lat":53.21,"lon":5.79},{"lat":53.21,"lon":5.78},{"lat":53.20,"lon":5.78}]}
              ]}]}
        """))
        assertEquals(1, hoods.size)
        assertEquals("quarter", hoods[0].kind)
        assertEquals(3_600_000_042L, hoods[0].overpassAreaId)
        assertTrue(hoods[0].contains(53.205, 5.785))

        val streets = Neighbourhoods.parseStreets(JSONObject("""
            {"elements":[
              {"type":"way","tags":{"name":"Lindengracht","highway":"residential"},"geometry":[{"lat":53.2,"lon":5.78},{"lat":53.2,"lon":5.781}]},
              {"type":"way","tags":{"name":"Lindengracht","highway":"residential"},"geometry":[{"lat":53.2,"lon":5.781},{"lat":53.2,"lon":5.782}]},
              {"type":"way","tags":{"name":"Bloemgracht","highway":"residential"},"geometry":[{"lat":53.201,"lon":5.78},{"lat":53.202,"lon":5.78}]}
            ]}
        """))
        assertEquals(listOf("Lindengracht", "Bloemgracht"), streets.map { it.name })
        assertEquals(2, streets[0].lines.size)
    }

    @Test
    fun aStreetCountsOnceMostOfItIsWalked() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(53.2))
        // Two 200 m east-west streets, 100 m apart.
        val a = Street("A-straat", listOf(line(5.78, 53.2, 5.78 + 200 / mPerDegLon, 53.2)))
        val b = Street("B-straat", listOf(line(5.78, 53.2009, 5.78 + 200 / mPerDegLon, 53.2009)))
        // Walked all of A, 8 m off the centre line (GPS), and the first 60 m of B.
        val trail = ArrayList<Double>()
        for (m in 0..200 step 20) { trail += 53.2 + 8 / 111_320.0; trail += 5.78 + m / mPerDegLon }
        for (m in 0..60 step 20) { trail += 53.2009; trail += 5.78 + m / mPerDegLon }
        val st = StreetCoverage.compute(listOf(a, b), trail.toDoubleArray()) { _, _ -> true }
        assertEquals(1, st.walked)
        assertEquals(2, st.total)
        assertEquals("B-straat", st.toExplore.single().name)
        assertEquals(0.4, st.toExplore.single().walkedFraction, 0.1)
    }

    @Test
    fun onlyThePartInsideTheAreaCounts() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(53.2))
        val s = Street("Long", listOf(line(5.78, 53.2, 5.78 + 400 / mPerDegLon, 53.2)))
        val trail = ArrayList<Double>()
        for (m in 0..200 step 20) { trail += 53.2; trail += 5.78 + m / mPerDegLon }
        // Area = the western half; walked all of that half.
        val st = StreetCoverage.compute(listOf(s), trail.toDoubleArray()) { _, lon -> lon < 5.78 + 200 / mPerDegLon }
        assertEquals(1, st.walked)
    }
}

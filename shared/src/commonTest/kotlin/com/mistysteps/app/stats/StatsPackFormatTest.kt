package com.mistysteps.app.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsPackFormatTest {
    // A square from (5.0, 53.0) to (5.01, 53.01), delta-encoded in units of 1e-5 degree.
    private val ring = "[500000,5300000,1000,0,0,1000,-1000,0,0,-1000]"

    private val hoodsJson = """{"hoods":[
        {"type":"relation","id":10,"name":"Binnenstad","kind":"suburb","polygons":[[$ring]]},
        {"type":"way","id":11,"name":"","kind":"quarter","polygons":[[$ring]]},
        {"type":"way","id":12,"name":"Zonder vlak","kind":"quarter","polygons":[]}
    ]}"""

    private val streetsJson = """{"streets":[
        {"name":"Hoofdstraat","lines":[[500200,5300500,400,0],[500600,5300500,300,0]]},
        {"name":"Leeg","lines":[]}
    ]}"""

    @Test
    fun aLineDecodesToLonLatPairs() {
        val line = StatsPackFormat.decodeLine(listOf(579000L, 5320000L, 1000L, 0L))
        assertEquals(listOf(5.79, 53.2, 5.80, 53.2), line.toList().map { kotlin.math.round(it * 1e5) / 1e5 })
    }

    @Test
    fun anIndexOfFormat1GivesTheProvinceAndTheTowns() {
        val index = StatsPackFormat.parseIndex("""{"format":1,"province":"fr","dataDate":"2026-11-10","cities":[7,9]}""")
        assertNotNull(index)
        assertEquals("fr", index.province)
        assertEquals("2026-11-10", index.dataDate)
        assertEquals(setOf(7L, 9L), index.cities)
    }

    @Test
    fun anIndexOfAnotherFormatOrBrokenTextGivesNull() {
        assertNull(StatsPackFormat.parseIndex("""{"format":2,"province":"fr","dataDate":"x","cities":[]}"""))
        assertNull(StatsPackFormat.parseIndex("not json"))
        assertNull(StatsPackFormat.parseIndex("{}"))
        assertNull(StatsPackFormat.parseIndex(""))
    }

    @Test
    fun hoodsBecomeBoundariesWithTheirNameKindAndOsmObject() {
        val hoods = StatsPackFormat.parseHoods(hoodsJson)
        assertEquals(1, hoods.size) // the nameless one and the one without a shape are skipped
        val hood = hoods.single()
        assertEquals("Binnenstad", hood.name)
        assertEquals("suburb", hood.kind)
        assertEquals("relation", hood.osmType)
        assertEquals(10L, hood.osmId)
        assertTrue(hood.contains(53.005, 5.005))
        assertFalse(hood.contains(53.5, 5.5))
    }

    @Test
    fun streetsKeepTheirLinesAndEmptyOnesAreDropped() {
        val streets = StatsPackFormat.parseStreets(streetsJson)
        assertEquals(1, streets.size)
        assertEquals("Hoofdstraat", streets[0].name)
        assertEquals(2, streets[0].lines.size)
        assertEquals(5.002, streets[0].lines[0][0], 1e-9)   // lon of the first point
        assertEquals(53.005, streets[0].lines[0][1], 1e-9)  // lat of the first point
        assertEquals(5.006, streets[0].lines[0][2], 1e-9)   // the second point is 400 units further east
    }

    @Test
    fun brokenHoodOrStreetTextGivesNothingInsteadOfFailing() {
        assertTrue(StatsPackFormat.parseHoods("not json").isEmpty())
        assertTrue(StatsPackFormat.parseHoods("{}").isEmpty())
        assertTrue(StatsPackFormat.parseStreets("[1,2").isEmpty())
    }

    @Test
    fun entryNamesFollowTheFileLayout() {
        assertEquals("streets/r12345.json", StatsPackFormat.streetsEntry("relation", 12345))
        assertEquals("streets/w7.json", StatsPackFormat.streetsEntry("way", 7))
        assertEquals("hoods/99.json", StatsPackFormat.hoodsEntry(99))
        assertEquals("index.json", StatsPackFormat.INDEX_ENTRY)
    }
}

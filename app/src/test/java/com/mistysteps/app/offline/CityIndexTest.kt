package com.mistysteps.app.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class CityIndexTest {

    // Two square towns side by side inside a square country. West has a hole (a lake that belongs to no one).
    private val sample = """
        {
          "format": 1,
          "country": { "name": "Testland", "osmId": 1,
            "polygons": [[[0,0, 10,0, 10,10, 0,10]]] },
          "cities": [
            { "osmId": 11, "name": "West",
              "polygons": [[[1,1, 5,1, 5,5, 1,5], [2,2, 3,2, 3,3, 2,3]]] },
            { "osmId": 12, "name": "East",
              "polygons": [[[5,1, 9,1, 9,5, 5,5]]] }
          ]
        }
    """.trimIndent()

    private val index = CityIndex.parse(sample)

    @Test fun findsTheTownAroundAPoint() {
        // Coordinates in the file are lon, lat; cityAt takes lat, lon.
        assertEquals("West", index.cityAt(lat = 4.0, lon = 1.5)?.name)
        assertEquals("East", index.cityAt(lat = 4.0, lon = 8.0)?.name)
    }

    @Test fun townsKeepTheirOsmRelation() {
        val west = index.cityAt(lat = 4.0, lon = 1.5)!!
        assertEquals("relation", west.osmType)
        assertEquals(11L, west.osmId)
    }

    @Test fun holesAreNotPartOfTheTown() {
        assertNull(index.cityAt(lat = 2.5, lon = 2.5))
    }

    @Test fun outsideEveryTownButInsideTheCountry() {
        assertNull(index.cityAt(lat = 8.0, lon = 8.0))
        assertTrue(index.country.contains(8.0, 8.0))
    }

    @Test fun outsideTheCountry() {
        assertNull(index.cityAt(lat = 20.0, lon = 20.0))
        assertTrue(!index.country.contains(20.0, 20.0))
        assertEquals("Testland", index.country.name)
    }

    @Test fun readsAStream() {
        val read = CityIndex.read(ByteArrayInputStream(sample.toByteArray()))
        assertEquals(2, read.cities.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesANewerFormat() {
        CityIndex.parse(sample.replace("\"format\": 1", "\"format\": 2"))
    }

    /** The real Dutch index shipped in the app. */
    @Test fun realIndexKnowsDutchTowns() {
        val asset = File("src/main/assets/${CityIndex.NL_ASSET}")
        val nl = asset.inputStream().use { CityIndex.read(it) }
        assertTrue("expected about 2500 woonplaatsen, got ${nl.cities.size}", nl.cities.size in 2000..3000)
        val leeuwarden = nl.cityAt(53.2012, 5.7999) // Waagplein
        assertEquals("Leeuwarden", leeuwarden?.name)
        // The town (woonplaats), as the old Nominatim lookups gave it: neighbourhoods saved by
        // earlier versions are filed under this relation.
        assertEquals(162081L, leeuwarden?.osmId)
        assertEquals("Stiens", nl.cityAt(53.2615, 5.7590)?.name) // a village in the same municipality
        assertEquals("Amsterdam", nl.cityAt(52.3731, 4.8926)?.name) // Dam
        assertEquals("Maastricht", nl.cityAt(50.8514, 5.6910)?.name) // Vrijthof
        assertTrue(nl.country.contains(52.3731, 4.8926))
        assertNull(nl.cityAt(52.5200, 13.4050)) // Berlin
        assertTrue(!nl.country.contains(50.8503, 4.3517)) // Brussels
    }
}

package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The real `assets/nl-provinces.json`, so a broken border or id shows up here. */
class ProvinceIndexAssetTest {
    private val index = ProvinceIndex.parse(File("src/main/assets/nl-provinces.json").readText())

    @Test
    fun hasTwelveProvinces() {
        assertEquals(
            listOf("dr", "fl", "fr", "ge", "gr", "li", "nb", "nh", "ov", "ut", "ze", "zh"),
            index.provinces.map { it.id }.sorted(),
        )
    }

    @Test
    fun knownPlacesLandInTheRightProvince() {
        assertEquals("fr", index.provinceAt(53.2014, 5.7936)?.id) // Leeuwarden
        assertEquals("nh", index.provinceAt(52.3731, 4.8922)?.id) // Amsterdam
        assertEquals("li", index.provinceAt(50.8514, 5.6910)?.id) // Maastricht
        assertEquals("ze", index.provinceAt(51.4988, 3.6110)?.id) // Middelburg
        assertEquals("gr", index.provinceAt(53.2194, 6.5665)?.id) // Groningen
    }

    @Test
    fun theNorthSeaAndGermanyAreInNoProvince() {
        assertNull(index.provinceAt(52.5, 3.0))
        assertNull(index.provinceAt(52.5, 9.0))
    }
}

package com.mistysteps.app.maps

import com.mistysteps.app.data.TrailPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProvinceIndexTest {
    // Two squares sharing the edge at lon 6.0: fr is lon 5 to 6, gr is lon 6 to 7, both lat 52.8 to 53.4.
    private val json = """
        {"format":1,"provinces":[
          {"id":"fr","name":"Friesland","polygons":[[[5.0,52.8,6.0,52.8,6.0,53.4,5.0,53.4]]]},
          {"id":"gr","name":"Groningen","polygons":[[[6.0,52.8,7.0,52.8,7.0,53.4,6.0,53.4]]]}
        ]}
    """.trimIndent()
    private val index = ProvinceIndex.parse(json)

    private fun point(lat: Double, lon: Double) = TrailPoint(lat, lon, 0L)

    @Test
    fun findsTheProvinceAPointIsIn() {
        assertEquals("fr", index.provinceAt(53.0, 5.5)?.id)
        assertEquals("gr", index.provinceAt(53.0, 6.5)?.id)
        assertEquals("Friesland", index.provinceAt(53.0, 5.5)?.name)
    }

    @Test
    fun pointInNoProvinceGivesNull() {
        assertNull(index.provinceAt(52.0, 4.0))
        assertNull(index.provinceAt(53.0, 8.0))
    }

    @Test
    fun pointOnTheSharedEdgeGivesOneProvinceWithoutThrowing() {
        assertNotNull(index.provinceAt(53.0, 6.0))
    }

    @Test
    fun mostWalkedCountsPointsPerProvince() {
        val points = List(7) { point(53.0, 5.5) } + List(3) { point(53.0, 6.5) }
        assertEquals("fr", index.mostWalked(points)?.id)
    }

    @Test
    fun mostWalkedOfNoPointsIsNull() {
        assertNull(index.mostWalked(emptyList()))
    }

    @Test
    fun mostWalkedOfPointsOutsideEveryProvinceIsNull() {
        assertNull(index.mostWalked(listOf(point(52.0, 4.0), point(48.0, 2.0))))
    }

    @Test
    fun aNewerFormatIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProvinceIndex.parse(json.replace("\"format\":1", "\"format\":2")) }
    }
}

package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
    private val sha = "a".repeat(64)

    private fun json(format: Int = 2, bytes: Long = 65_011_712, hash: String = sha, withStats: Boolean = true, url: String = "v2/nl/2027/fr/map.pmtiles") = """
        {"format":$format,"country":"nl","year":2027,"dataDate":"2027-01-02","provinces":[
          {"id":"fr","name":"Friesland","bbox":[4.9,52.8,6.3,53.5],
           "map":{"url":"$url","bytes":$bytes,"sha256":"$hash"}
           ${if (withStats) ""","stats":{"url":"v2/nl/2027/fr/stats.zip","bytes":4194304,"sha256":"$sha"}""" else ""}}
        ]}
    """.trimIndent()

    @Test
    fun parsesTheSpecExample() {
        val catalog = Catalog.parse(json())
        assertEquals(2027, catalog.year)
        assertEquals("2027-01-02", catalog.dataDate)
        val fr = catalog.entry("fr")!!
        assertEquals("Friesland", fr.name)
        assertEquals(2027, fr.year)
        assertEquals(listOf(4.9, 52.8, 6.3, 53.5), fr.bbox)
        assertEquals(65_011_712L, fr.map.bytes)
        assertEquals(sha, fr.map.sha256)
        assertNotNull(fr.stats)
    }

    @Test
    fun statsAreOptional() {
        assertNull(Catalog.parse(json(withStats = false)).entry("fr")!!.stats)
    }

    @Test
    fun relativeUrlsBecomeAbsolute() {
        val fr = Catalog.parse(json()).entry("fr")!!
        assertEquals("https://maps.potloodgum.com/v2/nl/2027/fr/map.pmtiles", fr.map.url)
        assertEquals("https://maps.potloodgum.com/v2/nl/2027/fr/stats.zip", fr.stats!!.url)
    }

    @Test
    fun absoluteUrlsAreKept() {
        val fr = Catalog.parse(json(url = "https://maps.potloodgum.com/x/map.pmtiles")).entry("fr")!!
        assertEquals("https://maps.potloodgum.com/x/map.pmtiles", fr.map.url)
    }

    @Test
    fun aNewerFormatThrowsCatalogFormatException() {
        assertThrows(CatalogFormatException::class.java) { Catalog.parse(json(format = 3)) }
    }

    @Test
    fun negativeSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Catalog.parse(json(bytes = -1)) }
    }

    @Test
    fun badChecksumIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Catalog.parse(json(hash = "xyz")) }
        assertThrows(IllegalArgumentException::class.java) { Catalog.parse(json(hash = "A".repeat(64))) }
    }

    @Test
    fun unknownProvinceIsNull() {
        assertNull(Catalog.parse(json()).entry("zz"))
    }
}

package com.mistysteps.app.maps

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapStyleTest {
    // A template shaped like assets/maps/misty-style.json: a background and two layers on __SOURCE__.
    private val template = """
        {"version":8,"name":"Misty","glyphs":"asset://maps/glyphs/{fontstack}/{range}.pbf",
         "sources":{"__SOURCE__":{"type":"vector"}},
         "layers":[
           {"id":"background","type":"background","paint":{"background-color":"#F6F3EC"}},
           {"id":"water","type":"fill","source":"__SOURCE__","source-layer":"water"},
           {"id":"road","type":"line","source":"__SOURCE__","source-layer":"transportation"}
         ]}
    """.trimIndent()

    private fun layerIds(style: String): List<String> {
        val layers = JSONObject(style).getJSONArray("layers")
        return (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
    }

    @Test
    fun zeroPacksGivesBackgroundOnly() {
        val style = JSONObject(MapStyle.build(template, emptyList()))
        assertEquals(0, style.getJSONObject("sources").length())
        assertEquals(listOf("background"), layerIds(style.toString()))
    }

    @Test
    fun onePackAddsOneSourceWithFileUrl() {
        val pack = File("/data/files/maps/nl/2027/fr/map.pmtiles")
        val style = JSONObject(MapStyle.build(template, listOf(pack)))
        val sources = style.getJSONObject("sources")
        assertEquals(1, sources.length())
        assertEquals("pmtiles://file://" + pack.absolutePath, sources.getJSONObject("pack-0").getString("url"))
        assertEquals("vector", sources.getJSONObject("pack-0").getString("type"))
        assertEquals(listOf("background", "water-0", "road-0"), layerIds(style.toString()))
        assertEquals("pack-0", style.getJSONArray("layers").getJSONObject(1).getString("source"))
        assertEquals("asset://maps/glyphs/{fontstack}/{range}.pbf", style.getString("glyphs"))
    }

    @Test
    fun twoPacksGiveUniqueIdsInPackOrder() {
        val style = MapStyle.build(template, listOf(File("/a/fr.pmtiles"), File("/a/zh.pmtiles")))
        val ids = layerIds(style)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("background", "water-0", "road-0", "water-1", "road-1"), ids)
        assertEquals(setOf("pack-0", "pack-1"), JSONObject(style).getJSONObject("sources").keys().asSequence().toSet())
    }

    @Test
    fun pathWithSpacesIsKept() {
        val style = MapStyle.build(template, listOf(File("/tmp/my pack.pmtiles")))
        assertTrue(style.contains("pmtiles://file:///tmp/my pack.pmtiles"))
        assertFalse(style.contains("%20"))
    }
}

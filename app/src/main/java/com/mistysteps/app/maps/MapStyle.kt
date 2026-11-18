package com.mistysteps.app.maps

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Turns the style template (`assets/maps/misty-style.json`, which has one URL-less source called
 * `__SOURCE__`) into a style for the map packs that are installed. A MapLibre source reads one PMTiles
 * file, so every pack gets its own source `pack-<n>` and its own copy of the template's layers.
 */
object MapStyle {
    private const val TEMPLATE_SOURCE = "__SOURCE__"

    /** The style JSON for [packs]; with none, just the background colour: solid mist on top of it. */
    fun build(template: String, packs: List<File>): String {
        val style = JSONObject(template)
        val templateLayers = style.getJSONArray("layers")
        val background = templateLayers.getJSONObject(0)
        val layers = JSONArray().put(background)
        val sources = JSONObject()
        packs.forEachIndexed { n, file ->
            val source = "pack-$n"
            sources.put(source, JSONObject().put("type", "vector").put("url", "pmtiles://file://${file.absolutePath}"))
            for (i in 1 until templateLayers.length()) {
                // Copy through text so each pack's layers are separate objects.
                val layer = JSONObject(templateLayers.getJSONObject(i).toString())
                check(layer.optString("source") == TEMPLATE_SOURCE) { "layer ${layer.optString("id")} must read from $TEMPLATE_SOURCE" }
                layer.put("id", "${layer.getString("id")}-$n").put("source", source)
                layers.put(layer)
            }
        }
        return style.put("sources", sources).put("layers", layers).toString()
    }
}

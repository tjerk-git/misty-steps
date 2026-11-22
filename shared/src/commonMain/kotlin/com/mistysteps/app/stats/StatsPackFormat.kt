package com.mistysteps.app.stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** What `index.json` of a stats file says: which province and which towns have neighbourhoods in it. */
data class StatsIndex(val province: String, val dataDate: String, val cities: Set<Long>)

/**
 * Reads the neighbourhoods-and-streets file that ships next to each province's map (built by `tools/maps/stats_build.py`,
 * layout described there). Pure functions over the text of one entry; none of them throws for bad input: they give null or
 * nothing, so a damaged or newer file just means "no neighbourhood card".
 */
object StatsPackFormat {
    const val FORMAT = 1
    const val INDEX_ENTRY = "index.json"
    private const val E5 = 100_000.0

    fun hoodsEntry(cityId: Long) = "hoods/$cityId.json"

    /** The entry holding the streets of a neighbourhood: `streets/r<id>.json` for a relation, `streets/w<id>.json` for a way. */
    fun streetsEntry(osmType: String, osmId: Long) = "streets/${if (osmType == "relation") "r" else "w"}$osmId.json"

    /** A delta-encoded line (units of 1e-5 degree: lon0, lat0, dlon1, dlat1, ...) as flat [lon, lat, lon, lat, ...] degrees. */
    fun decodeLine(ints: List<Long>): DoubleArray {
        val out = DoubleArray(ints.size / 2 * 2)
        var x = 0L
        var y = 0L
        for (i in 0 until ints.size / 2) {
            x += ints[2 * i]
            y += ints[2 * i + 1]
            out[2 * i] = x / E5
            out[2 * i + 1] = y / E5
        }
        return out
    }

    fun parseIndex(text: String): StatsIndex? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        if (o.getValue("format").jsonPrimitive.int != FORMAT) return null
        StatsIndex(
            province = o.getValue("province").jsonPrimitive.content,
            dataDate = o.getValue("dataDate").jsonPrimitive.content,
            cities = o.getValue("cities").jsonArray.map { it.jsonPrimitive.long }.toSet(),
        )
    }.getOrNull()

    fun parseHoods(text: String): List<Boundary> = runCatching {
        val hoods = Json.parseToJsonElement(text).jsonObject["hoods"]?.jsonArray ?: return emptyList()
        hoods.mapNotNull { el -> runCatching { hood(el.jsonObject) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun hood(o: JsonObject): Boundary? {
        val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val polygons = o.getValue("polygons").jsonArray.map { polygon ->
            polygon.jsonArray.map { ring -> decodeLine(ring.jsonArray.map { it.jsonPrimitive.long }) }
        }
        if (polygons.isEmpty()) return null
        val b = Boundary(
            name, polygons,
            o.getValue("type").jsonPrimitive.content, o.getValue("id").jsonPrimitive.long,
            o["kind"]?.jsonPrimitive?.contentOrNull,
        )
        return if (b.hasArea) b else null
    }

    fun parseStreets(text: String): List<Street> = runCatching {
        val streets = Json.parseToJsonElement(text).jsonObject["streets"]?.jsonArray ?: return emptyList()
        streets.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                val lines = (o.getValue("lines") as JsonArray).map { line -> decodeLine(line.jsonArray.map { it.jsonPrimitive.long }) }
                    .filter { it.size >= 4 }
                if (lines.isEmpty()) null else Street(o.getValue("name").jsonPrimitive.content, lines)
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

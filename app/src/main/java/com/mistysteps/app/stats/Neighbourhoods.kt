package com.mistysteps.app.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Neighbourhoods (wijken) and their streets, from OpenStreetMap.
 *
 * All neighbourhood borders of your city are fetched at once, by the city's OSM id, and
 * kept on the phone; which one you're in is then worked out on the phone. Streets are
 * fetched per neighbourhood, by its OSM id. So no position is ever sent.
 */
class Neighbourhoods(context: Context) {

    private val root = File(context.filesDir, "boundaries")
    private val hoodDir = File(root, "neighbourhoods").apply { mkdirs() }
    private val streetDir = File(root, "streets").apply { mkdirs() }
    private val hoods = HashMap<Long, List<Boundary>>() // by city area id
    private val streets = HashMap<Long, List<Street>>() // by neighbourhood area id

    /** The neighbourhood around (lat, lon) in [city], or null. Blocking; may hit the network. */
    @Synchronized
    fun find(city: Boundary, lat: Double, lon: Double): Boundary? {
        val area = city.overpassAreaId ?: return null
        val all = hoods.getOrPut(area) {
            parseHoods(cached(File(hoodDir, "$area.json"), readTimeoutMs = 60_000) { hoodQuery(area) })
        }
        return pick(all, city, lat, lon)
    }

    /** Named streets in [hood]. Blocking; may hit the network. */
    @Synchronized
    fun streets(hood: Boundary): List<Street> {
        val area = hood.overpassAreaId ?: return emptyList()
        return streets.getOrPut(area) {
            parseStreets(cached(File(streetDir, "$area.json"), readTimeoutMs = 30_000) { streetQuery(area) })
        }
    }

    private fun cached(file: File, readTimeoutMs: Int, ql: () -> String): JSONObject {
        if (file.exists()) runCatching { return JSONObject(file.readText()) }
        val json = Overpass.query(ql(), readTimeoutMs)
        file.writeText(json.toString())
        return json
    }

    companion object {
        /** Preferred kinds, best first: a wijk-sized area is big enough to explore, small enough to finish. */
        private val ORDER = listOf("quarter", "suburb", "neighbourhood", "admin11", "admin10", "admin9")

        fun hoodQuery(cityArea: Long) = """
            [out:json][timeout:55];
            area(id:$cityArea)->.c;
            (
              relation["place"~"^(suburb|quarter|neighbourhood)${'$'}"](area.c);
              way["place"~"^(suburb|quarter|neighbourhood)${'$'}"](area.c);
              relation["boundary"="administrative"]["admin_level"~"^(9|10|11)${'$'}"](area.c);
            );
            out geom;
        """.trimIndent()

        fun streetQuery(hoodArea: Long) = """
            [out:json][timeout:25];
            area(id:$hoodArea)->.n;
            way["highway"~"^(residential|living_street|pedestrian|unclassified|tertiary|secondary|primary|footway|path|cycleway|service|track|steps)${'$'}"]["name"]["foot"!="no"](area.n);
            out tags geom;
        """.trimIndent()

        /** Picks the best-sized area containing the point; ignores anything as big as the city. */
        fun pick(all: List<Boundary>, city: Boundary, lat: Double, lon: Double): Boundary? =
            all.filter { it.contains(lat, lon) && (city.areaM2 <= 0 || it.areaM2 < city.areaM2 * 0.5) }
                .sortedWith(compareBy<Boundary> { rank(it.kind) }.thenBy { it.areaM2 })
                .firstOrNull()

        private fun rank(kind: String?): Int = ORDER.indexOf(kind).let { if (it < 0) 99 else it }

        fun parseHoods(json: JSONObject): List<Boundary> {
            val out = ArrayList<Boundary>()
            val elements = json.optJSONArray("elements") ?: return out
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: continue
                val name = tags.optString("name")
                if (name.isBlank()) continue
                val kind = tags.optString("place").ifBlank { "admin" + tags.optString("admin_level") }
                val polygons = when (e.optString("type")) {
                    "relation" -> {
                        val members = e.optJSONArray("members") ?: continue
                        OsmRings.polygons((0 until members.length()).mapNotNull { m ->
                            val mem = members.getJSONObject(m)
                            if (mem.optString("type") != "way") return@mapNotNull null
                            val geo = mem.optJSONArray("geometry") ?: return@mapNotNull null
                            OsmRings.Member(mem.optString("role"), coords(geo))
                        })
                    }
                    "way" -> OsmRings.rings(listOf(coords(e.optJSONArray("geometry") ?: continue))).map { listOf(it) }
                    else -> continue
                }
                if (polygons.isEmpty()) continue
                val b = Boundary(name, polygons, e.optString("type"), e.optLong("id"), kind)
                if (b.hasArea) out += b
            }
            return out
        }

        fun parseStreets(json: JSONObject): List<Street> {
            val byName = LinkedHashMap<String, MutableList<DoubleArray>>()
            val elements = json.optJSONArray("elements") ?: return emptyList()
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val name = e.optJSONObject("tags")?.optString("name")?.ifBlank { null } ?: continue
                val geo = e.optJSONArray("geometry") ?: continue
                byName.getOrPut(name) { ArrayList() } += coords(geo)
            }
            return byName.map { (name, lines) -> Street(name, lines) }
        }

        /** Overpass geometry [{lat, lon}, ...] → flat [lon, lat, ...]. */
        private fun coords(geo: JSONArray) = DoubleArray(geo.length() * 2).also { out ->
            for (k in 0 until geo.length()) {
                val pt = geo.getJSONObject(k)
                out[2 * k] = pt.getDouble("lon")
                out[2 * k + 1] = pt.getDouble("lat")
            }
        }
    }
}

package com.mistysteps.app.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Neighbourhoods (wijken) and their streets, as saved on this phone by earlier versions.
 *
 * Earlier versions fetched these from OpenStreetMap's Overpass service. That put a load on
 * shared servers for every user, so the app no longer fetches anything: it only reads what an
 * earlier version already saved. Downloadable town maps will bring them back for everyone
 * (docs/superpowers/specs/2026-11-10-offline-maps-design.md).
 */
class Neighbourhoods(context: Context) {

    private val root = File(context.filesDir, "boundaries")
    private val hoodDir = File(root, "neighbourhoods")
    private val streetDir = File(root, "streets")
    private val hoods = HashMap<Long, List<Boundary>?>() // by city area id
    private val streets = HashMap<Long, List<Street>?>() // by neighbourhood area id

    /** All saved neighbourhoods of [city], or null if none were ever saved. Blocking (disk). */
    @Synchronized
    fun saved(city: Boundary): List<Boundary>? {
        val area = city.overpassAreaId ?: return null
        return hoods.getOrPut(area) { saved(File(hoodDir, "$area.json"))?.let(::parseHoods) }
    }

    /** Saved named streets in [hood], or null if they were never saved. Blocking (disk). */
    @Synchronized
    fun streets(hood: Boundary): List<Street>? {
        val area = hood.overpassAreaId ?: return null
        return streets.getOrPut(area) { saved(File(streetDir, "$area.json"))?.let(::parseStreets) }
    }

    private fun saved(file: File): JSONObject? =
        if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrNull() else null

    companion object {
        /** Preferred kinds, best first: a wijk-sized area is big enough to explore, small enough to finish. */
        private val ORDER = listOf("quarter", "suburb", "neighbourhood", "admin11", "admin10", "admin9")

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

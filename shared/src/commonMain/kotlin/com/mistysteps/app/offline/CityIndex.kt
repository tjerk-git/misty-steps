package com.mistysteps.app.offline

import com.mistysteps.app.stats.Boundary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * A country's border and the borders of all its towns, shipped with the app. For the Netherlands
 * the towns are the woonplaatsen, the same areas the app's old Nominatim lookups called "city".
 *
 * Built from an OpenStreetMap dump by `tools/maps/index.py`, so finding your town needs no
 * network at all: the lookup happens on the phone.
 */
class CityIndex(val country: Boundary, val cities: List<Town>) {

    /** One town. Its [Boundary] (with its lookup tables) is only built once a point falls in its box. */
    class Town(
        val name: String,
        val osmId: Long,
        private val minLat: Double, private val maxLat: Double,
        private val minLon: Double, private val maxLon: Double,
        polygons: List<List<DoubleArray>>,
    ) {
        val boundary: Boundary by lazy { Boundary(name, polygons, "relation", osmId) }

        fun inBox(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon
    }

    /** The town around (lat, lon), or null outside every town. */
    fun cityAt(lat: Double, lon: Double): Boundary? =
        cities.firstOrNull { it.inBox(lat, lon) && it.boundary.contains(lat, lon) }?.boundary

    companion object {
        /** The newest index format this app understands. */
        const val FORMAT = 1

        /**
         * The Netherlands, in `assets/`. Plain JSON: the Android build unpacks .gz assets and
         * drops the extension, and compresses the APK anyway.
         */
        const val NL_ASSET = "nl-index.json"

        fun parse(text: String): CityIndex {
            val json = Json.parseToJsonElement(text).jsonObject
            val format = json.getValue("format").jsonPrimitive.int
            require(format <= FORMAT) { "Index format $format is newer than this app understands ($FORMAT)" }
            val c = json.getValue("country").jsonObject
            val country = Boundary(
                c.getValue("name").jsonPrimitive.content, polygons(c.getValue("polygons").jsonArray),
                "relation", c.getValue("osmId").jsonPrimitive.long,
            )
            return CityIndex(country, json.getValue("cities").jsonArray.map { town(it.jsonObject) })
        }

        private fun town(o: JsonObject): Town {
            val polygons = polygons(o.getValue("polygons").jsonArray)
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (rings in polygons) for (i in rings[0].indices step 2) { // outer rings bound the holes
                minLon = minOf(minLon, rings[0][i]); maxLon = maxOf(maxLon, rings[0][i])
                minLat = minOf(minLat, rings[0][i + 1]); maxLat = maxOf(maxLat, rings[0][i + 1])
            }
            return Town(
                o.getValue("name").jsonPrimitive.content, o.getValue("osmId").jsonPrimitive.long,
                minLat, maxLat, minLon, maxLon, polygons,
            )
        }

        /** [[ring, hole, ...], ...] where a ring is a flat [lon, lat, lon, lat, ...] array. */
        private fun polygons(arr: JsonArray): List<List<DoubleArray>> = arr.map { p ->
            p.jsonArray.map { ring ->
                val r = ring.jsonArray
                DoubleArray(r.size) { r[it].jsonPrimitive.double }
            }
        }
    }
}

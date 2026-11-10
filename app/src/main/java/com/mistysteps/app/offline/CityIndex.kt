package com.mistysteps.app.offline

import android.content.Context
import com.mistysteps.app.stats.Boundary
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

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

        fun load(context: Context, asset: String = NL_ASSET): CityIndex =
            context.assets.open(asset).use { read(it) }

        fun read(input: InputStream): CityIndex = parse(input.bufferedReader().readText())

        fun parse(text: String): CityIndex {
            val json = JSONObject(text)
            val format = json.getInt("format")
            require(format <= FORMAT) { "Index format $format is newer than this app understands ($FORMAT)" }
            val c = json.getJSONObject("country")
            val country = Boundary(c.getString("name"), polygons(c.getJSONArray("polygons")), "relation", c.getLong("osmId"))
            val list = json.getJSONArray("cities")
            return CityIndex(country, (0 until list.length()).map { town(list.getJSONObject(it)) })
        }

        private fun town(o: JSONObject): Town {
            val polygons = polygons(o.getJSONArray("polygons"))
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (rings in polygons) for (i in rings[0].indices step 2) { // outer rings bound the holes
                minLon = minOf(minLon, rings[0][i]); maxLon = maxOf(maxLon, rings[0][i])
                minLat = minOf(minLat, rings[0][i + 1]); maxLat = maxOf(maxLat, rings[0][i + 1])
            }
            return Town(o.getString("name"), o.getLong("osmId"), minLat, maxLat, minLon, maxLon, polygons)
        }

        /** [[ring, hole, ...], ...] where a ring is a flat [lon, lat, lon, lat, ...] array. */
        private fun polygons(arr: JSONArray): List<List<DoubleArray>> = (0 until arr.length()).map { p ->
            val rings = arr.getJSONArray(p)
            (0 until rings.length()).map { r ->
                val ring = rings.getJSONArray(r)
                DoubleArray(ring.length()) { ring.getDouble(it) }
            }
        }
    }
}

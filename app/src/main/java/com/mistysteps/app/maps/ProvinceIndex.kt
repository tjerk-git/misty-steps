package com.mistysteps.app.maps

import android.content.Context
import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.stats.Boundary
import org.json.JSONArray
import org.json.JSONObject

class Province(val id: String, val name: String, val boundary: Boundary)

/**
 * The twelve Dutch provinces, from `assets/nl-provinces.json`: which one a coordinate is in, all on the
 * phone. Borders are simplified (about 300 m), good enough to pick a map to download.
 */
class ProvinceIndex(val provinces: List<Province>) {
    fun provinceAt(lat: Double, lon: Double): Province? = provinces.firstOrNull { it.boundary.contains(lat, lon) }

    /** The province with most of [points], or null when none of them is in a province. */
    fun mostWalked(points: List<TrailPoint>): Province? =
        points.mapNotNull { provinceAt(it.lat, it.lon) }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

    companion object {
        private const val FORMAT = 1
        private const val ASSET = "nl-provinces.json"

        fun load(context: Context, asset: String = ASSET): ProvinceIndex =
            context.assets.open(asset).use { parse(it.bufferedReader().readText()) }

        fun parse(text: String): ProvinceIndex {
            val json = JSONObject(text)
            val format = json.getInt("format")
            require(format <= FORMAT) { "Province index format $format is newer than this app understands ($FORMAT)" }
            val list = json.getJSONArray("provinces")
            return ProvinceIndex((0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                Province(o.getString("id"), o.getString("name"), Boundary(o.getString("name"), polygons(o.getJSONArray("polygons"))))
            })
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

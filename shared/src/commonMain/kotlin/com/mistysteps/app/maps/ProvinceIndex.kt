package com.mistysteps.app.maps

import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.stats.Boundary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        const val ASSET = "nl-provinces.json"

        fun parse(text: String): ProvinceIndex {
            val json = Json.parseToJsonElement(text).jsonObject
            val format = json.getValue("format").jsonPrimitive.int
            require(format <= FORMAT) { "Province index format $format is newer than this app understands ($FORMAT)" }
            val list = json.getValue("provinces").jsonArray
            return ProvinceIndex(list.map { el ->
                val o = el.jsonObject
                val name = o.getValue("name").jsonPrimitive.content
                Province(o.getValue("id").jsonPrimitive.content, name, Boundary(name, polygons(o.getValue("polygons").jsonArray)))
            })
        }

        /** [[ring, hole, ...], ...] where a ring is a flat [lon, lat, lon, lat, ...] array. */
        private fun polygons(arr: JsonArray): List<List<DoubleArray>> = arr.map { p ->
            p.jsonArray.map { ring ->
                val values = ring.jsonArray
                DoubleArray(values.size) { values[it].jsonPrimitive.double }
            }
        }
    }
}

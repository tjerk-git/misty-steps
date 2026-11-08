package com.mistysteps.app.stats

import android.content.Context
import android.os.SystemClock
import com.mistysteps.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Finds the city / country around a point, with its border.
 *
 * Borders come from OpenStreetMap's Nominatim service and are kept on the phone, so each
 * area is fetched once. Before asking the network we check whether the point already lies
 * inside a border we have. Only a rounded (~1 km) position is ever sent.
 */
class BoundaryProvider(context: Context) {

    enum class Level(val zoom: Int, val simplify: Double) {
        CITY(zoom = 10, simplify = 0.0003),
        COUNTRY(zoom = 3, simplify = 0.003),
    }

    private val root = File(context.filesDir, "boundaries")
    private val memory = HashMap<Level, MutableList<Boundary>>()

    /** Blocking; call off the main thread. Throws on network trouble. */
    @Synchronized
    fun find(lat: Double, lon: Double, level: Level): Boundary? {
        val known = cached(level)
        known.firstOrNull { it.contains(lat, lon) }?.let { return it }

        val json = fetch(lat, lon, level) ?: return null
        val boundary = parse(json) ?: return null
        if (boundary.hasArea) {
            val id = "${json.optString("osm_type")}${json.optLong("osm_id")}"
            File(dir(level), "$id.json").writeText(json.toString())
            known += boundary
        }
        return boundary
    }

    private fun cached(level: Level): MutableList<Boundary> = memory.getOrPut(level) {
        dir(level).listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { runCatching { parse(JSONObject(it.readText())) }.getOrNull() }
            .toMutableList()
    }

    private fun dir(level: Level) = File(root, level.name.lowercase()).apply { mkdirs() }

    private fun fetch(lat: Double, lon: Double, level: Level): JSONObject? {
        throttle()
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                "&lat=${round2(lat)}&lon=${round2(lon)}" +
                "&zoom=${level.zoom}&polygon_geojson=1&polygon_threshold=${level.simplify}" +
                "&accept-language=${Locale.getDefault().toLanguageTag()}"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", "MistySteps/${BuildConfig.VERSION_NAME} (Android)")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) error("Nominatim answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            return if (json.has("error")) null else json
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: JSONObject): Boundary? {
        val name = json.optString("name").ifBlank {
            json.optString("display_name").substringBefore(",").ifBlank { return null }
        }
        val geo = json.optJSONObject("geojson")
        val coords = geo?.optJSONArray("coordinates")
        val polygons: List<List<DoubleArray>> = when (geo?.optString("type")) {
            "Polygon" -> listOf(rings(coords!!))
            "MultiPolygon" -> (0 until coords!!.length()).map { rings(coords.getJSONArray(it)) }
            else -> emptyList() // a point: we know the name but not the border
        }
        return Boundary(name, polygons, json.optString("osm_type").ifBlank { null }, json.optLong("osm_id"))
    }

    private fun rings(arr: JSONArray): List<DoubleArray> = (0 until arr.length()).map { r ->
        val ring = arr.getJSONArray(r)
        DoubleArray(ring.length() * 2).also { out ->
            for (i in 0 until ring.length()) {
                val pt = ring.getJSONArray(i)
                out[2 * i] = pt.getDouble(0)
                out[2 * i + 1] = pt.getDouble(1)
            }
        }
    }

    private companion object {
        private var lastRequest = 0L

        /** Nominatim's usage policy: at most one request per second. */
        @Synchronized
        fun throttle() {
            val wait = lastRequest + 1_100 - SystemClock.elapsedRealtime()
            if (wait > 0) Thread.sleep(wait)
            lastRequest = SystemClock.elapsedRealtime()
        }

        fun round2(v: Double) = String.format(Locale.US, "%.2f", v)
    }
}

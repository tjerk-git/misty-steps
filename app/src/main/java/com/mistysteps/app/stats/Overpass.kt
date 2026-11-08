package com.mistysteps.app.stats

import android.os.SystemClock
import com.mistysteps.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Asks OpenStreetMap's Overpass service for map data. The public servers are often busy,
 * so this tries a few of them in turn. Queries only ever name an OSM area (a city or a
 * neighbourhood), never your position.
 */
object Overpass {
    private val servers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    private var lastRequest = 0L

    /**
     * Blocking; call off the main thread. Tries each server once, waiting at most
     * [readTimeoutMs] for each answer. Throws if none of them answers.
     */
    @Synchronized
    fun query(ql: String, readTimeoutMs: Int = 30_000): JSONObject {
        var last: Exception? = null
        for (server in servers) {
            val wait = lastRequest + 1_000 - SystemClock.elapsedRealtime()
            if (wait > 0) Thread.sleep(wait)
            lastRequest = SystemClock.elapsedRealtime()
            try {
                return post(server, ql, readTimeoutMs)
            } catch (e: Exception) {
                android.util.Log.w("Overpass", "$server: ${e.message}")
                last = e
            }
        }
        throw last ?: IllegalStateException("No Overpass server answered")
    }

    private fun post(server: String, ql: String, readTimeoutMs: Int): JSONObject {
        val conn = URL(server).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", "MistySteps/${BuildConfig.VERSION_NAME} (Android)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Overpass ${conn.responseCode} from $server")
            val text = conn.inputStream.bufferedReader().readText()
            // Busy servers sometimes answer 200 with an error page instead of JSON.
            if (!text.trimStart().startsWith("{")) error("Overpass sent no JSON from $server")
            return JSONObject(text).also {
                if (it.optString("remark").contains("runtime error")) error("Overpass: ${it.optString("remark")}")
            }
        } finally {
            conn.disconnect()
        }
    }
}

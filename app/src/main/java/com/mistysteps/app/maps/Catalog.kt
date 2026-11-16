package com.mistysteps.app.maps

import android.content.Context
import com.mistysteps.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A file on the maps server, with what the app needs to check it after downloading. */
data class FileRef(val url: String, val bytes: Long, val sha256: String)

/** One province's pack in the catalog. [stats] is the neighbourhoods and streets file (not published yet). */
data class PackEntry(
    val id: String,
    val name: String,
    val year: Int,
    val bbox: List<Double>,
    val map: FileRef,
    val stats: FileRef?,
)

/** The catalog is newer than this app understands: update the app to use new maps. */
class CatalogFormatException(val format: Int) : Exception("Catalog format $format is newer than this app understands (${Catalog.FORMAT})")

class Catalog(val year: Int, val dataDate: String, val provinces: List<PackEntry>) {
    fun entry(id: String): PackEntry? = provinces.firstOrNull { it.id == id }

    companion object {
        const val FORMAT = 2
        const val BASE = "https://maps.potloodgum.com/"
        const val URL = BASE + "v2/catalog.json"
        private val SHA256 = Regex("[0-9a-f]{64}")

        fun parse(text: String): Catalog {
            val json = JSONObject(text)
            val format = json.getInt("format")
            if (format > FORMAT) throw CatalogFormatException(format)
            val year = json.getInt("year")
            val list = json.getJSONArray("provinces")
            val provinces = (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                val bbox = o.getJSONArray("bbox")
                PackEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    year = year,
                    bbox = (0 until bbox.length()).map { bbox.getDouble(it) },
                    map = fileRef(o.getJSONObject("map")),
                    stats = o.optJSONObject("stats")?.let(::fileRef),
                )
            }
            return Catalog(year, json.getString("dataDate"), provinces)
        }

        private fun fileRef(o: JSONObject): FileRef {
            val bytes = o.getLong("bytes")
            val sha = o.getString("sha256")
            require(bytes >= 0) { "negative size" }
            require(SHA256.matches(sha)) { "sha256 must be 64 lower-case hex characters" }
            val url = o.getString("url")
            return FileRef(if (url.startsWith("https://")) url else BASE + url.trimStart('/'), bytes, sha)
        }
    }
}

/** Keeps the last good catalog in `filesDir/maps/catalog.json` and refreshes it at most once a week. */
class CatalogStore(context: Context) {
    private val file = File(context.filesDir, "maps/catalog.json")
    private val userAgent = "${context.packageName}/${BuildConfig.VERSION_NAME}"

    private val _needsAppUpdate = MutableStateFlow(false)
    /** True once the server catalog turned out newer than this app can read. */
    val needsAppUpdate: StateFlow<Boolean> = _needsAppUpdate

    fun cached(): Catalog? = runCatching { Catalog.parse(file.readText()) }.getOrNull()

    /** The fresh catalog, or the cached one when offline, too new or recently refreshed. Null if there is neither. */
    suspend fun refresh(force: Boolean = false): Catalog? = withContext(Dispatchers.IO) {
        val have = cached()
        if (!force && have != null && System.currentTimeMillis() - file.lastModified() < WEEK_MS) return@withContext have
        try {
            val text = fetch()
            val fresh = Catalog.parse(text)
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(text)
            temp.renameTo(file)
            _needsAppUpdate.value = false
            fresh
        } catch (e: CatalogFormatException) {
            _needsAppUpdate.value = true
            have
        } catch (e: Exception) {
            have
        }
    }

    private fun fetch(): String {
        val connection = URL(Catalog.URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", userAgent)
        return try {
            check(connection.responseCode == 200) { "catalog HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

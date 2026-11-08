package com.mistysteps.app.data

import com.mistysteps.app.pin.Pin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything worth keeping, as one JSON file you can save anywhere and load on another phone
 * (or in the Dev build). Readable on purpose: your trails are yours.
 *
 * {
 *   "format": "misty-steps-backup", "version": 1, "exportedAt": <ms>, "app": "1.1.0",
 *   "distanceM": 21700.0, "character": "horse",
 *   "sessions": [ { "id", "start", "end", "distanceM", "points", "batteryStart", "batteryEnd", "newAreaM2" } ],
 *   "points": [ [lat, lon, time, radius, sessionId or null], ... ],
 *   "pins": [ { "lat", "lon", "time", "label", "note" or null, "sessionId" or null } ]
 * }
 * "pins" was added later without a version bump: older files simply don't have it.
 */
data class Backup(
    val exportedAt: Long,
    val appVersion: String,
    val distanceM: Double,
    val character: String?,
    val sessions: List<Session>,
    val points: List<TrailPoint>,
    val pins: List<Pin> = emptyList(),
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        root.put("app", appVersion)
        root.put("distanceM", distanceM)
        character?.let { root.put("character", it) }
        root.put("sessions", JSONArray().also { arr ->
            for (s in sessions) arr.put(JSONObject().apply {
                put("id", s.id)
                put("start", s.startTime)
                s.endTime?.let { put("end", it) }
                put("distanceM", s.distanceM)
                put("points", s.points)
                s.batteryStart?.let { put("batteryStart", it) }
                s.batteryEnd?.let { put("batteryEnd", it) }
                s.newAreaM2?.let { put("newAreaM2", it) }
            })
        })
        root.put("points", JSONArray().also { arr ->
            for (p in points) arr.put(JSONArray().apply {
                put(p.lat); put(p.lon); put(p.time); put(p.radius)
                put(p.sessionId ?: JSONObject.NULL)
            })
        })
        root.put("pins", JSONArray().also { arr ->
            for (p in pins) arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lon", p.lon)
                put("time", p.time)
                put("label", p.labelKey)
                put("note", p.note ?: JSONObject.NULL)
                put("sessionId", p.sessionId ?: JSONObject.NULL)
            })
        })
        return root.toString()
    }

    companion object {
        private const val BROKEN_PIN = "This backup has a broken pin in it."

        private fun readPin(o: JSONObject?): Pin {
            require(o != null) { BROKEN_PIN }
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            val label = if (o.isNull("label")) "" else o.optString("label")
            require(lat in -90.0..90.0 && lon in -180.0..180.0 && label.isNotBlank() && o.has("time")) { BROKEN_PIN }
            return Pin(
                id = 0,
                lat = lat,
                lon = lon,
                time = o.optLong("time"),
                labelKey = label,
                note = if (o.isNull("note")) null else o.optString("note").ifBlank { null },
                sessionId = if (o.isNull("sessionId")) null else o.optLong("sessionId"),
            )
        }

        const val FORMAT = "misty-steps-backup"
        const val VERSION = 1

        /** Throws [IllegalArgumentException] with a readable message if this isn't a Misty Steps backup. */
        fun fromJson(text: String): Backup {
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("This file isn't a Misty Steps backup.")
            }
            require(root.optString("format") == FORMAT) { "This file isn't a Misty Steps backup." }
            require(root.optInt("version") <= VERSION) { "This backup comes from a newer Misty Steps. Update the app first." }
            val sessions = root.optJSONArray("sessions") ?: JSONArray()
            val points = root.optJSONArray("points") ?: JSONArray()
            val pins = root.optJSONArray("pins") ?: JSONArray()
            fun JSONObject.longOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
            fun JSONObject.intOrNull(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
            fun JSONObject.doubleOrNull(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
            return Backup(
                exportedAt = root.optLong("exportedAt"),
                appVersion = root.optString("app"),
                distanceM = root.optDouble("distanceM", 0.0),
                character = root.optString("character").ifBlank { null },
                sessions = (0 until sessions.length()).map { i ->
                    val s = sessions.getJSONObject(i)
                    Session(
                        id = s.getLong("id"),
                        startTime = s.getLong("start"),
                        endTime = s.longOrNull("end"),
                        distanceM = s.optDouble("distanceM", 0.0),
                        points = s.optInt("points"),
                        batteryStart = s.intOrNull("batteryStart"),
                        batteryEnd = s.intOrNull("batteryEnd"),
                        newAreaM2 = s.doubleOrNull("newAreaM2"),
                    )
                },
                points = (0 until points.length()).map { i ->
                    val p = points.getJSONArray(i)
                    val lat = p.getDouble(0)
                    val lon = p.getDouble(1)
                    require(lat in -90.0..90.0 && lon in -180.0..180.0) { "This backup has a broken spot in it." }
                    TrailPoint(lat, lon, p.getLong(2), p.getDouble(3), if (p.isNull(4)) null else p.getLong(4))
                },
                pins = (0 until pins.length()).map { i -> readPin(pins.optJSONObject(i)) },
            )
        }
    }
}

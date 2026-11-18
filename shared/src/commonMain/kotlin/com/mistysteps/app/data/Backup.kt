package com.mistysteps.app.data

import com.mistysteps.app.character.CharacterNames
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.backup_broken_pin
import com.mistysteps.app.resources.backup_broken_point
import com.mistysteps.app.resources.backup_newer
import com.mistysteps.app.resources.backup_not_a_backup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import org.jetbrains.compose.resources.StringResource

/**
 * Everything worth keeping, as one JSON file you can save anywhere and load on another phone
 * (or in the Dev build, or on another platform). Readable on purpose: your trails are yours.
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
    /** The names you gave your characters, by character id. Added later: older files don't have it. */
    val characterNames: Map<String, String> = emptyMap(),
) {
    fun toJson(): String = buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("app", appVersion)
        put("distanceM", distanceM)
        character?.let { put("character", it) }
        if (characterNames.isNotEmpty()) put("characterNames", buildJsonObject { characterNames.forEach { (id, name) -> put(id, name) } })
        put("sessions", buildJsonArray {
            for (s in sessions) add(buildJsonObject {
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
        put("points", buildJsonArray {
            for (p in points) add(buildJsonArray {
                add(JsonPrimitive(p.lat)); add(JsonPrimitive(p.lon)); add(JsonPrimitive(p.time)); add(JsonPrimitive(p.radius))
                add(p.sessionId?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        })
        put("pins", buildJsonArray {
            for (p in pins) add(buildJsonObject {
                put("lat", p.lat)
                put("lon", p.lon)
                put("time", p.time)
                put("label", p.labelKey)
                put("note", p.note?.let { JsonPrimitive(it) } ?: JsonNull)
                put("sessionId", p.sessionId?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        })
    }.toString()

    companion object {
        const val FORMAT = "misty-steps-backup"
        const val VERSION = 1

        private fun brokenPin() = BackupException(BackupProblem.BROKEN_PIN, "This backup has a broken pin in it.")

        private fun JsonObject.present(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive
        private fun JsonObject.longOrNull(k: String) = present(k)?.long
        private fun JsonObject.intOrNull(k: String) = present(k)?.int
        private fun JsonObject.doubleOrNull(k: String) = present(k)?.double

        private fun readPin(o: JsonObject?): Pin {
            if (o == null) throw brokenPin()
            val lat = o.present("lat")?.doubleOrNull ?: Double.NaN
            val lon = o.present("lon")?.doubleOrNull ?: Double.NaN
            val label = o.present("label")?.contentOrNull.orEmpty()
            if (!(lat in -90.0..90.0 && lon in -180.0..180.0 && label.isNotBlank() && o.containsKey("time"))) throw brokenPin()
            return Pin(
                id = 0,
                lat = lat,
                lon = lon,
                time = o.present("time")?.longOrNull ?: 0L,
                labelKey = label,
                note = o.present("note")?.contentOrNull?.ifBlank { null },
                sessionId = o.present("sessionId")?.longOrNull,
            )
        }

        /** Names from a file: cleaned the same way as typed ones; anything that isn't text, or is blank, is dropped. */
        private fun readNames(o: JsonObject?): Map<String, String> {
            if (o == null) return emptyMap()
            val names = LinkedHashMap<String, String>()
            for ((id, value) in o) {
                val raw = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                CharacterNames.clean(raw)?.let { names[id] = it }
            }
            return names
        }

        /** Throws [BackupException] (an [IllegalArgumentException]) if this isn't a usable Misty Steps backup. */
        fun fromJson(text: String): Backup {
            val root = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw BackupException(BackupProblem.NOT_A_BACKUP, "This file isn't a Misty Steps backup.")
            }
            if (root.present("format")?.contentOrNull != FORMAT) {
                throw BackupException(BackupProblem.NOT_A_BACKUP, "This file isn't a Misty Steps backup.")
            }
            if ((root.present("version")?.intOrNull ?: 0) > VERSION) {
                throw BackupException(BackupProblem.NEWER, "This backup comes from a newer Misty Steps. Update the app first.")
            }
            val sessions = root["sessions"] as? JsonArray ?: JsonArray(emptyList())
            val points = root["points"] as? JsonArray ?: JsonArray(emptyList())
            val pins = root["pins"] as? JsonArray ?: JsonArray(emptyList())
            return Backup(
                exportedAt = root.present("exportedAt")?.longOrNull ?: 0L,
                appVersion = root.present("app")?.contentOrNull.orEmpty(),
                distanceM = root.present("distanceM")?.doubleOrNull ?: 0.0,
                character = root.present("character")?.contentOrNull?.ifBlank { null },
                characterNames = readNames(root["characterNames"] as? JsonObject),
                sessions = sessions.map { el ->
                    val s = el.jsonObject
                    Session(
                        id = s.getValue("id").jsonPrimitive.long,
                        startTime = s.getValue("start").jsonPrimitive.long,
                        endTime = s.longOrNull("end"),
                        distanceM = s.present("distanceM")?.doubleOrNull ?: 0.0,
                        points = s.present("points")?.intOrNull ?: 0,
                        batteryStart = s.intOrNull("batteryStart"),
                        batteryEnd = s.intOrNull("batteryEnd"),
                        newAreaM2 = s.doubleOrNull("newAreaM2"),
                    )
                },
                points = points.map { el ->
                    val p = el.jsonArray
                    val lat = p[0].jsonPrimitive.double
                    val lon = p[1].jsonPrimitive.double
                    if (!(lat in -90.0..90.0 && lon in -180.0..180.0)) {
                        throw BackupException(BackupProblem.BROKEN_POINT, "This backup has a broken spot in it.")
                    }
                    TrailPoint(lat, lon, p[2].jsonPrimitive.long, p[3].jsonPrimitive.double, p.getOrNull(4)?.takeIf { it !is JsonNull }?.jsonPrimitive?.long)
                },
                pins = pins.map { el -> readPin(el as? JsonObject) },
            )
        }
    }
}

/** Why a file can't be imported. The text shown to the user comes from [res]. */
enum class BackupProblem(val res: StringResource) {
    NOT_A_BACKUP(Res.string.backup_not_a_backup),
    NEWER(Res.string.backup_newer),
    BROKEN_PIN(Res.string.backup_broken_pin),
    BROKEN_POINT(Res.string.backup_broken_point),
}

class BackupException(val problem: BackupProblem, message: String) : IllegalArgumentException(message)

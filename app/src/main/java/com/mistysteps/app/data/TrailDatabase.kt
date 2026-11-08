package com.mistysteps.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Every place you have walked, stored only on this device. */
class TrailDatabase(context: Context) :
    SQLiteOpenHelper(context, "trails.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        createPoints(db)
        createSessions(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS points_session ON points(session_id)")
    }

    private fun createPoints(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                time INTEGER NOT NULL,
                radius REAL NOT NULL DEFAULT ${FogStore.CLEAR_RADIUS_M},
                session_id INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE points ADD COLUMN radius REAL NOT NULL DEFAULT ${FogStore.CLEAR_RADIUS_M}")
        }
        if (oldVersion < 3) {
            // Points recorded before walks were logged keep a NULL session.
            db.execSQL("ALTER TABLE points ADD COLUMN session_id INTEGER")
            createSessions(db)
        } else if (oldVersion < 4) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN new_area_m2 REAL")
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE INDEX IF NOT EXISTS points_session ON points(session_id)")
        }
    }

    /** One row per walk. The id is the start time in epoch millis. */
    private fun createSessions(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                distance_m REAL NOT NULL DEFAULT 0,
                points INTEGER NOT NULL DEFAULT 0,
                battery_start INTEGER,
                battery_end INTEGER,
                new_area_m2 REAL
            )
            """.trimIndent()
        )
    }

    fun insert(points: List<TrailPoint>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues(5)
            for (p in points) {
                values.put("lat", p.lat)
                values.put("lon", p.lon)
                values.put("time", p.time)
                values.put("radius", p.radius)
                if (p.sessionId != null) values.put("session_id", p.sessionId) else values.putNull("session_id")
                db.insert("points", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadAll(): List<TrailPoint> {
        val out = ArrayList<TrailPoint>()
        readableDatabase.rawQuery("SELECT lat, lon, time, radius, session_id FROM points ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                out += TrailPoint(
                    c.getDouble(0), c.getDouble(1), c.getLong(2), c.getDouble(3),
                    if (c.isNull(4)) null else c.getLong(4),
                )
            }
        }
        return out
    }

    fun startSession(s: Session) {
        writableDatabase.insert("sessions", null, ContentValues(3).apply {
            put("id", s.id)
            put("start_time", s.startTime)
            s.batteryStart?.let { put("battery_start", it) }
        })
    }

    fun endSession(s: Session) {
        writableDatabase.update(
            "sessions",
            ContentValues(5).apply {
                put("end_time", s.endTime)
                put("distance_m", s.distanceM)
                put("points", s.points)
                s.batteryEnd?.let { put("battery_end", it) }
                s.newAreaM2?.let { put("new_area_m2", it) }
            },
            "id = ?", arrayOf(s.id.toString()),
        )
    }

    /**
     * Adds a backup's walks and spots. Only adds: spots already here (same place and time) and
     * walks already here (same id) are skipped, nothing is changed or deleted.
     * Returns (spots added, walks added).
     */
    fun importData(points: List<TrailPoint>, sessions: List<Session>): Pair<Int, Int> {
        val db = writableDatabase
        val have = HashSet<String>()
        db.rawQuery("SELECT lat, lon, time FROM points", null).use { c ->
            while (c.moveToNext()) have += key(c.getDouble(0), c.getDouble(1), c.getLong(2))
        }
        var addedPoints = 0
        var addedSessions = 0
        db.beginTransaction()
        try {
            for (s in sessions) {
                val v = ContentValues(8).apply {
                    put("id", s.id)
                    put("start_time", s.startTime)
                    s.endTime?.let { put("end_time", it) }
                    put("distance_m", s.distanceM)
                    put("points", s.points)
                    s.batteryStart?.let { put("battery_start", it) }
                    s.batteryEnd?.let { put("battery_end", it) }
                    s.newAreaM2?.let { put("new_area_m2", it) }
                }
                if (db.insertWithOnConflict("sessions", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) addedSessions++
            }
            val v = ContentValues(5)
            for (p in points) {
                if (!have.add(key(p.lat, p.lon, p.time))) continue
                v.put("lat", p.lat)
                v.put("lon", p.lon)
                v.put("time", p.time)
                v.put("radius", p.radius)
                if (p.sessionId != null) v.put("session_id", p.sessionId) else v.putNull("session_id")
                db.insert("points", null, v)
                addedPoints++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return addedPoints to addedSessions
    }

    fun countPoints(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM points", null).use { c ->
        c.moveToFirst(); c.getLong(0)
    }

    private fun key(lat: Double, lon: Double, time: Long) = "$lat,$lon,$time"

    /** Gives walks that never got an end time (app killed) the time of their last spot. */
    fun closeDanglingSessions() {
        writableDatabase.execSQL(
            """
            UPDATE sessions SET
                end_time = COALESCE((SELECT MAX(time) FROM points WHERE session_id = sessions.id), start_time),
                points = (SELECT COUNT(*) FROM points WHERE session_id = sessions.id)
            WHERE end_time IS NULL
            """.trimIndent()
        )
    }

    /** Newest first. [limit] null = all. */
    fun loadSessions(limit: Int?): List<Session> {
        val out = ArrayList<Session>()
        readableDatabase.rawQuery(
            "$SESSION_COLUMNS FROM sessions ORDER BY start_time DESC" + (limit?.let { " LIMIT $it" } ?: ""), null,
        ).use { c -> while (c.moveToNext()) out += readSession(c) }
        return out
    }

    fun loadSession(id: Long): Session? =
        readableDatabase.rawQuery("$SESSION_COLUMNS FROM sessions WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToNext()) readSession(c) else null
        }

    private fun readSession(c: android.database.Cursor) = Session(
        id = c.getLong(0),
        startTime = c.getLong(1),
        endTime = if (c.isNull(2)) null else c.getLong(2),
        distanceM = c.getDouble(3),
        points = c.getInt(4),
        batteryStart = if (c.isNull(5)) null else c.getInt(5),
        batteryEnd = if (c.isNull(6)) null else c.getInt(6),
        newAreaM2 = if (c.isNull(7)) null else c.getDouble(7),
    )

    /** The spots of one walk, in order. */
    fun loadSessionPoints(id: Long): List<TrailPoint> = query(
        "SELECT lat, lon, time, radius, session_id FROM points WHERE session_id = ? ORDER BY id", arrayOf(id.toString()),
    )

    /** Spots from other walks (or from before walks were logged) inside a lat/lon box. */
    fun loadOtherPointsIn(id: Long, minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<TrailPoint> = query(
        "SELECT lat, lon, time, radius, session_id FROM points " +
            "WHERE (session_id IS NULL OR session_id != ?) AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
        arrayOf(id.toString(), minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString()),
    )

    private fun query(sql: String, args: Array<String>): List<TrailPoint> {
        val out = ArrayList<TrailPoint>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += TrailPoint(
                    c.getDouble(0), c.getDouble(1), c.getLong(2), c.getDouble(3),
                    if (c.isNull(4)) null else c.getLong(4),
                )
            }
        }
        return out
    }

    private companion object {
        const val SESSION_COLUMNS =
            "SELECT id, start_time, end_time, distance_m, points, battery_start, battery_end, new_area_m2"
    }
}

data class TrailPoint(
    val lat: Double,
    val lon: Double,
    val time: Long,
    val radius: Double = FogStore.CLEAR_RADIUS_M,
    /** The walk this spot belongs to; null for spots from before walks were logged. */
    val sessionId: Long? = null,
)

/** One walk: from "Start wandering" to stop. */
data class Session(
    val id: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceM: Double = 0.0,
    val points: Int = 0,
    /** Battery percentage when the walk started and ended. */
    val batteryStart: Int? = null,
    val batteryEnd: Int? = null,
    /** Ground this walk cleared that no earlier walk had, in m². Null until the walk has ended. */
    val newAreaM2: Double? = null,
)

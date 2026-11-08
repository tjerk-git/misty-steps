package com.mistysteps.app.data

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.mistysteps.app.record.SpeedGate
import com.mistysteps.app.stats.ClearedCells
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * Single source of truth for where the fog has been lifted.
 * Writes go to SQLite on a background thread; the in-memory [index] is main-thread only.
 */
object FogStore {
    /** Radius of fog cleared around every recorded spot. */
    const val CLEAR_RADIUS_M = 50.0

    /** The spot where a walk starts gets a bigger clearing. */
    const val START_CLEAR_RADIUS_M = 100.0

    /** Don't store a new spot until we've moved at least this far. */
    private const val MIN_STEP_M = 10f

    /** Fill gaps between fixes with a spot every this many metres... */
    private const val FILL_STEP_M = 20f

    /** ...but only if the gap is walkable; bigger jumps (GPS loss, a train) are not filled. */
    private const val MAX_FILL_GAP_M = 300f

    /** Ignore fixes worse than this. */
    const val MAX_ACCURACY_M = 35f

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var db: TrailDatabase
    private lateinit var prefs: android.content.SharedPreferences

    /** Main-thread only. Replaced once the stored trail has been loaded. */
    var index = PointIndex()
        private set

    /** The lines of every walk, for drawing. Main-thread only, replaced like [index]. */
    var trails = Trails()
        private set

    /** The walk being recorded right now, or null. Main-thread only. */
    var session: Session? = null
        private set
    private var sessionDistance = 0.0
    private var sessionPoints = 0
    private var lastFixTime = 0L

    private val gate = SpeedGate()
    /** Set after a stretch in a vehicle, so the next spot isn't treated as a walk's start. */
    private var resumingAfterVehicle = false

    private val _inVehicle = MutableStateFlow(false)
    /** True while you seem to be in a car, train or bus; the fog then doesn't clear. */
    val inVehicle: StateFlow<Boolean> = _inVehicle.asStateFlow()

    private val _finished = MutableSharedFlow<Session>(extraBufferCapacity = 4)
    /** Each walk once it has ended and its new ground has been counted. */
    val finishedWalks: SharedFlow<Session> = _finished.asSharedFlow()

    private val _version = MutableStateFlow(0)
    /** Bumps whenever new fog has been cleared. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var last: Location? = null

    /** The latest GPS fix good enough to record, walking or not. For dropping pins. */
    @Volatile
    var lastGoodFix: Location? = null
        private set

    /** Most recent place we know you were (last fix, or end of the stored trail). */
    @Volatile
    var lastKnown: Pair<Double, Double>? = null
        private set

    fun init(context: Context) {
        db = TrailDatabase(context.applicationContext)
        PinStore.init(db)
        prefs = context.getSharedPreferences("misty", Context.MODE_PRIVATE)
        _distanceMeters.value = prefs.getFloat("distance_m", 0f).toDouble()
        savedDistance = _distanceMeters.value
        io.execute {
            // A walk the app was killed during never got its final update; close it at its last spot.
            db.closeDanglingSessions()
            loadFromDatabase(keepLive = true)
        }
    }

    /**
     * IO thread. Rebuilds the in-memory spots and trail lines from the database. With [keepLive],
     * spots recorded while this was loading (only possible at app start) are kept as well.
     */
    private fun loadFromDatabase(keepLive: Boolean) {
        val points = db.loadAll()
        points.lastOrNull()?.let { if (lastKnown == null) lastKnown = it.lat to it.lon }
        // Projecting every point is the slow part, so do it here, not on the main thread.
        val built = PointIndex()
        val builtTrails = Trails()
        for (p in points) {
            built.add(p.lat, p.lon, p.radius)
            builtTrails.add(p)
        }
        main.post {
            if (keepLive) {
                built.addAll(index)
                builtTrails.addAll(trails)
            }
            index = built
            trails = builtTrails
            _loaded.value = true
            _version.value++
        }
    }

    // ---- backup ---------------------------------------------------------------------------

    /** Everything worth keeping, for a backup file. Blocking; call off the main thread. */
    fun exportBackup(appVersion: String, character: String?): Backup = io.submit<Backup> {
        Backup(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            distanceM = _distanceMeters.value,
            character = character,
            sessions = db.loadSessions(null),
            points = db.loadAll(),
        )
    }.get()

    data class ImportResult(
        val spots: Int,
        val walks: Int,
        /** True when this phone had no trails yet, so the backup's settings (character) can be used too. */
        val wasEmpty: Boolean,
    )

    /**
     * Adds a backup to what's on this phone; nothing here is deleted or changed. Don't call while
     * recording. Blocking; call off the main thread.
     */
    fun importBackup(backup: Backup): ImportResult = io.submit<ImportResult> {
        val wasEmpty = db.countPoints() == 0L
        val knownWalks = db.loadSessions(null).map { it.id }.toSet()
        val (spots, walks) = db.importData(backup.points, backup.sessions)
        // Distance walked: on an empty phone take the backup's total, otherwise add the new walks.
        val extra = if (wasEmpty) backup.distanceM
            else backup.sessions.filter { it.id !in knownWalks }.sumOf { it.distanceM }
        main.post {
            _distanceMeters.value = _distanceMeters.value + extra
            flush()
        }
        loadFromDatabase(keepLive = false)
        ImportResult(spots, walks, wasEmpty)
    }.get()

    /**
     * Starts a fresh walk: the next fix won't be joined to the previous walk's last one,
     * and a new session is logged. Main thread.
     */
    fun beginWalk(batteryPct: Int?): Session {
        last = null
        val now = System.currentTimeMillis()
        // The id is the start time; nudge it if two walks somehow start in the same millisecond.
        val id = maxOf(now, (session?.id ?: 0L) + 1)
        val s = Session(id = id, startTime = now, batteryStart = batteryPct)
        session = s
        sessionDistance = 0.0
        sessionPoints = 0
        lastFixTime = now
        gate.reset()
        _inVehicle.value = false
        resumingAfterVehicle = false
        io.execute { db.startSession(s) }
        return s
    }

    /** Ends the current walk and saves its log entry. Returns it, or null if no walk was running. */
    fun endWalk(batteryPct: Int?): Session? {
        val s = snapshot(batteryPct) ?: return null
        session = null
        last = null
        gate.reset()
        _inVehicle.value = false
        flush()
        io.execute {
            db.endSession(s)
            // Count the ground nobody had cleared before this walk. Off the main thread: it
            // projects every spot of the walk and of earlier walks nearby onto ~10 m cells.
            val done = try {
                s.copy(newAreaM2 = newGround(s.id)).also { db.endSession(it) }
            } catch (e: Exception) {
                android.util.Log.w("FogStore", "Could not count new ground", e)
                s
            }
            main.post {
                com.mistysteps.app.debug.BatteryLog.walkFinished(done)
                _finished.tryEmit(done)
            }
        }
        _version.value++ // this walk's trail turns from "current" to "past"
        return s
    }

    /** IO thread. Area this walk cleared that no other walk has. */
    private fun newGround(id: Long): Double {
        val mine = db.loadSessionPoints(id)
        if (mine.isEmpty()) return 0.0
        // Only spots near this walk can overlap it.
        val pad = 0.01 // ~1 km of latitude, plenty for 100 m circles
        val padLon = pad / kotlin.math.cos(Math.toRadians(mine[0].lat)).coerceAtLeast(0.05)
        val others = db.loadOtherPointsIn(
            id,
            mine.minOf { it.lat } - pad, mine.minOf { it.lon } - padLon,
            mine.maxOf { it.lat } + pad, mine.maxOf { it.lon } + padLon,
        )
        return ClearedCells.from(mine).areaNotIn(ClearedCells.from(others))
    }

    /** A walk with its points, for the postcard. Blocking; call off the main thread. */
    fun loadWalk(id: Long): Pair<Session, List<TrailPoint>>? = io.submit<Pair<Session, List<TrailPoint>>?> {
        db.loadSession(id)?.let { it to db.loadSessionPoints(id) }
    }.get()

    private fun snapshot(batteryPct: Int? = null): Session? = session?.copy(
        endTime = maxOf(lastFixTime, session!!.startTime),
        distanceM = sessionDistance,
        points = sessionPoints,
        batteryEnd = batteryPct,
    )

    /** Recent walks, newest first. Blocking; call off the main thread. */
    fun loadSessions(limit: Int = 30): List<Session> =
        io.submit<List<Session>> { db.loadSessions(limit) }.get()

    /** Feed a GPS fix. Must be called on the main thread. */
    fun onLocation(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        lastKnown = loc.latitude to loc.longitude
        lastGoodFix = loc
        if (session != null) {
            val vehicle = gate.update(loc.elapsedRealtimeNanos / 1_000_000, loc.latitude, loc.longitude)
            if (vehicle != _inVehicle.value) _inVehicle.value = vehicle
            if (vehicle) {
                // Don't clear, and don't join the trail across the ride.
                last = null
                resumingAfterVehicle = true
                return
            }
        }
        val prev = last
        val sid = session?.id
        val newPoints = ArrayList<TrailPoint>()
        var newSegment = false
        if (prev == null) {
            val radius = if (resumingAfterVehicle) CLEAR_RADIUS_M else START_CLEAR_RADIUS_M
            resumingAfterVehicle = false
            newPoints += TrailPoint(loc.latitude, loc.longitude, loc.time, radius, sid)
            newSegment = true
        } else {
            val d = prev.distanceTo(loc)
            if (d < MIN_STEP_M) return
            if (d <= MAX_FILL_GAP_M) {
                val steps = ceil(d / FILL_STEP_M).toInt()
                // Go the short way round when stepping across the 180° meridian.
                var dLon = loc.longitude - prev.longitude
                if (dLon > 180) dLon -= 360
                if (dLon < -180) dLon += 360
                for (i in 1 until steps) {
                    val f = i.toDouble() / steps
                    newPoints += TrailPoint(
                        prev.latitude + (loc.latitude - prev.latitude) * f,
                        wrapLon(prev.longitude + dLon * f),
                        prev.time + ((loc.time - prev.time) * f).toLong(),
                        sessionId = sid,
                    )
                }
                addDistance(d.toDouble())
            } else {
                newSegment = true // too far to have walked: don't draw a line across the jump
            }
            newPoints += TrailPoint(loc.latitude, loc.longitude, loc.time, sessionId = sid)
        }
        last = loc
        lastFixTime = System.currentTimeMillis()
        sessionPoints += newPoints.size

        for ((i, p) in newPoints.withIndex()) {
            index.add(p.lat, p.lon, p.radius)
            trails.add(p, newSegment && i == 0)
        }
        _version.value++
        io.execute { db.insert(newPoints) }
    }

    /** Blocking read of the whole trail; call off the main thread. */
    fun loadAllPoints(): List<TrailPoint> = io.submit<List<TrailPoint>> { db.loadAll() }.get()

    private var savedDistance = 0.0

    private fun addDistance(d: Double) {
        sessionDistance += d
        val total = _distanceMeters.value + d
        _distanceMeters.value = total
        if (total - savedDistance >= 50) flush()
    }

    /** Persists the walked distance. Called every 50 m and when a walk ends. */
    fun flush() {
        val total = _distanceMeters.value
        if (total == savedDistance) return
        savedDistance = total
        prefs.edit().putFloat("distance_m", total.toFloat()).apply()
        // Keep the walk's log entry current too, in case the app gets killed mid-walk.
        snapshot()?.let { io.execute { db.endSession(it) } }
    }

    private fun wrapLon(lon: Double): Double = when {
        lon >= 180 -> lon - 360
        lon < -180 -> lon + 360
        else -> lon
    }
}

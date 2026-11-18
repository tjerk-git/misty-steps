package com.mistysteps.app.record

import com.mistysteps.app.data.FogConstants
import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.data.Trails
import kotlin.math.ceil

/** A GPS fix as the recorder needs it. [timeMs] is the wall clock, [elapsedMs] a monotonic clock. */
class RecordedFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float?,
    val timeMs: Long,
    val elapsedMs: Long,
)

/** What a fix adds to the trail: [points] to clear, whether they start a new line, and the metres walked. */
class RecorderStep(val points: List<TrailPoint>, val newSegment: Boolean, val distanceM: Double)

/**
 * Turns GPS fixes into cleared spots: drops poor fixes, fills the gap between two fixes with evenly spaced spots,
 * does not join across a jump, and stops clearing while you seem to be in a motor vehicle ([SpeedGate]).
 * Shared by every platform so a walk records the same everywhere. Not thread safe: feed it from one thread.
 */
class WalkRecorder {
    private val gate = SpeedGate()
    private var last: RecordedFix? = null

    /** Set after a stretch in a vehicle, so the next spot isn't treated as a walk's start. */
    private var resumingAfterVehicle = false

    /** True while you seem to be in a car, train or bus; the fog then doesn't clear. */
    var inVehicle = false
        private set

    /** Starts a fresh walk: the next fix won't be joined to the previous walk's last one. */
    fun begin() = reset()

    fun end() = reset()

    private fun reset() {
        last = null
        gate.reset()
        inVehicle = false
        resumingAfterVehicle = false
    }

    /** Whether a fix is good enough to use at all. */
    fun accepts(fix: RecordedFix): Boolean = fix.accuracyM == null || fix.accuracyM <= MAX_ACCURACY_M

    /**
     * Feed a fix that [accepts] said yes to. [sessionId] is the walk being recorded; without one there is
     * no vehicle check. Null when nothing is to be added (in a vehicle, or not moved far enough).
     */
    fun onFix(fix: RecordedFix, sessionId: Long?): RecorderStep? {
        if (sessionId != null) {
            inVehicle = gate.update(fix.elapsedMs, fix.lat, fix.lon)
            if (inVehicle) {
                // Don't clear, and don't join the trail across the ride.
                last = null
                resumingAfterVehicle = true
                return null
            }
        }
        val prev = last
        val points = ArrayList<TrailPoint>()
        var newSegment = false
        var distance = 0.0
        if (prev == null) {
            val radius = if (resumingAfterVehicle) FogConstants.CLEAR_RADIUS_M else FogConstants.START_CLEAR_RADIUS_M
            resumingAfterVehicle = false
            points += TrailPoint(fix.lat, fix.lon, fix.timeMs, radius, sessionId)
            newSegment = true
        } else {
            val d = Trails.approxDistanceM(prev.lat, prev.lon, fix.lat, fix.lon)
            if (d < MIN_STEP_M) return null
            if (d <= MAX_FILL_GAP_M) {
                val steps = ceil(d / FILL_STEP_M).toInt()
                // Go the short way round when stepping across the 180 degree meridian.
                var dLon = fix.lon - prev.lon
                if (dLon > 180) dLon -= 360
                if (dLon < -180) dLon += 360
                for (i in 1 until steps) {
                    val f = i.toDouble() / steps
                    points += TrailPoint(
                        prev.lat + (fix.lat - prev.lat) * f,
                        wrapLon(prev.lon + dLon * f),
                        prev.timeMs + ((fix.timeMs - prev.timeMs) * f).toLong(),
                        sessionId = sessionId,
                    )
                }
                distance = d
            } else {
                newSegment = true // too far to have walked: don't draw a line across the jump
            }
            points += TrailPoint(fix.lat, fix.lon, fix.timeMs, sessionId = sessionId)
        }
        last = fix
        return RecorderStep(points, newSegment, distance)
    }

    private fun wrapLon(lon: Double): Double = when {
        lon >= 180 -> lon - 360
        lon < -180 -> lon + 360
        else -> lon
    }

    companion object {
        /** Ignore fixes worse than this. */
        const val MAX_ACCURACY_M = 35f

        /** Don't store a new spot until we've moved at least this far. */
        const val MIN_STEP_M = 10.0

        /** Fill gaps between fixes with a spot every this many metres... */
        const val FILL_STEP_M = 20.0

        /** ...but only if the gap is walkable; bigger jumps (GPS loss, a train) are not filled. */
        const val MAX_FILL_GAP_M = 300.0
    }
}

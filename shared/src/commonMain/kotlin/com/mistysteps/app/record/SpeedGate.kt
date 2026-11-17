package com.mistysteps.app.record

import com.mistysteps.app.data.Trails

/**
 * Tells moving under your own power from riding in a motor vehicle. Walking, running,
 * cycling, skating, a step, a wheelchair or a mobility scooter all clear the mist; once
 * you've been going faster than that for a while (car, train, bus out of town), clearing
 * pauses until you slow down again.
 *
 * Uses the straight-line distance over the last [WINDOW_MS] of fixes, which is steadier than
 * the GPS's own speed reading. Speed alone can't catch a slow city bus; see "Tell buses
 * from bikes" in workflow.md.
 */
class SpeedGate {
    private val times = ArrayDeque<Long>()
    private val lats = ArrayDeque<Double>()
    private val lons = ArrayDeque<Double>()

    /** True while you seem to be in a motor vehicle. */
    var inVehicle = false
        private set

    /** Latest speed estimate in m/s, or null without enough fixes yet. */
    var speed: Double? = null
        private set

    /** Feed a fix (time in ms). Returns [inVehicle]. */
    fun update(time: Long, lat: Double, lon: Double): Boolean {
        times.addLast(time); lats.addLast(lat); lons.addLast(lon)
        while (times.size > 2 && time - times[1] >= WINDOW_MS) {
            times.removeFirst(); lats.removeFirst(); lons.removeFirst()
        }
        val span = time - times.first()
        if (times.size < 3 || span < MIN_SPAN_MS) {
            speed = null
            return inVehicle
        }
        val v = Trails.approxDistanceM(lats.first(), lons.first(), lat, lon) / (span / 1000.0)
        speed = v
        if (!inVehicle && v > VEHICLE_ABOVE_MS) inVehicle = true
        else if (inVehicle && v < OWN_POWER_BELOW_MS) inVehicle = false
        return inVehicle
    }

    fun reset() {
        times.clear(); lats.clear(); lons.clear()
        inVehicle = false
        speed = null
    }

    companion object {
        const val WINDOW_MS = 30_000L
        const val MIN_SPAN_MS = 15_000L
        /** 32 km/h held for 30 s: faster than nearly anyone cycles, e-bikes stop helping at 25. */
        const val VEHICLE_ABOVE_MS = 32 / 3.6
        /** 25 km/h: back to clearing (hysteresis, so a car slowing for a turn doesn't flip it back). */
        const val OWN_POWER_BELOW_MS = 25 / 3.6
    }
}

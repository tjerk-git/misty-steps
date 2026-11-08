package com.mistysteps.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Normalised Web Mercator: x and y both run from 0 to 1 across the whole world
 * (x from 180°W eastwards, y from ~85°N southwards). This is the projection the
 * OSM tiles use, so a point here maps to the screen with a single scale + offset.
 */
object Mercator {
    const val MAX_LAT = 85.05112878
    private const val EARTH_CIRCUMFERENCE_M = 2 * PI * 6_378_137.0

    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val s = sin(Math.toRadians(clamped))
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    /** Inverse of [x]. */
    fun lon(x: Double): Double = x * 360.0 - 180.0

    /** Inverse of [y]. */
    fun lat(y: Double): Double = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * y))))

    /** Length of [meters] on the ground at [lat], expressed in normalised mercator units. */
    fun metersToUnits(meters: Double, lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        return meters / (EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(clamped)))
    }
}

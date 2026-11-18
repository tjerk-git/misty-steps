package com.mistysteps.app.data

/** Values shared by the fog logic on every platform. */
object FogConstants {
    const val CLEAR_RADIUS_M = 50.0

    /** The spot where a walk starts gets a bigger clearing. */
    const val START_CLEAR_RADIUS_M = 100.0
}

data class TrailPoint(
    val lat: Double,
    val lon: Double,
    val time: Long,
    val radius: Double = FogConstants.CLEAR_RADIUS_M,
    /** The walk this spot belongs to; null for spots from before walks were logged. */
    val sessionId: Long? = null,
)

package com.mistysteps.app.data

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

/** Where the walks are kept on the device; the session counterpart of [PointStore]. */
interface SessionStore {
    fun startSession(s: Session)

    /** Saves the walk's end: time, distance, spot count, battery and new ground. */
    fun endSession(s: Session)

    /** Newest first. [limit] null = all. */
    fun loadSessions(limit: Int?): List<Session>
}

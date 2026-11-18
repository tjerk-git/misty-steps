package com.mistysteps.app.map

data class Fix(
    val lat: Double,
    val lon: Double,
    val bearing: Float?,
    val speed: Float?,
    val accuracy: Float?,
    val time: Long,
)

object FixRules {
    const val MAX_AGE_MS = 10_000L

    /** The newest fix wins; at the same moment the more accurate one does. */
    fun prefer(current: Fix?, candidate: Fix): Fix = when {
        current == null -> candidate
        candidate.time > current.time -> candidate
        candidate.time < current.time -> current
        (candidate.accuracy ?: Float.MAX_VALUE) < (current.accuracy ?: Float.MAX_VALUE) -> candidate
        else -> current
    }

    fun isStale(fix: Fix, now: Long): Boolean = now - fix.time > MAX_AGE_MS
}

package com.mistysteps.app.fog

/** How much the mist costs to draw. Each step down is cheaper; [MINIMAL] is a still veil with the cleared holes. */
enum class FogLevel {
    /** Two drifting swirl layers, about 12 frames a second of drift. */
    FULL,

    /** One swirl layer, drifting about 4 frames a second. */
    LITE,

    /** No swirls and no drift: the veil and the cleared holes only. */
    MINIMAL,
}

/**
 * Picks a [FogLevel] from how long the app's frames really take, so a slow or throttled phone gets a cheaper mist
 * instead of a map that answers late.
 *
 * It steps down when the typical (median) frame is slow, and back up only after a long calm stretch; each time a step
 * back up is followed by another step down soon after, the wait before the next try doubles, so it settles instead of
 * flip-flopping. Battery saver (which caps CPU speed) keeps it at [FogLevel.LITE] or below.
 *
 * Pure logic: the caller passes frame times and the clock.
 */
class FogGovernor(
    private val slowMs: Double = SLOW_MS,
    private val fastMs: Double = FAST_MS,
    private val window: Int = WINDOW,
    private val degradeCooldownMs: Long = DEGRADE_COOLDOWN_MS,
    private val baseRecoverMs: Long = BASE_RECOVER_MS,
) {
    var level = FogLevel.FULL
        private set

    private val frames = DoubleArray(window)
    private var count = 0          // frames recorded since the level last changed (capped at window)
    private var next = 0
    private var changedAt = Long.MIN_VALUE / 2
    private var recoveredAt = Long.MIN_VALUE / 2
    private var flops = 0
    private var powerSave = false

    /** Battery saver on: never above [FogLevel.LITE]. Returns true when the level changed. */
    fun setPowerSave(on: Boolean, nowMs: Long): Boolean {
        powerSave = on
        if (on && level == FogLevel.FULL) return moveTo(FogLevel.LITE, nowMs)
        return false
    }

    /** One finished frame took [durationMs]. Returns true when the level changed. */
    fun onFrame(durationMs: Double, nowMs: Long): Boolean {
        frames[next] = durationMs
        next = (next + 1) % window
        if (count < window) count++
        if (count < window) return false
        val typical = median()
        val since = nowMs - changedAt
        if (typical > slowMs && level != FogLevel.MINIMAL && since >= degradeCooldownMs) {
            // Slow again soon after climbing back up: wait longer before the next try.
            if (nowMs - recoveredAt < RECENT_RECOVERY_MS) flops++
            return moveTo(FogLevel.entries[level.ordinal + 1], nowMs)
        }
        val floor = if (powerSave) FogLevel.LITE else FogLevel.FULL
        if (typical < fastMs && level.ordinal > floor.ordinal && since >= recoverWaitMs()) {
            recoveredAt = nowMs
            return moveTo(FogLevel.entries[level.ordinal - 1], nowMs)
        }
        return false
    }

    private fun recoverWaitMs(): Long = minOf(baseRecoverMs shl minOf(flops, 4), MAX_RECOVER_MS)

    private fun moveTo(to: FogLevel, nowMs: Long): Boolean {
        level = to
        changedAt = nowMs
        count = 0 // judge the new level on its own frames
        next = 0
        return true
    }

    private fun median(): Double = frames.copyOf().also { it.sort() }[window / 2]

    companion object {
        /** A typical frame slower than this (about two missed frames at 60 Hz) means the phone can't keep up. */
        const val SLOW_MS = 32.0

        /** A typical frame faster than this is comfortable, so a richer level may be tried. */
        const val FAST_MS = 16.0
        const val WINDOW = 30
        const val DEGRADE_COOLDOWN_MS = 10_000L
        const val BASE_RECOVER_MS = 120_000L
        const val MAX_RECOVER_MS = 30 * 60_000L
        const val RECENT_RECOVERY_MS = 10 * 60_000L
    }
}

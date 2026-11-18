package com.mistysteps.app.map

import kotlin.math.exp
import kotlin.math.hypot

sealed class FollowStep {
    /** Nothing to do this frame. */
    object Hold : FollowStep()

    /** Move the camera centre to ([x], [y]) in mercator units; [settled] when it has arrived. */
    class Glide(val x: Double, val y: Double, val settled: Boolean) : FollowStep()

    /** Too far to slide: fly the camera to ([x], [y]). */
    class Fly(val x: Double, val y: Double) : FollowStep()
}

/**
 * The camera easing after your character: every frame it closes part of the gap, so it drifts after you
 * instead of jumping with each GPS fix. Kept apart from the map so it can be tested.
 *
 * The map only keeps whole pixels, so the small per-frame steps of a slow glide would be rounded away; the
 * exact position is kept here and the map gets the result.
 */
class FollowGlide {
    private var camX = Double.NaN
    private var camY = Double.NaN
    /** True once the camera has caught up; it then waits for a real move before gliding again. */
    private var caughtUp = false

    fun reset() {
        camX = Double.NaN
        caughtUp = false
    }

    /**
     * [centerX]/[centerY]: where the map's camera really is; [targetX]/[targetY]: where it should go (both
     * mercator units, 0..1); [worldPx]: the world's width in pixels at this zoom; [dtMs]: time since the last step.
     */
    fun step(
        centerX: Double, centerY: Double, targetX: Double, targetY: Double,
        worldPx: Double, viewW: Int, viewH: Int, dtMs: Long,
    ): FollowStep {
        // Someone else moved the map (pinch zoom, a fly): start from where it is now.
        if (camX.isNaN() || hypot((camX - centerX) * worldPx, (camY - centerY) * worldPx) > RESYNC_PX) {
            camX = centerX
            camY = centerY
        }
        var dx = targetX - camX
        if (dx > 0.5) dx -= 1.0
        if (dx < -0.5) dx += 1.0
        val dy = targetY - camY
        val off = hypot(dx * worldPx, dy * worldPx)

        val step: FollowStep = when {
            // Far away (first fix, or you travelled): fly there instead of sliding across the world.
            off > FAR_SCREENS * maxOf(viewW, viewH) -> {
                caughtUp = false
                return FollowStep.Fly(targetX, targetY)
            }
            // Caught up and you've barely moved (GPS jitter): stay put, no redraws.
            caughtUp && off < START_PX -> FollowStep.Hold
            off > SETTLED_PX -> {
                var f = 1 - exp(-dtMs.coerceIn(1, 100) / TAU_MS)
                // The last bit: land exactly on the target and stop.
                if (off * (1 - f) < SETTLED_PX) f = 1.0
                camX = (camX + dx * f).mod(1.0)
                camY += dy * f
                FollowStep.Glide(camX, camY, settled = f >= 1.0)
            }
            else -> FollowStep.Hold
        }
        caughtUp = step is FollowStep.Hold || (step is FollowStep.Glide && step.settled)
        return step
    }

    private companion object {
        /** Slow and floaty. */
        const val TAU_MS = 900.0
        /** Closer than this (screen pixels) counts as centred. */
        const val SETTLED_PX = 0.5
        /** Once centred, gliding starts again only when you're this far (screen pixels) off. */
        const val START_PX = 8.0
        /** Further than this many screens away flies. */
        const val FAR_SCREENS = 2.5
        /** The map was moved by someone else when it is this far (pixels) from where we think it is. */
        const val RESYNC_PX = 3.0
    }
}

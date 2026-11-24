package com.mistysteps.app.postcard

import kotlin.math.roundToLong

/** How many steps a walk's distance is for one animal: a small joke on the postcard, so the strides are playful approximations. */
object WalkSteps {
    /** The step length used for a character whose sprite sheet entry has no `stride` line. */
    const val DEFAULT_STRIDE_M = 0.3

    /** The number of steps of [strideM] metres in [distanceM]; 0 for no distance, no stride, or numbers that are not real. */
    fun count(distanceM: Double, strideM: Double): Long {
        if (!distanceM.isFinite() || !strideM.isFinite() || distanceM <= 0.0 || strideM <= 0.0) return 0L
        val steps = distanceM / strideM
        return if (steps >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else steps.roundToLong()
    }

    /** Exact below 100 steps, two significant digits from 100 up: 31,234 reads as 31,000. */
    fun readable(steps: Long): Long {
        if (steps < 100L) return steps
        var magnitude = 10L
        while (steps / magnitude >= 100L) {
            if (magnitude > Long.MAX_VALUE / 10) return steps
            magnitude *= 10L
        }
        val rounded = (steps / magnitude + if (steps % magnitude * 2 >= magnitude) 1L else 0L)
        return rounded * magnitude
    }
}

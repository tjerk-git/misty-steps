package com.mistysteps.app.fog

import kotlin.math.min

/**
 * What the fog mask has to show: fog pixel = mercator * [k] + off, for a [w] x [h] pixel view,
 * with the spots as of [version].
 */
data class MaskView(val offX: Double, val offY: Double, val k: Double, val w: Int, val h: Int, val version: Int)

/** What a built mask covers: the view plus [padX] / [padY] extra pixels on every side. */
data class MaskSpec(
    val offX: Double, val offY: Double, val k: Double,
    val w: Int, val h: Int,
    val padX: Int, val padY: Int,
    val viewW: Int, val viewH: Int,
    val version: Int,
)

/** Where to draw a built mask now: view pixel = mask pixel * [scale] + [tx] / [ty]. */
data class Placement(val scale: Double, val tx: Double, val ty: Double)

/**
 * Rules for the mask that is built off the main thread: it is built a little larger than the
 * view, so while panning or zooming the last finished mask can be moved and scaled to fit until
 * a fresh one arrives.
 */
object MaskPlan {
    private const val PAD = 0.25
    private const val MIN_SCALE = 0.8
    private const val MAX_SCALE = 1.25

    fun specFor(v: MaskView): MaskSpec {
        val padX = (v.w * PAD).toInt()
        val padY = (v.h * PAD).toInt()
        return MaskSpec(
            offX = v.offX + padX, offY = v.offY + padY, k = v.k,
            w = v.w + 2 * padX, h = v.h + 2 * padY,
            padX = padX, padY = padY, viewW = v.w, viewH = v.h, version = v.version,
        )
    }

    fun place(built: MaskSpec, now: MaskView): Placement {
        val s = now.k / built.k
        return Placement(s, now.offX - built.offX * s, now.offY - built.offY * s)
    }

    fun needsRebuild(built: MaskSpec?, now: MaskView): Boolean {
        if (built == null || built.version != now.version) return true
        if (built.viewW != now.w || built.viewH != now.h) return true
        val p = place(built, now)
        if (p.scale < MIN_SCALE || p.scale > MAX_SCALE) return true
        // Room left around the view; rebuild once less than half of the padding remains.
        val slack = minOf(
            -p.tx, -p.ty,
            p.tx + built.w * p.scale - now.w,
            p.ty + built.h * p.scale - now.h,
        )
        return slack < min(built.padX, built.padY) * p.scale / 2
    }
}

/**
 * One job at a time; while one runs, only the newest request is kept. Main thread only.
 * [request] returns the job to start now, or null if it has to wait; [finished] returns the
 * waiting job to start next, if any.
 */
class MaskJobs<T : Any> {
    private var running = false
    private var waiting: T? = null

    fun request(job: T): T? {
        if (running) { waiting = job; return null }
        running = true
        return job
    }

    fun finished(): T? {
        val next = waiting
        waiting = null
        running = next != null
        return next
    }
}

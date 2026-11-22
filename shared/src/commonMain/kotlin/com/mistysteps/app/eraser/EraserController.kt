package com.mistysteps.app.eraser

import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.map.LatLon
import com.mistysteps.app.map.MapProjection

/** Where the eraser puts its spots and takes them back from: the fog store (a fake in tests). */
interface ErasedStore {
    fun add(points: List<TrailPoint>)
    fun remove(strokeId: Long, points: List<TrailPoint>)
}

/**
 * The magic eraser on the map. While [active], one finger wipes the mist along its path (the mist comes off as the
 * finger moves); a second finger ends the stroke and hands the gesture back to the map, so pan and zoom still work.
 * Every stroke can be taken back with [undo] (the last [EraserRules.UNDO_LIMIT] of them) until the eraser is put down.
 * Main thread only.
 */
class EraserController(
    private val projection: MapProjection,
    private val store: ErasedStore,
    private val density: Float,
    /** Turns the map's own one-finger pan (and double-tap zoom) on or off. */
    private val setMapGestures: (Boolean) -> Unit,
    /** Milliseconds since 1970; passed in because common code has no clock of its own. */
    private val clock: () -> Long,
) {
    /** Metres of drag the person may still erase: unlimited when the eraser is bought, the taste that is left otherwise. */
    var allowance: () -> Double = { Double.POSITIVE_INFINITY }

    /** Called with the length of each finished drag, so a free taste can be counted down. */
    var onSpent: (Double) -> Unit = {}

    /** The allowance ran out in the middle of, or at the start of, a drag. */
    var onOutOfAllowance: () -> Unit = {}

    /** A drag started while the map was zoomed out too far. */
    var onZoomedOut: () -> Unit = {}

    /** The undo list changed (for the Undo button). */
    var onUndoChanged: () -> Unit = {}

    private val session = EraserSession()
    val canUndo: Boolean get() = session.canUndo

    var active = false
        set(value) {
            if (field == value) return
            if (!value) {
                endStroke()
                session.clear()
                setMapGestures(true)
                onUndoChanged()
            }
            field = value
        }

    private var walker: SpotWalker? = null
    private var strokeId = 0L
    private var lastId = 0L
    private var radiusM = 0.0
    private var budgetM = Double.POSITIVE_INFINITY
    private var lastPoint: LatLon? = null
    private val strokeSpots = ArrayList<TrailPoint>()

    /** The first finger lands at screen position ([x], [y]). */
    fun down(x: Float, y: Float) {
        if (active) begin(x, y)
    }

    /** The finger moved to ([x], [y]); only a lone finger erases. */
    fun move(x: Float, y: Float, pointerCount: Int) {
        if (active && walker != null && pointerCount == 1) extend(x, y)
    }

    /** A second finger landed: this stroke ends and the map pans and zooms. */
    fun secondFinger() {
        if (!active) return
        endStroke()
        setMapGestures(true)
    }

    /** The finger lifted, or the gesture was cancelled. */
    fun up() {
        if (active) endStroke()
    }

    fun undo() {
        val stroke = session.undo() ?: return
        store.remove(stroke.id, stroke.spots)
        onUndoChanged()
    }

    private fun begin(x: Float, y: Float) {
        if (!EraserRules.canErase(projection.zoom)) {
            onZoomedOut()
            return
        }
        budgetM = allowance()
        if (budgetM <= 0.0) {
            onOutOfAllowance()
            return
        }
        setMapGestures(false)
        val at = projection.fromScreen(x, y)
        val pixelsPerMeter = projection.metersToPixels(1.0, at.lat)
        radiusM = EraserRules.brushRadiusM(EraserRules.BRUSH_DP * density, pixelsPerMeter)
        strokeId = maxOf(clock(), lastId + 1)
        lastId = strokeId
        walker = SpotWalker(radiusM)
        strokeSpots.clear()
        lastPoint = at
        commit(walker!!.add(at))
    }

    private fun extend(x: Float, y: Float) {
        val w = walker ?: return
        var p = projection.fromScreen(x, y)
        val previous = lastPoint ?: p
        // Cut the drag exactly where the allowance ends.
        val room = budgetM - w.dragM
        val step = EraserRules.distance(previous, p)
        val outOfAllowance = step >= room
        if (outOfAllowance) p = EraserRules.interpolate(previous, p, if (step == 0.0) 0.0 else room / step)
        lastPoint = p
        commit(w.add(p))
        if (outOfAllowance) {
            endStroke()
            onOutOfAllowance()
        }
    }

    private fun commit(spots: List<LatLon>) {
        if (spots.isEmpty()) return
        val points = spotsToPoints(strokeId, strokeId + strokeSpots.size, spots, radiusM)
        strokeSpots += points
        store.add(points)
    }

    private fun endStroke() {
        val w = walker ?: return
        walker = null
        commit(w.finish())
        if (strokeSpots.isNotEmpty()) {
            session.push(EraserStroke(strokeId, strokeSpots.toList()))
            onUndoChanged()
        }
        onSpent(w.dragM)
        strokeSpots.clear()
        lastPoint = null
    }
}

package com.mistysteps.app.eraser

import com.mistysteps.app.data.TrailPoint

/** One drag of the eraser: its id (the start time) and the spots it cleared. */
class EraserStroke(val id: Long, val spots: List<TrailPoint>)

/** The strokes of one go with the eraser, newest last; only the last [EraserRules.UNDO_LIMIT] can be undone. */
class EraserSession {
    private val strokes = ArrayDeque<EraserStroke>()

    val size: Int get() = strokes.size
    val canUndo: Boolean get() = strokes.isNotEmpty()

    fun push(stroke: EraserStroke) {
        strokes.addLast(stroke)
        while (strokes.size > EraserRules.UNDO_LIMIT) strokes.removeFirst()
    }

    /** The newest stroke, removed from the list, or null when there is nothing to undo. */
    fun undo(): EraserStroke? = strokes.removeLastOrNull()

    fun clear() = strokes.clear()
}

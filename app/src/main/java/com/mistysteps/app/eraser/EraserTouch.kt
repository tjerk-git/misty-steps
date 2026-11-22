package com.mistysteps.app.eraser

import android.view.MotionEvent
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.TrailPoint

object FogErasedStore : ErasedStore {
    override fun add(points: List<TrailPoint>) = FogStore.addErased(points)
    override fun remove(strokeId: Long, points: List<TrailPoint>) = FogStore.removeErased(strokeId, points)
}

/** Feed every touch event of the map to this; it never consumes them (the map must see them too). */
fun EraserController.onTouch(e: MotionEvent) {
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> down(e.x, e.y)
        MotionEvent.ACTION_MOVE -> move(e.x, e.y, e.pointerCount)
        MotionEvent.ACTION_POINTER_DOWN -> secondFinger()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> up()
    }
}

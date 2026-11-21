package com.mistysteps.app.eraser

import com.mistysteps.app.data.TrailPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EraserSessionTest {
    private fun stroke(id: Long) = EraserStroke(id, listOf(TrailPoint(53.2, 5.8, id, 50.0, null)))

    @Test
    fun undoReturnsTheNewestStrokeFirst() {
        val s = EraserSession()
        s.push(stroke(1)); s.push(stroke(2))
        assertEquals(2L, s.undo()?.id)
        assertEquals(1L, s.undo()?.id)
        assertNull(s.undo())
    }

    @Test
    fun onlyTheLastTenStrokesCanBeUndone() {
        val s = EraserSession()
        for (id in 1L..11L) s.push(stroke(id))
        assertEquals(10, s.size)
        val undone = generateSequence { s.undo() }.map { it.id }.toList()
        assertEquals((11L downTo 2L).toList(), undone)
    }

    @Test
    fun clearForgetsEverythingAndCanUndoSaysSo() {
        val s = EraserSession()
        assertFalse(s.canUndo)
        s.push(stroke(1))
        assertTrue(s.canUndo)
        s.clear()
        assertFalse(s.canUndo)
        assertNull(s.undo())
    }
}

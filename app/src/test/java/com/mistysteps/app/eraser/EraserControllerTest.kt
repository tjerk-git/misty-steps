package com.mistysteps.app.eraser

import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.map.LatLon
import com.mistysteps.app.map.MercatorProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserControllerTest {
    private class FakeStore : ErasedStore {
        val added = ArrayList<TrailPoint>()
        val removed = ArrayList<Pair<Long, List<TrailPoint>>>()
        override fun add(points: List<TrailPoint>) { added += points }
        override fun remove(strokeId: Long, points: List<TrailPoint>) { removed += strokeId to points }
    }

    // MapLibre zoom 16 on a 1080 x 2400 screen (512 px tiles): about 0.71 m per pixel at this latitude.
    private fun projection(zoom: Double = 16.0) = MercatorProjection(1080, 2400, 53.2, 5.8, zoom, tileSize = 512)

    private class Rig(zoom: Double = 16.0, p: MercatorProjection) {
        val store = FakeStore()
        val gestures = ArrayList<Boolean>()
        var clock = 1_000_000L
        val controller = EraserController(p, store, density = 3f, setMapGestures = { gestures += it }, clock = { clock })
        var outOfAllowance = 0
        var zoomedOut = 0
        var spent = 0.0
        init {
            controller.onOutOfAllowance = { outOfAllowance++ }
            controller.onZoomedOut = { zoomedOut++ }
            controller.onSpent = { spent += it }
            controller.active = true
        }
        /** A finger dragging [pixels] to the right in steps of 10 px, starting at (100, 1200). */
        fun drag(pixels: Int) {
            controller.down(100f, 1200f)
            var x = 100
            while (x < 100 + pixels) { x += 10; controller.move(x.toFloat(), 1200f, 1) }
            controller.up()
            clock += 10
        }
    }

    @Test
    fun aTapErasesOnePatchAndUndoPutsItBack() {
        val r = Rig(p = projection())
        r.controller.down(500f, 1200f); r.controller.up()
        assertEquals(1, r.store.added.size)
        assertTrue(r.controller.canUndo)
        r.controller.undo()
        assertEquals(1, r.store.removed.size)
        assertEquals(r.store.added.map { it.eraseStroke }.distinct().single(), r.store.removed.single().first)
        assertFalse(r.controller.canUndo)
    }

    @Test
    fun aDragErasesASeriesOfSpotsOfOneStrokeWithUniqueTimes() {
        val r = Rig(p = projection())
        r.drag(200)
        assertTrue(r.store.added.size >= 4)
        assertEquals(1, r.store.added.map { it.eraseStroke }.distinct().size)
        assertEquals(r.store.added.size, r.store.added.map { it.time }.distinct().size)
        assertTrue(r.store.added.all { it.sessionId == null })
    }

    @Test
    fun zoomedOutTooFarNothingIsErasedAndMapGesturesStayOn() {
        val r = Rig(p = projection(zoom = 13.5))
        r.drag(100)
        assertTrue(r.store.added.isEmpty())
        assertTrue(r.zoomedOut >= 1)
        assertFalse(r.gestures.contains(false))
    }

    @Test
    fun theAllowanceCutsTheDragWhereItEndsAndStopsOnce() {
        val r = Rig(p = projection())
        r.controller.allowance = { 50.0 }
        r.drag(400)
        val p0 = r.store.added.first()
        val farthest = r.store.added.maxOf {
            EraserRules.distance(LatLon(p0.lat, p0.lon), LatLon(it.lat, it.lon))
        }
        assertTrue("farthest was $farthest", farthest <= 50.5)
        assertEquals(1, r.outOfAllowance)
        assertEquals(50.0, r.spent, 1.0)
    }

    @Test
    fun anAllowanceOfZeroErasesNothingAndSaysSo() {
        val r = Rig(p = projection())
        r.controller.allowance = { 0.0 }
        r.drag(100)
        assertTrue(r.store.added.isEmpty())
        assertTrue(r.outOfAllowance >= 1)
    }

    @Test
    fun aSecondFingerEndsTheStrokeHandsTheMapItsGesturesAndTheStrokeStaysUndoable() {
        val r = Rig(p = projection())
        r.controller.down(100f, 1200f)
        r.controller.move(200f, 1200f, 1)
        val before = r.store.added.size
        r.controller.secondFinger()
        r.controller.move(400f, 1200f, 2)
        assertEquals(before, r.store.added.size)
        assertEquals(true, r.gestures.last())
        r.controller.up()
        assertTrue(r.controller.canUndo)
    }

    @Test
    fun puttingTheEraserDownForgetsTheUndoListAndGivesTheMapItsGesturesBack() {
        val r = Rig(p = projection())
        r.drag(100)
        assertTrue(r.controller.canUndo)
        r.controller.active = false
        assertFalse(r.controller.canUndo)
        assertEquals(true, r.gestures.last())
        val before = r.store.added.size
        r.drag(100)
        assertEquals(before, r.store.added.size)
    }

    @Test
    fun undoOnlyTakesBackTheLastStroke() {
        val r = Rig(p = projection())
        r.drag(100)
        val firstStroke = r.store.added.map { it.eraseStroke }.distinct().single()
        r.drag(100)
        r.controller.undo()
        val (undoneId, spots) = r.store.removed.single()
        assertTrue(undoneId != firstStroke)
        assertTrue(spots.all { it.eraseStroke == undoneId })
    }
}

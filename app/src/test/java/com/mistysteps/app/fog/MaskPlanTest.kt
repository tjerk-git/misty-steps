package com.mistysteps.app.fog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskPlanTest {

    // A 100 x 200 fog-pixel view; the mask is built with 25 px / 50 px padding around it.
    private fun view(offX: Double = 10.0, offY: Double = 20.0, k: Double = 1000.0, version: Int = 1) =
        MaskView(offX, offY, k, 100, 200, version)

    private val built = MaskPlan.specFor(view())

    @Test
    fun builtMaskIsPaddedAndShiftedByThePadding() {
        assertEquals(150, built.w)
        assertEquals(300, built.h)
        assertEquals(35.0, built.offX, 1e-9)
        assertEquals(70.0, built.offY, 1e-9)
    }

    @Test
    fun unchangedViewPlacesTheMaskBehindThePadding() {
        val p = MaskPlan.place(built, view())
        assertEquals(-25.0, p.tx, 1e-9)
        assertEquals(-50.0, p.ty, 1e-9)
        assertEquals(1.0, p.scale, 1e-9)
    }

    @Test
    fun panningMovesTheStaleMaskByTheSameAmount() {
        val p = MaskPlan.place(built, view(offX = 10.0 - 12.0, offY = 20.0 + 7.0))
        assertEquals(-25.0 - 12.0, p.tx, 1e-9)
        assertEquals(-50.0 + 7.0, p.ty, 1e-9)
    }

    @Test
    fun zoomingScalesTheStaleMaskAroundTheSameMapPoint() {
        val now = view(k = 2000.0, offX = 100.0, offY = 300.0)
        val p = MaskPlan.place(built, now)
        assertEquals(2.0, p.scale, 1e-9)
        // A spot at mercator x = 0.04: mask px 0.04*1000+35 = 75; view px 0.04*2000+100 = 180.
        assertEquals(180.0, 75.0 * p.scale + p.tx, 1e-9)
        // Same for y = 0.07: mask px 140; view px 0.07*2000+300 = 440.
        assertEquals(440.0, 140.0 * p.scale + p.ty, 1e-9)
    }

    @Test
    fun noMaskYetNeedsABuild() {
        assertTrue(MaskPlan.needsRebuild(null, view()))
    }

    @Test
    fun sameViewAndVersionNeedsNoRebuild() {
        assertFalse(MaskPlan.needsRebuild(built, view()))
    }

    @Test
    fun newSpotsNeedARebuild() {
        assertTrue(MaskPlan.needsRebuild(built, view(version = 2)))
    }

    @Test
    fun smallPanKeepsTheStaleMask() {
        assertFalse(MaskPlan.needsRebuild(built, view(offX = 10.0 - 8.0)))
    }

    @Test
    fun panIntoTheEdgeOfThePaddingNeedsARebuild() {
        // 20 of 25 padding px used: less than half of the padding is left on the right.
        assertTrue(MaskPlan.needsRebuild(built, view(offX = 10.0 - 20.0)))
        assertTrue(MaskPlan.needsRebuild(built, view(offY = 20.0 + 40.0)))
    }

    @Test
    fun bigZoomChangeNeedsARebuildSmallOneDoesNot() {
        assertFalse(MaskPlan.needsRebuild(built, view(k = 1050.0, offX = 10.0 - 2.0, offY = 20.0 - 4.0)))
        assertTrue(MaskPlan.needsRebuild(built, view(k = 1500.0)))
        assertTrue(MaskPlan.needsRebuild(built, view(k = 600.0)))
    }

    @Test
    fun sizeChangeNeedsARebuild() {
        assertTrue(MaskPlan.needsRebuild(built, MaskView(10.0, 20.0, 1000.0, 120, 200, 1)))
    }

    @Test
    fun jobQueueRunsOneAtATimeAndKeepsOnlyTheLatestRequest() {
        val q = MaskJobs<String>()
        assertEquals("a", q.request("a"))        // idle: starts right away
        assertNull(q.request("b"))               // busy: waits
        assertNull(q.request("c"))               // replaces b
        assertEquals("c", q.finished())          // next job is the latest
        assertNull(q.finished())                 // nothing left: idle again
        assertEquals("d", q.request("d"))
        assertNotNull(q)
    }
}

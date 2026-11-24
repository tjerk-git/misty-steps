package com.mistysteps.app.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OffscreenPointerTest {
    private val insets = PointerInsets(16f, 190f, 16f, 300f)

    private fun place(
        tx: Float,
        ty: Float,
        following: Boolean = false,
        wasShown: Boolean = false,
        hasFix: Boolean = true,
        width: Float = 1000f,
        height: Float = 2000f,
    ) = OffscreenPointer.place(hasFix, tx, ty, width, height, following, insets, 24f, wasShown)

    @Test
    fun hiddenWhenTargetOnScreen() {
        assertNull(place(500f, 1000f))
    }

    @Test
    fun hiddenWithoutFix() {
        assertNull(place(5000f, 1000f, hasFix = false))
    }

    @Test
    fun hiddenWhileFollowing() {
        assertNull(place(5000f, 1000f, following = true))
    }

    @Test
    fun rightEdge() {
        val p = assertNotNull(place(5000f, 1000f))
        assertEquals(984f, p.x, 0.01f)
        assertEquals(1000f, p.y, 0.01f)
        assertEquals(0f, p.angleRad, 1e-5f)
    }

    @Test
    fun leftEdge() {
        val p = assertNotNull(place(-4000f, 1000f))
        assertEquals(16f, p.x, 0.01f)
        assertTrue(abs(abs(p.angleRad) - PI.toFloat()) < 1e-5f)
    }

    @Test
    fun aboveClampsToTopInset() {
        val p = assertNotNull(place(500f, -3000f))
        assertEquals(190f, p.y, 0.01f)
    }

    @Test
    fun belowClampsToBottomInset() {
        val p = assertNotNull(place(500f, 6000f))
        assertEquals(1700f, p.y, 0.01f)
    }

    @Test
    fun cornerStaysInsideSafeRect() {
        val p = assertNotNull(place(5000f, 9000f))
        assertTrue(p.x in 16f..984f, "x ${p.x}")
        assertTrue(p.y in 190f..1700f, "y ${p.y}")
    }

    @Test
    fun angleLooksAtTarget() {
        val p = assertNotNull(place(2000f, 1000f))
        assertEquals(atan2(1000f - p.y, 2000f - p.x), p.angleRad, 1e-4f)
    }

    @Test
    fun noFlickerAtEdge() {
        assertNotNull(place(1010f, 1000f, wasShown = false))
        assertNull(place(990f, 1000f, wasShown = false))
        assertNotNull(place(990f, 1000f, wasShown = true))
        assertNull(place(900f, 1000f, wasShown = true))
    }

    @Test
    fun nonFiniteTargetHidden() {
        assertNull(place(Float.NaN, 1000f))
        assertNull(place(1000f, Float.NaN))
        assertNull(place(Float.POSITIVE_INFINITY, 1000f))
        assertNull(place(1000f, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun zeroSizeHidden() {
        assertNull(place(5000f, 1000f, width = 0f))
        assertNull(place(5000f, 1000f, height = 0f))
    }

    @Test
    fun noRoomStillInsideMap() {
        val p = assertNotNull(place(5000f, 5000f, width = 300f, height = 400f))
        assertTrue(p.x in 0f..300f, "x ${p.x}")
        assertTrue(p.y in 0f..400f, "y ${p.y}")
    }

    @Test
    fun insetsScaleWithDensityAndClearTheControls() {
        assertEquals(PointerInsets(32f, 464f, 32f, 680f), OffscreenPointer.insetsFor(2f))
        assertEquals(24f * 3f, OffscreenPointer.hysteresisFor(3f), 1e-4f)
    }
}

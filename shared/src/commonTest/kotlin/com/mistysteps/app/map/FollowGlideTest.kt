package com.mistysteps.app.map

import com.mistysteps.app.data.Mercator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class FollowGlideTest {
    private val world = 256.0 * 32768.0 // zoom 15
    private val w = 1080
    private val h = 2400

    private fun glide() = FollowGlide()

    private fun x(lon: Double) = Mercator.x(lon)
    private fun y(lat: Double) = Mercator.y(lat)

    /** A target [pixels] to the east of (53.2, 5.8) at zoom 15. */
    private fun eastBy(pixels: Double) = x(5.8) + pixels / world

    @Test
    fun farAwayFliesInsteadOfSliding() {
        val step = glide().step(x(5.8), y(53.2), eastBy(3 * 2400.0), y(53.2), world, w, h, 16)
        assertTrue(step is FollowStep.Fly)
    }

    @Test
    fun aSmallOffsetGlidesTowardsTheTargetWithoutArriving() {
        val step = glide().step(x(5.8), y(53.2), eastBy(100.0), y(53.2), world, w, h, 16) as FollowStep.Glide
        assertTrue(step.x > x(5.8) && step.x < eastBy(100.0))
        assertFalse(step.settled)
    }

    @Test
    fun theLastHalfPixelLandsExactlyAndSettles() {
        val target = eastBy(0.4)
        val step = glide().step(x(5.8), y(53.2), target, y(53.2), world, w, h, 16)
        assertTrue(step is FollowStep.Hold || (step is FollowStep.Glide && step.settled && step.x == target))
    }

    @Test
    fun onceCaughtUpGpsJitterDoesNotMoveTheCamera() {
        val g = glide()
        // Catch up first.
        var step = g.step(x(5.8), y(53.2), eastBy(0.3), y(53.2), world, w, h, 16)
        assertTrue(step is FollowStep.Hold || (step is FollowStep.Glide && step.settled))
        // Then the target wobbles by 5 px, under the 8 px that starts a new glide.
        step = g.step(x(5.8), y(53.2), eastBy(5.0), y(53.2), world, w, h, 16)
        assertTrue(step is FollowStep.Hold)
    }

    @Test
    fun aMoveOfTheMapBySomeoneElseResetsTheExactPosition() {
        val g = glide()
        g.step(x(5.8), y(53.2), eastBy(100.0), y(53.2), world, w, h, 16)
        // The map was moved 50 px west by a pinch or a fly: the next step starts from the map's own centre.
        val moved = x(5.8) - 50.0 / world
        val step = g.step(moved, y(53.2), eastBy(100.0), y(53.2), world, w, h, 16) as FollowStep.Glide
        assertTrue(step.x > moved)
    }

    @Test
    fun theShortWayAroundTheDateLineIsTaken() {
        // Camera just east of the antimeridian's left edge, target just west of its right edge: move west, not across the world.
        val step = glide().step(0.001, y(10.0), 0.999, y(10.0), world, w, h, 16)
        // 0.002 of the world is about 16,000 px at zoom 15: far, so it flies; the fly target is the target itself.
        assertTrue(step is FollowStep.Fly)
        assertEquals(0.999, (step as FollowStep.Fly).x, 1e-12)
    }
}

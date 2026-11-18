package com.mistysteps.app.postcard

import com.mistysteps.app.data.TrailPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostcardViewTest {
    private fun p(lat: Double, lon: Double) = TrailPoint(lat, lon, 0L)

    @Test fun theTrailLandsInsideThePicture() {
        val points = listOf(p(53.2000, 5.7900), p(53.2040, 5.8000), p(53.2010, 5.7950))
        val view = PostcardView.fit(points, 972, 1010)
        for (pt in points) {
            assertTrue(view.x(pt.lon) in 0f..972f, "x ${view.x(pt.lon)}")
            assertTrue(view.y(pt.lat) in 0f..1010f, "y ${view.y(pt.lat)}")
        }
    }

    @Test fun theCentreOfTheTrailIsTheCentreOfThePicture() {
        val view = PostcardView.fit(listOf(p(53.20, 5.79), p(53.21, 5.81)), 972, 1010)
        val lat = 53.205
        val lon = 5.80
        assertEquals(486f, view.x(lon), 3f)
        assertEquals(505f, view.y(lat), 3f)
    }

    @Test fun aTinyWalkStaysAtLeastFourHundredMetresAcross() {
        val view = PostcardView.fit(listOf(p(53.2, 5.79)), 972, 1010)
        // 400 m at this latitude is about 0.0000099 of the world; at least that fits across the usable width.
        assertTrue(view.zoom < 17.6)
        assertTrue(view.zoom > 3.0)
    }

    @Test fun anEmptyTrailDoesNotCrash() {
        val view = PostcardView.fit(emptyList(), 972, 1010)
        assertEquals(486f, view.x(0.0), 3f) // longitude 0 is the world's centre
    }

    @Test fun zoomIsClampedToWhereTilesExist() {
        val far = PostcardView.fit(listOf(p(53.0, 5.0), p(-30.0, 150.0)), 972, 1010)
        assertTrue(far.zoom >= 3.0 - 1e-9)
    }
}

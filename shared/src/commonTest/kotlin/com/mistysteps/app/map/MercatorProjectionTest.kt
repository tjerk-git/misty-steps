package com.mistysteps.app.map

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.math.cos
import kotlin.math.hypot

class MercatorProjectionTest {
    private fun at(zoom: Double, lat: Double = 53.2, lon: Double = 5.8) =
        MercatorProjection(width = 1080, height = 2400, centerLat = lat, centerLon = lon, zoom = zoom)

    @Test
    fun theCentreIsTheMiddleOfTheView() {
        val p = at(15.0).toScreen(53.2, 5.8)
        assertEquals(540f, p.x, 0.01f)
        assertEquals(1200f, p.y, 0.01f)
    }

    @Test
    fun toScreenAndFromScreenAreInverses() {
        for (zoom in listOf(3.0, 15.0, 18.0)) {
            val proj = at(zoom)
            val point = proj.fromScreen(300f, 900f)
            val back = proj.toScreen(point.lat, point.lon)
            assertEquals(300f, back.x, 0.05f, "x at zoom $zoom")
            assertEquals(900f, back.y, 0.05f, "y at zoom $zoom")
        }
    }

    @Test
    fun northIsUpAndEastIsRight() {
        val proj = at(15.0)
        assertEquals(true, proj.toScreen(53.21, 5.8).y < 1200f)
        assertEquals(true, proj.toScreen(53.2, 5.81).x > 540f)
    }

    @Test
    fun oneZoomLevelUpDoublesDistancesFromTheCentre() {
        val near = at(15.0).toScreen(53.21, 5.81)
        val far = at(16.0).toScreen(53.21, 5.81)
        assertEquals(2.0, hypot(far.x - 540.0, far.y - 1200.0) / hypot(near.x - 540.0, near.y - 1200.0), 1e-3)
    }

    @Test
    fun worldSizeDoublesWithEachZoomLevel() {
        assertEquals(2.0, at(16.0).worldSizePx / at(15.0).worldSizePx, 1e-9)
        assertEquals(256.0 * 32768.0, at(15.0).worldSizePx, 1e-6)
    }

    @Test
    fun metersToPixelsMatchesTheWebMercatorScale() {
        val lat = 53.2
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(lat)) / 32768.0
        assertEquals((50.0 / metersPerPixel).toFloat(), at(15.0).metersToPixels(50.0, lat), 0.05f)
    }
}

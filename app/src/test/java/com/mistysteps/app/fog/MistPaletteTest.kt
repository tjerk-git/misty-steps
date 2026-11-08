package com.mistysteps.app.fog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MistPaletteTest {
    private val lat = 53.2
    private val lon = 5.8

    private fun elevation(utc: String) = MistPalette.sunElevation(Instant.parse(utc).toEpochMilli(), lat, lon)

    @Test
    fun sunIsHighAtMidsummerNoonInLeeuwarden() {
        // Solar noon is ~11:37 UTC; max height = 90 - 53.2 + 23.44 ≈ 60.2°.
        assertEquals(60.2, elevation("2026-06-21T11:37:00Z"), 1.0)
    }

    @Test
    fun sunIsBelowTheHorizonAtMidwinterMidnight() {
        // Lowest point = -(90 - 53.2 + 23.44) ≈ -60.2°.
        assertEquals(-60.2, elevation("2026-12-21T23:37:00Z"), 1.5)
    }

    @Test
    fun sunCrossesTheHorizonAroundSunrise() {
        // Sunrise in Leeuwarden on 8 November is just before 08:00 local (07:00 UTC).
        val before = elevation("2026-11-08T06:30:00Z")
        val after = elevation("2026-11-08T07:30:00Z")
        assertTrue("before was $before", before < -1.0)
        assertTrue("after was $after", after > 1.0)
    }

    @Test
    fun paletteEndsAreDayAndNight() {
        val day = MistPalette.forElevation(40.0)
        assertEquals(0xECF4F5FA.toInt(), day.base)
        assertEquals(0xFFFFFFFF.toInt(), day.shadowTint)
        val night = MistPalette.forElevation(-30.0)
        assertEquals(0xECC4CBE0.toInt(), night.base)
        val dusk = MistPalette.forElevation(-3.5)
        assertEquals(236, dusk.base ushr 24)
    }
}

package com.mistysteps.app.maps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadMeterTest {
    @Test fun unknownUntilTwoSamples() {
        val m = DownloadMeter()
        assertNull(m.secondsLeft(0, 10_000_000))
        m.update(0, 0)
        assertNull(m.secondsLeft(0, 10_000_000))
        assertEquals(0, m.bytesPerSecond)
    }

    @Test fun steadySpeedGivesTimeLeft() {
        val m = DownloadMeter()
        m.update(0, 0)
        m.update(1000, 2_000_000)
        assertEquals(2_000_000, m.bytesPerSecond)
        // 8 MB left at 2 MB/s: 4 s, rounded up to 5 so it never says 0 s while still going
        assertEquals(5, m.secondsLeft(2_000_000, 10_000_000))
    }

    @Test fun smoothsAJump() {
        val m = DownloadMeter()
        m.update(0, 0)
        m.update(1000, 1_000_000)
        m.update(2000, 6_000_000) // a burst of 5 MB/s
        assertTrue(m.bytesPerSecond in 2_000_000..3_000_000)
    }

    @Test fun tooSoonSamplesAreSkipped() {
        val m = DownloadMeter()
        m.update(0, 0)
        m.update(100, 500_000)
        assertEquals(0, m.bytesPerSecond)
        m.update(1000, 1_000_000)
        assertEquals(1_000_000, m.bytesPerSecond)
    }

    @Test fun restartResets() {
        val m = DownloadMeter()
        m.update(0, 0)
        m.update(1000, 1_000_000)
        m.update(2000, 100)
        assertEquals(0, m.bytesPerSecond)
    }

    @Test fun stalledDownloadKeepsLastSpeed() {
        val m = DownloadMeter()
        m.update(0, 0)
        m.update(1000, 1_000_000)
        m.update(5000, 1_000_000)
        assertEquals(1_000_000, m.bytesPerSecond)
    }
}

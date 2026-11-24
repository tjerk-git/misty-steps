package com.mistysteps.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {
    @Test
    fun percentKeepsTwoSignificantDigits() {
        Locale.setDefault(Locale.US)
        assertEquals("0%", formatPercent(0.0))
        assertEquals("42%", formatPercent(0.42))
        assertEquals("4.8%", formatPercent(0.048))
        assertEquals("0.48%", formatPercent(0.0048))
        assertEquals("0.0000031%", formatPercent(3.1e-8))
    }

    @Test
    fun durationReadsNaturally() {
        Locale.setDefault(Locale.US)
        assertEquals("under a minute", formatDuration(30_000, DurationText.EN))
        assertEquals("12 min", formatDuration(12 * 60_000L + 5_000, DurationText.EN))
        assertEquals("1 h 05 min", formatDuration(65 * 60_000L, DurationText.EN))
    }

    @Test
    fun oneInRoundsToTwoSignificantDigits() {
        Locale.setDefault(Locale.US)
        assertEquals("54,000,000", formatOneIn(1.85e-8))
        assertEquals("1,200", formatOneIn(8.3e-4))
    }

    @Test
    fun nothingClearedIsZeroPercentNotOneInNaN() {
        Locale.setDefault(Locale.US)
        assertEquals("0%", formatShare(0.0) { "1 in $it" })
        assertEquals("1 in 54,000,000", formatShare(1.85e-8) { "1 in $it" })
        assertEquals("12%", formatShare(0.12) { "1 in $it" })
    }
}

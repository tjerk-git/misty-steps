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
        assertEquals("under a minute", formatDuration(30_000))
        assertEquals("12 min", formatDuration(12 * 60_000L + 5_000))
        assertEquals("1 h 05 min", formatDuration(65 * 60_000L))
    }
}

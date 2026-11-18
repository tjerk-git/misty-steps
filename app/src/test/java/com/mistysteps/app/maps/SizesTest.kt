package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SizesTest {
    @Test
    fun megabytesAreWholeNumbers() {
        Locale.setDefault(Locale.US)
        assertEquals("62 MB", formatSize(62_000_000))
        assertEquals("26 MB", formatSize(25_707_476))
    }

    @Test
    fun gigabytesHaveOneDecimal() {
        Locale.setDefault(Locale.US)
        assertEquals("1.2 GB", formatSize(1_200_000_000))
    }

    @Test
    fun dutchUsesAComma() {
        Locale.setDefault(Locale("nl", "NL"))
        assertEquals("1,2 GB", formatSize(1_200_000_000))
    }

    @Test
    fun zeroIsZeroMegabytes() {
        Locale.setDefault(Locale.US)
        assertEquals("0 MB", formatSize(0))
    }
}

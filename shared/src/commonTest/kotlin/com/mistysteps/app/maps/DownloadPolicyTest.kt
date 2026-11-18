package com.mistysteps.app.maps

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class DownloadPolicyTest {
    private val mb = 1_000_000L

    @Test
    fun bigDownloadOnMobileDataNeedsAWarning() {
        assertTrue(DownloadPolicy.needsDataWarning(metered = true, bytes = 62 * mb))
    }

    @Test
    fun smallDownloadOnMobileDataDoesNot() {
        assertFalse(DownloadPolicy.needsDataWarning(metered = true, bytes = 49 * mb))
    }

    @Test
    fun wifiNeverNeedsAWarning() {
        assertFalse(DownloadPolicy.needsDataWarning(metered = false, bytes = 500 * mb))
    }

    @Test
    fun exactlyFiftyMegabytesIsWarned() {
        assertTrue(DownloadPolicy.needsDataWarning(metered = true, bytes = 50 * mb))
    }

    @Test
    fun spaceNeedsTwentyPercentExtra() {
        assertTrue(DownloadPolicy.hasSpaceFor(bytes = 100, freeBytes = 120))
        assertFalse(DownloadPolicy.hasSpaceFor(bytes = 100, freeBytes = 119))
    }
}

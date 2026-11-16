package com.mistysteps.app.maps

import com.mistysteps.app.stats.Boundary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferRulesTest {
    private val fr = Province("fr", "Friesland", Boundary("Friesland", emptyList()))

    @Test
    fun noKnownProvinceMeansNoOffer() {
        assertFalse(OfferRules.shouldOffer(null, PackState.NotDownloaded, emptySet()))
    }

    @Test
    fun notDownloadedProvinceIsOffered() {
        assertTrue(OfferRules.shouldOffer(fr, PackState.NotDownloaded, emptySet()))
    }

    @Test
    fun installedOrBusyProvincesAreNotOffered() {
        assertFalse(OfferRules.shouldOffer(fr, PackState.Ready(2026), emptySet()))
        assertFalse(OfferRules.shouldOffer(fr, PackState.Downloading(1, 2), emptySet()))
        assertFalse(OfferRules.shouldOffer(fr, PackState.UpdateAvailable(2026, 2027), emptySet()))
    }

    @Test
    fun dismissedProvinceIsNotOfferedAgain() {
        assertFalse(OfferRules.shouldOffer(fr, PackState.NotDownloaded, setOf("fr")))
        assertTrue(OfferRules.shouldOffer(fr, PackState.NotDownloaded, setOf("zh")))
    }

    @Test
    fun missingStateMeansNoOffer() {
        assertFalse(OfferRules.shouldOffer(fr, null, emptySet()))
    }
}

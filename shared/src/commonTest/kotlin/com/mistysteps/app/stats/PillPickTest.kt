package com.mistysteps.app.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PillPickTest {
    private fun area(kind: AreaKind, name: String?, cleared: Double, total: Double?) = AreaStat(kind, name, cleared, total)
    private val world = area(AreaKind.WORLD, null, 100.0, 5.1e14)
    private fun stats(city: AreaStat?, country: AreaStat?) = Stats(100.0, 0.0, city, country, world, null)

    private val leeuwarden = area(AreaKind.CITY, "Leeuwarden", 39_000.0, 58_660_000.0)
    private val netherlands = area(AreaKind.COUNTRY, "Netherlands", 39_000.0, 41_389_000_000.0)

    @Test
    fun theCityWinsWhenItsShareIsKnown() {
        assertEquals(leeuwarden, PillPick.place(stats(leeuwarden, netherlands)))
    }

    @Test
    fun aCityWithoutATotalFallsBackToTheCountry() {
        assertEquals(netherlands, PillPick.place(stats(leeuwarden.copy(totalM2 = null), netherlands)))
    }

    @Test
    fun aCityWithAZeroTotalFallsBackToTheCountry() {
        assertEquals(netherlands, PillPick.place(stats(leeuwarden.copy(totalM2 = 0.0), netherlands)))
    }

    @Test
    fun neitherCityNorCountryMeansNothing() {
        assertNull(PillPick.place(stats(null, null)))
        assertNull(PillPick.place(stats(leeuwarden.copy(totalM2 = null), netherlands.copy(totalM2 = null))))
    }

    @Test
    fun aCityWithNothingClearedStillCounts() {
        val empty = leeuwarden.copy(clearedM2 = 0.0)
        assertEquals(empty, PillPick.place(stats(empty, netherlands)))
    }
}

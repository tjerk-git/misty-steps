package com.mistysteps.app.maps

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsFetchRulesTest {
    private val sha = "a".repeat(64)
    private fun entry(id: String, year: Int = 2026, stats: Boolean = true) = PackEntry(
        id, id.uppercase(), year, listOf(0.0, 0.0, 1.0, 1.0),
        FileRef("https://x/$id/map", 1000, sha), if (stats) FileRef("https://x/$id/stats", 150, sha) else null,
    )

    @Test
    fun totalBytesIsTheMapPlusItsStatsFile() {
        assertEquals(1150L, entry("fr").totalBytes)
        assertEquals(1000L, entry("fr", stats = false).totalBytes)
    }

    @Test
    fun anUpToDateInstalledPackWithoutStatsNeedsThem() {
        val got = statsToFetch(listOf(entry("fr")), installedYear = { 2026 }, hasStats = { false })
        assertEquals(listOf("fr"), got.map { it.id })
    }

    @Test
    fun packsThatAlreadyHaveStatsOrAreNotInstalledOrAreOutOfDateNeedNothing() {
        val entries = listOf(entry("a"), entry("b"), entry("c", year = 2027))
        val got = statsToFetch(
            entries,
            installedYear = { id -> when (id) { "a" -> 2026; "c" -> 2026; else -> null } }, // b is not installed; c's map is older than the catalog
            hasStats = { id -> id == "a" },
        )
        assertEquals(emptyList(), got)
    }

    @Test
    fun anEntryWithoutAStatsFileIsLeftAlone() {
        assertEquals(emptyList(), statsToFetch(listOf(entry("fr", stats = false)), { 2026 }, { false }))
    }
}

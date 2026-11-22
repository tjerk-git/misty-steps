package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PickerStateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sha = "a".repeat(64)
    private fun entry(id: String, name: String, bytes: Long) =
        PackEntry(id, name, 2026, listOf(0.0, 0.0, 1.0, 1.0), FileRef("https://x/$id", bytes, sha), null)

    private val options = listOf(entry("zh", "Zuid-Holland", 106), entry("dr", "Drenthe", 41), entry("fr", "Fryslân", 58))

    @Test
    fun optionsAreSortedByName() {
        assertEquals(listOf("dr", "fr", "zh"), PickerState(options).options.map { it.id })
    }

    @Test
    fun nothingIsSelectedByDefault() {
        val state = PickerState(options)
        assertTrue(state.selected.isEmpty())
        assertEquals(0L, state.totalBytes)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val on = PickerState(options).toggle("fr")
        assertEquals(setOf("fr"), on.selected)
        assertTrue(on.toggle("fr").selected.isEmpty())
    }

    @Test
    fun totalSumsTheSelected() {
        assertEquals(41L + 106L, PickerState(options).toggle("dr").toggle("zh").totalBytes)
    }

    @Test
    fun preselectedIdsUnknownToTheCatalogAreDropped() {
        assertEquals(setOf("fr"), PickerState(options, preselected = setOf("fr", "xx")).selected)
    }

    @Test
    fun toDownloadSkipsWhatIsInstalledOrDownloading() {
        val packs = ProvincePacks(tmp.root)
        File(tmp.root, "2026/fr/map.pmtiles").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(1)) }
        packs.markDownloading("dr", 5, 41)
        val state = PickerState(options).toggle("fr").toggle("dr").toggle("zh")
        assertEquals(listOf("zh"), state.toDownload(packs::state).map { it.id })
    }

    @Test
    fun toDownloadRetriesAFailedProvince() {
        val packs = ProvincePacks(tmp.root)
        packs.markFailed("fr", Reason.NETWORK)
        assertEquals(listOf("fr"), PickerState(options).toggle("fr").toDownload(packs::state).map { it.id })
    }

    @Test
    fun theTotalCountsTheStatsFileOfEachTickedProvince() {
        val withStats = listOf(
            PackEntry("dr", "Drenthe", 2026, listOf(0.0, 0.0, 1.0, 1.0), FileRef("https://x/dr", 41, sha), FileRef("https://x/dr-s", 6, sha)),
            PackEntry("fr", "Fryslân", 2026, listOf(0.0, 0.0, 1.0, 1.0), FileRef("https://x/fr", 58, sha), null),
        )
        val state = PickerState(withStats, setOf("dr", "fr"))
        assertEquals(41L + 6L + 58L, state.totalBytes)
    }
}

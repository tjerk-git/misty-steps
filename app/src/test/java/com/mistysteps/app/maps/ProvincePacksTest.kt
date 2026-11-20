package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProvincePacksTest {
    @get:Rule val tmp = TemporaryFolder()

    private val packs by lazy { ProvincePacks(tmp.root) }
    private val sha = "a".repeat(64)

    private fun entry(year: Int = 2026, id: String = "fr") =
        PackEntry(id, "Friesland", year, listOf(0.0, 0.0, 1.0, 1.0), FileRef("https://x/$id.pmtiles", 1000, sha), null)

    private fun install(year: Int, id: String = "fr") {
        val f = File(tmp.root, "$year/$id/map.pmtiles")
        f.parentFile!!.mkdirs()
        f.writeBytes(ByteArray(10))
    }

    @Test
    fun nothingInstalledIsNotDownloaded() {
        assertTrue(packs.state(entry()) is PackState.NotDownloaded)
        assertNull(packs.installedYear("fr"))
        assertNull(packs.mapFile("fr"))
    }

    @Test
    fun installedPackIsReady() {
        install(2026)
        assertEquals(2026, (packs.state(entry()) as PackState.Ready).year)
        assertTrue(packs.mapFile("fr")!!.path.endsWith("2026/fr/map.pmtiles"))
    }

    @Test
    fun newerCatalogYearOffersAnUpdate() {
        install(2026)
        val state = packs.state(entry(year = 2027)) as PackState.UpdateAvailable
        assertEquals(2026, state.installedYear)
        assertEquals(2027, state.newYear)
    }

    @Test
    fun folderWithoutTheMapFileDoesNotCount() {
        File(tmp.root, "2026/fr").mkdirs()
        assertTrue(packs.state(entry()) is PackState.NotDownloaded)
    }

    @Test
    fun markDownloadingShowsProgressAndBumpsVersion() {
        val before = packs.version.value
        packs.markDownloading("fr", 400, 1000)
        val state = packs.state(entry()) as PackState.Downloading
        assertEquals(400L, state.bytes)
        assertEquals(1000L, state.total)
        assertTrue(packs.version.value > before)
    }

    @Test
    fun markFailedShowsTheReasonAndClearTransientRestoresTheState() {
        packs.markFailed("fr", Reason.NO_SPACE)
        assertEquals(Reason.NO_SPACE, (packs.state(entry()) as PackState.Failed).reason)
        packs.clearTransient("fr")
        assertTrue(packs.state(entry()) is PackState.NotDownloaded)
    }

    @Test
    fun removeDeletesEveryYearAndBumpsVersion() {
        install(2026)
        install(2027)
        val before = packs.version.value
        packs.remove("fr")
        assertFalse(File(tmp.root, "2026/fr").exists())
        assertFalse(File(tmp.root, "2027/fr").exists())
        assertNull(packs.installedYear("fr"))
        assertTrue(packs.version.value > before)
    }

    @Test
    fun removeOlderThanKeepsTheOldPackUntilTheNewOneExists() {
        install(2026)
        packs.removeOlderThan(2027, "fr")
        assertTrue(File(tmp.root, "2026/fr/map.pmtiles").exists())
        install(2027)
        packs.removeOlderThan(2027, "fr")
        assertFalse(File(tmp.root, "2026/fr").exists())
        assertTrue(File(tmp.root, "2027/fr/map.pmtiles").exists())
    }

    @Test
    fun otherProvincesAreLeftAlone() {
        install(2026, "fr")
        install(2026, "zh")
        packs.remove("fr")
        assertEquals(2026, packs.installedYear("zh"))
    }

    @Test
    fun mapFilesListsTheNewestInstalledPackOfEachProvinceSortedById() {
        install(2026, "zh")
        install(2026, "fr")
        install(2027, "fr")
        File(tmp.root, "2027/gr").mkdirs() // no map file inside: does not count
        assertEquals(
            listOf("fr", "zh"),
            packs.mapFiles().map { it.parentFile!!.name },
        )
        assertTrue(packs.mapFiles()[0].path.contains("2027/fr/"))
    }

    @Test
    fun mapFilesIsEmptyWithNothingInstalled() {
        assertTrue(packs.mapFiles().isEmpty())
    }

    // The map reloads its whole style when this changes, so download progress must not move it.
    @Test
    fun downloadProgressDoesNotChangeInstalledVersion() {
        val before = packs.installedVersion.value
        repeat(20) { packs.markDownloading("fr", it * 10L, 1000) }
        packs.markFailed("fr", Reason.NETWORK)
        assertEquals(before, packs.installedVersion.value)
        assertTrue(packs.version.value >= 21)
    }

    @Test
    fun installedVersionMovesWhenPacksComeAndGo() {
        val v0 = packs.installedVersion.value
        install(2026)
        packs.clearTransient("fr")
        val v1 = packs.installedVersion.value
        assertTrue(v1 > v0)
        packs.remove("fr")
        assertTrue(packs.installedVersion.value > v1)
    }
}

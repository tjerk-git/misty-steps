package com.mistysteps.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipStatsPackTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun goodZip(): File = File(tmp.root, "stats.zip").also {
        writeZip(it, mapOf(
            "index.json" to indexJson(listOf(7L)),
            "hoods/7.json" to hoodsJson(Triple("relation", 10L, "Binnenstad")),
            "streets/r10.json" to streetsJson("Hoofdstraat"),
        ))
    }

    @Test
    fun readsTheIndexTheHoodsAndTheStreetsOfOneEntryAtATime() {
        val pack = ZipStatsPack(goodZip())
        assertEquals(setOf(7L), pack.index?.cities)
        assertEquals("Binnenstad", pack.hoods(7)?.single()?.name)
        assertEquals("Hoofdstraat", pack.streets("relation", 10)?.single()?.name)
    }

    @Test
    fun anEntryThatIsNotThereIsNull() {
        val pack = ZipStatsPack(goodZip())
        assertNull(pack.hoods(8))
        assertNull(pack.streets("way", 999))
    }

    @Test
    fun aFileThatIsNotAZipGivesNothingAndDoesNotCrash() {
        val junk = File(tmp.root, "junk.zip").apply { writeText("this is not a zip") }
        val pack = ZipStatsPack(junk)
        assertNull(pack.index)
        assertNull(pack.hoods(7))
        assertNull(pack.streets("relation", 10))
    }

    @Test
    fun aTruncatedZipGivesNothingAndDoesNotCrash() {
        val whole = goodZip().readBytes()
        val cut = File(tmp.root, "cut.zip").apply { writeBytes(whole.copyOf(whole.size / 2)) }
        val pack = ZipStatsPack(cut)
        assertNull(pack.index)
        assertNull(pack.hoods(7))
    }

    @Test
    fun aMissingFileGivesNothing() {
        val pack = ZipStatsPack(File(tmp.root, "nope.zip"))
        assertNull(pack.index)
        assertNull(pack.hoods(7))
    }

    @Test
    fun aNewerFormatIsIgnored() {
        val f = File(tmp.root, "v2.zip")
        writeZip(f, mapOf("index.json" to indexJson(listOf(7L), format = 2), "hoods/7.json" to hoodsJson(Triple("relation", 10L, "X"))))
        val pack = ZipStatsPack(f)
        assertNull(pack.index)
        assertNull(pack.hoods(7)) // without a readable index the file counts for nothing
    }
}

package com.mistysteps.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StatsPacksTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>) = File(tmp.root, name).also { writeZip(it, entries) }

    @Test
    fun theHoodsOfATownComeFromThePackThatListsIt() {
        val fr = zip("fr.zip", mapOf("index.json" to indexJson(listOf(7L)), "hoods/7.json" to hoodsJson(Triple("relation", 10L, "Binnenstad"))))
        val gr = zip("gr.zip", mapOf("index.json" to indexJson(listOf(8L)), "hoods/8.json" to hoodsJson(Triple("way", 20L, "Oosterpark"))))
        val packs = StatsPacks { listOf(fr, gr) }
        assertEquals("Binnenstad", packs.hoodsFor(7)?.single()?.name)
        assertEquals("Oosterpark", packs.hoodsFor(8)?.single()?.name)
        assertNull(packs.hoodsFor(99))
    }

    @Test
    fun theStreetsOfAHoodComeFromTheFirstPackThatHasThem() {
        val fr = zip("fr.zip", mapOf("index.json" to indexJson(listOf(7L)), "streets/r10.json" to streetsJson("Hoofdstraat")))
        val gr = zip("gr.zip", mapOf("index.json" to indexJson(listOf(8L)), "streets/r10.json" to streetsJson("Dubbel"), "streets/w20.json" to streetsJson("Parklaan")))
        val packs = StatsPacks { listOf(fr, gr) }
        assertEquals(listOf("Hoofdstraat"), packs.streetsFor("relation", 10)?.map { it.name })   // not both: no duplicates
        assertEquals(listOf("Parklaan"), packs.streetsFor("way", 20)?.map { it.name })
        assertNull(packs.streetsFor("way", 5))
    }

    @Test
    fun aHoodWhoseStreetsEntryIsMissingStillHasItsHoods() {
        val fr = zip("fr.zip", mapOf("index.json" to indexJson(listOf(7L)), "hoods/7.json" to hoodsJson(Triple("relation", 10L, "Binnenstad"))))
        val packs = StatsPacks { listOf(fr) }
        assertNotNull(packs.hoodsFor(7))
        assertNull(packs.streetsFor("relation", 10))
    }

    @Test
    fun damagedAndNewerPacksAreSkippedNotFatal() {
        val junk = File(tmp.root, "junk.zip").apply { writeText("nope") }
        val newer = zip("v2.zip", mapOf("index.json" to indexJson(listOf(7L), format = 2), "hoods/7.json" to hoodsJson(Triple("relation", 1L, "Nieuw"))))
        val good = zip("fr.zip", mapOf("index.json" to indexJson(listOf(7L)), "hoods/7.json" to hoodsJson(Triple("relation", 10L, "Binnenstad"))))
        val packs = StatsPacks { listOf(junk, newer, File(tmp.root, "gone.zip"), good) }
        assertEquals("Binnenstad", packs.hoodsFor(7)?.single()?.name)
    }

    @Test
    fun withNoPacksEverythingIsNull() {
        val packs = StatsPacks { emptyList() }
        assertNull(packs.hoodsFor(7))
        assertNull(packs.streetsFor("relation", 10))
    }
}

package com.mistysteps.app.pin

import com.mistysteps.app.data.PointIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {

    private fun pin(
        time: Long = 0,
        lat: Double = 53.2,
        lon: Double = 5.8,
        labelKey: String = "sit",
        note: String? = null,
    ) = Pin(id = 0, lat = lat, lon = lon, time = time, labelKey = labelKey, note = note, sessionId = null)

    @Test
    fun unknownKeyReadsAsSomethingElseButKeepsItsKey() {
        assertEquals(PinLabel.CROW, PinLabel.of("crow"))
        assertEquals(PinLabel.OTHER, PinLabel.of("owl"))
        assertEquals(PinLabel.OTHER, pin(labelKey = "owl").label)
        assertEquals("owl", pin(labelKey = "owl").labelKey)
        assertEquals(13, PinLabel.entries.size)
        assertEquals(
            listOf("sit", "view", "shelter", "quiet", "smell", "birdsong", "crow", "cat", "ducks", "tree", "building", "path", "other"),
            PinLabel.entries.map { it.key },
        )
    }

    @Test
    fun notesAreTrimmedCutAndNeverBlank() {
        assertNull(PinRules.cleanNote(null))
        assertNull(PinRules.cleanNote("   \n  "))
        assertEquals("red door", PinRules.cleanNote("  red door \n"))
        assertEquals("by the red door", PinRules.cleanNote("by the\nred door"))
        assertEquals(80, PinRules.cleanNote("a".repeat(100))!!.length)
    }

    @Test
    fun cuttingANoteNeverSplitsACharacter() {
        val note = "a".repeat(79) + "𝄞" + "b" // U+1D11E straddles the 80 limit
        val cut = PinRules.cleanNote(note)!!
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertEquals("a".repeat(79), cut)
    }

    @Test
    fun somethingElseNeedsANote() {
        assertTrue(PinRules.canPlace(PinLabel.SIT, null))
        assertFalse(PinRules.canPlace(PinLabel.OTHER, null))
        assertFalse(PinRules.canPlace(PinLabel.OTHER, PinRules.cleanNote("   ")))
        assertTrue(PinRules.canPlace(PinLabel.OTHER, "a bell rings at noon"))
    }

    @Test
    fun editingWithoutTouchingTheLabelKeepsAnUnknownKey() {
        assertEquals("owl", PinRules.keyAfterEdit("owl", null))
        assertEquals("cat", PinRules.keyAfterEdit("owl", PinLabel.CAT))
    }

    @Test
    fun clearedMeansInsideAClearedCircle() {
        val index = PointIndex().apply { add(53.2000, 5.8000, 50.0) }
        assertTrue(PinRules.isCleared(index, 53.2000, 5.8003)) // ~20 m east
        assertFalse(PinRules.isCleared(index, 53.2000, 5.8010)) // ~67 m east
        assertFalse(PinRules.isCleared(PointIndex(), 53.2, 5.8))
    }

    @Test
    fun zoomedOutPinsBecomeDotsThenDisappear() {
        assertEquals(PinMode.ICON, PinRules.mode(14.01))
        assertEquals(PinMode.DOT, PinRules.mode(14.0))
        assertEquals(PinMode.DOT, PinRules.mode(10.0))
        assertEquals(PinMode.HIDDEN, PinRules.mode(9.99))
    }

    @Test
    fun aFixOlderThan30sIsStale() {
        assertTrue(PinRules.isFresh(30_000))
        assertFalse(PinRules.isFresh(30_001))
    }

    @Test
    fun tapPicksTheClosestPinOrNothing() {
        val pos = mapOf("a" to (100f to 100f), "b" to (110f to 100f), "far" to (500f to 500f))
        assertEquals("b", PinRules.pickPin(pos.keys.toList(), 108f, 100f, 60f) { pos[it] })
        assertNull(PinRules.pickPin(pos.keys.toList(), 300f, 300f, 60f) { pos[it] })
        assertNull(PinRules.pickPin(listOf("off"), 0f, 0f, 60f) { null }) // off screen
    }

    @Test
    fun importSkipsPinsWithSameTimeAndPlace() {
        val have = listOf(pin(time = 1, lat = 53.2, lon = 5.8))
        val incoming = listOf(pin(time = 1, lat = 53.2, lon = 5.8, note = "dup"), pin(time = 2, lat = 53.2, lon = 5.8))
        assertEquals(listOf(2L), PinRules.newPins(have, incoming).map { it.time })
        assertEquals(2, PinRules.newPins(emptyList(), incoming + incoming).size) // duplicates inside one file collapse too
    }

    @Test
    fun walkBadgeShowsFourAndCountsTheRest() {
        val six = (1..6).map { pin(time = it.toLong(), labelKey = if (it % 2 == 0) "cat" else "sit") }
        val (shown, more) = PinRules.walkBadge(six)
        assertEquals(listOf(PinLabel.SIT, PinLabel.CAT, PinLabel.SIT, PinLabel.CAT), shown)
        assertEquals(2, more)
        assertEquals(emptyList<PinLabel>() to 0, PinRules.walkBadge(emptyList()))
    }

    @Test
    fun placesAreGroupedInLabelOrderNewestFirst() {
        val pins = listOf(
            pin(time = 1, labelKey = "crow"),
            pin(time = 3, labelKey = "sit"),
            pin(time = 2, labelKey = "sit"),
            pin(time = 4, labelKey = "owl"),
        )
        val groups = PinRules.groupByLabel(pins)
        assertEquals(listOf(PinLabel.SIT, PinLabel.CROW, PinLabel.OTHER), groups.map { it.first })
        assertEquals(listOf(3L, 2L), groups[0].second.map { it.time })
    }
}

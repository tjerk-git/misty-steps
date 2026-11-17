package com.mistysteps.app.character

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class CharacterNamesTest {
    @Test
    fun aNameIsTrimmed() {
        assertEquals("Mika", CharacterNames.clean("  Mika  "))
    }

    @Test
    fun spacesInsideAreCollapsed() {
        assertEquals("Mika de Kat", CharacterNames.clean("Mika   de \t Kat"))
    }

    @Test
    fun anEmptyOrBlankNameMeansNoName() {
        assertNull(CharacterNames.clean(""))
        assertNull(CharacterNames.clean("   "))
        assertNull(CharacterNames.clean(null))
    }

    @Test
    fun aNameIsCutAtTwentyCharacters() {
        assertEquals("a".repeat(20), CharacterNames.clean("a".repeat(30)))
        assertEquals("a".repeat(20), CharacterNames.clean("a".repeat(20)))
    }

    @Test
    fun cuttingNeverSplitsACharacterThatTakesTwoUnits() {
        // 19 letters plus a character outside the basic plane (two UTF-16 units): the cut keeps whole characters.
        val name = "a".repeat(19) + "🐱" + "b"
        val cleaned = CharacterNames.clean(name)!!
        assertEquals(20, cleaned.codePointCount(0, cleaned.length))
        assertEquals("a".repeat(19) + "🐱", cleaned)
    }

    @Test
    fun controlCharactersAreDropped() {
        assertEquals("Mika", CharacterNames.clean("Mi\u0000ka\n"))
    }

    @Test
    fun aNameThatIsOnlyControlCharactersMeansNoName() {
        assertNull(CharacterNames.clean("\u0000\u0007"))
    }

    @Test
    fun dutchAndOtherLettersAreKept() {
        assertEquals("Fryslân Ĳsbeer", CharacterNames.clean("Fryslân Ĳsbeer"))
    }
}

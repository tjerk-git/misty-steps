package com.mistysteps.app.character

import com.mistysteps.app.postcard.WalkSteps
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both apps parse the shared sprite sheet at launch and crash on a malformed entry, so the real file is checked here.
 * Paths are relative to the :shared module directory, where the tests run.
 */
class CharactersFileTest {
    @Test
    fun theSpriteSheetParsesAndEveryIdIsUnique() {
        val all = Characters.parse(File("../app/src/main/assets/characters.txt").readLines())
        assertTrue(all.isNotEmpty())
        assertEquals(all.size, all.map { it.id }.toSet().size, "duplicate character id")
    }

    @Test
    fun theSantaRatIsInTheSheet() {
        val all = Characters.parse(File("../app/src/main/assets/characters.txt").readLines())
        assertTrue(all.any { it.id == "rat" }, "rat missing from characters.txt")
    }

    @Test
    fun theSantaRatTextsExistInEnglishAndDutch() {
        val keys = listOf("character_rat_name", "character_rat_blurb", "rat_card_title", "rat_card_body", "rat_card_try", "rat_card_later")
        for (dir in listOf("values", "values-nl")) {
            val xml = File("strings/$dir/strings.xml").readText()
            for (key in keys) assertTrue(xml.contains("name=\"$key\""), "$dir/strings.xml is missing $key")
        }
    }

    @Test
    fun everyCharacterHasItsOwnStrideLine() {
        val lines = File("../app/src/main/assets/characters.txt").readLines().map { it.trim() }
        val sections = lines.count { it.startsWith("[") && it.endsWith("]") }
        val strides = lines.count { it.startsWith("stride") }
        assertEquals(sections, strides, "every character needs a 'stride = <metres>' line")
        for (c in Characters.parse(lines)) assertTrue(c.strideM in 0.02..2.0, "${c.id} stride ${c.strideM}")
    }

    @Test
    fun aCharacterWithoutAStrideGetsTheDefault() {
        val lines = File("../app/src/main/assets/characters.txt").readLines().filterNot { it.trim().startsWith("stride") }
        for (c in Characters.parse(lines)) assertEquals(WalkSteps.DEFAULT_STRIDE_M, c.strideM, "${c.id}")
    }
}

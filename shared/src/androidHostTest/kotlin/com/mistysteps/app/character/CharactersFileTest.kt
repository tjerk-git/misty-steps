package com.mistysteps.app.character

import com.mistysteps.app.stats.FactMeasure
import com.mistysteps.app.stats.FunFacts
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
    fun theYouPageTextsExistInEnglishAndDutch() {
        val keys = listOf("you_title", "you_settings_header", "you_about_header", "you_change_character")
        for (dir in listOf("values", "values-nl")) {
            val xml = File("strings/$dir/strings.xml").readText()
            for (key in keys) assertTrue(xml.contains("name=\"$key\""), "$dir/strings.xml is missing $key")
        }
    }

    @Test
    fun noConsentStringsAreLeft() {
        for (dir in listOf("values", "values-nl")) {
            val xml = File("strings/$dir/strings.xml").readText()
            assertTrue("name=\"consent_" !in xml, "$dir/strings.xml still has consent strings")
        }
    }

    @Test
    fun thePillTextsExistInEnglishAndDutch() {
        for (dir in listOf("values", "values-nl")) {
            val lines = File("strings/$dir/strings.xml").readLines()
            val place = lines.firstOrNull { it.contains("name=\"pill_place_line\"") }
            val total = lines.firstOrNull { it.contains("name=\"pill_total_caption\"") }
            assertTrue(place != null, "$dir is missing pill_place_line")
            assertTrue(total != null, "$dir is missing pill_total_caption")
            assertTrue("%1\$s" in place && "%2\$s" in place, "$dir pill_place_line needs the place (%1\$s) and its share (%2\$s)")
            assertTrue(lines.none { it.contains("name=\"pill_place_caption\"") }, "$dir still has the old one-line pill_place_caption")
        }
    }

    @Test
    fun everyFunFactHasItsSentenceInEnglishAndDutch() {
        val keys = FactMeasure.entries.flatMap { m -> FunFacts.references(m).map { FunFacts.stringKey(m, it) } } + "facts_title"
        for (dir in listOf("values", "values-nl")) {
            val lines = File("strings/$dir/strings.xml").readLines()
            for (key in keys) {
                val line = lines.firstOrNull { it.contains("name=\"$key\"") }
                assertTrue(line != null, "$dir/strings.xml is missing $key")
                if (key != "facts_title") assertTrue("%1\$s" in line, "$dir $key needs %1\$s (the number)")
            }
        }
    }

    @Test
    fun theReviewedFunFactSentencesReadNaturally() {
        val en = File("strings/values/strings.xml").readText()
        val nl = File("strings/values-nl/strings.xml").readText()
        assertTrue("You cleared Monaco %1\$s times over." in en)
        assertTrue("You cleared Vatican City %1\$s times over." in en)
        assertTrue("Je hebt al %1\$s van de weg naar de maan afgelegd." in nl)
    }

    @Test
    fun thePostcardCloseButtonHasANeutralLabel() {
        val en = File("strings/values/strings.xml").readText()
        val nl = File("strings/values-nl/strings.xml").readText()
        assertTrue("<string name=\"postcard_close\">Back</string>" in en)
        assertTrue("<string name=\"postcard_close\">Terug</string>" in nl)
    }
}

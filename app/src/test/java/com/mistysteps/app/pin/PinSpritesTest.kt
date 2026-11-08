package com.mistysteps.app.pin

import com.mistysteps.app.character.PixelArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PinSpritesTest {

    @Test
    fun everyLabelHasASprite() {
        val sprites = PinSprites.parse(File("src/main/assets/pins.txt").readLines())
        assertEquals(PinLabel.entries.map { it.key }.toSet(), sprites.keys)
        sprites.values.forEach { assertEquals(256, it.size) }
        sprites.forEach { (k, px) -> assertTrue("$k is empty", px.count { it != 0 } >= 20) }
    }

    @Test
    fun aLetterMissingFromThePaletteIsAnError() {
        val bad = listOf("[sit]", "palette = A:ffffff", "body =") + List(16) { "Z" + ".".repeat(15) }
        val e = assertThrows(IllegalArgumentException::class.java) { PinSprites.parse(bad) }
        assertTrue(e.message!!.contains("sit"))
    }

    @Test
    fun outlineWrapsOpaquePixels() {
        val src = IntArray(256).also { it[0] = 0xFFFFFFFF.toInt() }
        val out = PixelArt.outline(src)
        assertEquals(18 * 18, out.size)
        assertEquals(0xFFFFFFFF.toInt(), out[1 * 18 + 1])
        assertEquals(PixelArt.OUTLINE, out[0 * 18 + 1])
        assertEquals(PixelArt.OUTLINE, out[1 * 18 + 2])
        assertEquals(0, out[3 * 18 + 3])
    }
}

package com.mistysteps.app.fog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FogNoiseTest {
    private val tint = 0xB0B8CE

    @Test fun makesTheRequestedSizeAndKeepsTheTint() {
        val px = FogNoise.pixels(32, seed = 7, tint = tint, maxAlpha = 0.55f, contrast = 1.6f)
        assertEquals(32 * 32, px.size)
        assertTrue(px.all { (it and 0xFFFFFF) == tint })
    }

    @Test fun alphaStaysBelowTheMaximumAndUsesTheRange() {
        val px = FogNoise.pixels(64, seed = 7, tint = tint, maxAlpha = 0.5f, contrast = 1.6f)
        val alphas = px.map { it ushr 24 }
        assertTrue(alphas.all { it <= 127 })
        assertTrue(alphas.max() > 100)
        assertTrue(alphas.min() < 20)
    }

    @Test fun sameSeedGivesTheSamePixelsAndAnotherSeedDoesNot() {
        val a = FogNoise.pixels(32, 7, tint, 0.55f, 1.6f)
        assertTrue(a.contentEquals(FogNoise.pixels(32, 7, tint, 0.55f, 1.6f)))
        assertTrue(!a.contentEquals(FogNoise.pixels(32, 23, tint, 0.55f, 1.6f)))
    }

    @Test fun tilesWithoutASeam() {
        // The last column is close to the first: the noise is periodic in the tile.
        val size = 64
        val px = FogNoise.pixels(size, seed = 23, tint = tint, maxAlpha = 0.85f, contrast = 1.8f)
        var edgeJump = 0
        var typicalJump = 0
        for (y in 0 until size) {
            edgeJump += kotlin.math.abs((px[y * size] ushr 24) - (px[y * size + size - 1] ushr 24))
            typicalJump += kotlin.math.abs((px[y * size + 1] ushr 24) - (px[y * size] ushr 24))
        }
        assertTrue(edgeJump <= typicalJump * 2 + size)
    }
}

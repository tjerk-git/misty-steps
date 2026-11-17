package com.mistysteps.app.fog

import android.graphics.Bitmap

/** The swirl texture as an Android bitmap; the pixels come from the shared [FogNoise]. */
object FogTexture {
    fun generate(size: Int, seed: Int, tint: Int, maxAlpha: Float, contrast: Float): Bitmap =
        Bitmap.createBitmap(FogNoise.pixels(size, seed, tint, maxAlpha, contrast), size, size, Bitmap.Config.ARGB_8888)
}

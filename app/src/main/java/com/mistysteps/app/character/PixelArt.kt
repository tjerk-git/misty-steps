package com.mistysteps.app.character

import android.graphics.Bitmap

/** Shared helpers for the 16x16 pixel sprites (characters and pin icons). */
object PixelArt {
    /** Colour of the 1 px outline around every sprite. */
    const val OUTLINE: Int = 0xFF1B1B2A.toInt()

    /** Size of a 16x16 sprite once outlined. */
    const val SIZE = 18

    /** Pads 16x16 to 18x18 and draws a 1 px dark outline around every opaque pixel. */
    fun outline(src: IntArray): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until 16) for (x in 0 until 16) out[(y + 1) * SIZE + x + 1] = src[y * 16 + x]
        val result = out.copyOf()
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (out[y * SIZE + x] != 0) continue
            val near = (x > 0 && out[y * SIZE + x - 1] != 0) || (x < SIZE - 1 && out[y * SIZE + x + 1] != 0) ||
                (y > 0 && out[(y - 1) * SIZE + x] != 0) || (y < SIZE - 1 && out[(y + 1) * SIZE + x] != 0)
            if (near) result[y * SIZE + x] = OUTLINE
        }
        return result
    }

    fun bitmap(px: IntArray, size: Int = SIZE): Bitmap = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

package com.mistysteps.app.pin

import android.graphics.Bitmap
import com.mistysteps.app.character.PixelArt
import com.mistysteps.app.character.bitmap

private val cache = HashMap<PinLabel, Bitmap>()

/** The outlined icon as an Android bitmap, made once, for the map painter and the postcard. */
fun PinSprites.bitmap(label: PinLabel): Bitmap = synchronized(cache) {
    cache.getOrPut(label) { PixelArt.bitmap(pixels(label)) }
}

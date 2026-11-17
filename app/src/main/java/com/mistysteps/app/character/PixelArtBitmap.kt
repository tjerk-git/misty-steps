package com.mistysteps.app.character

import android.graphics.Bitmap

/** The pixels of a sprite as an Android bitmap, for the parts of the app that draw with the Android canvas. */
fun PixelArt.bitmap(px: IntArray, size: Int = SIZE): Bitmap = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)

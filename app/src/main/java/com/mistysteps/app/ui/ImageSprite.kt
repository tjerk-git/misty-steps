package com.mistysteps.app.ui

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Draws a crisp, nearest-neighbour scaled bitmap sprite (the pin icons) filling the largest square that fits. */
fun DrawScope.drawSprite(frame: ImageBitmap) {
    val side = minOf(size.width, size.height).toInt()
    val left = ((size.width - side) / 2).toInt()
    val top = ((size.height - side) / 2).toInt()
    drawImage(
        frame,
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(side, side),
        filterQuality = FilterQuality.None,
    )
}

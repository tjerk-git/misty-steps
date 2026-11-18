package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mistysteps.app.character.Character
import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.PixelArt
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.TimeSource

/** Milliseconds since the composable appeared, ticking every [stepMs]. Read it in draw lambdas only. */
@Composable
fun rememberTicker(stepMs: Long = 70): State<Long> = produceState(0L) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        delay(stepMs)
        value = start.elapsedNow().inWholeMilliseconds
    }
}

/** The frames of one character, for drawing with [drawSprite]. */
class SpriteFrames(private val c: Character) {
    fun at(walking: Boolean, timeMs: Long): IntArray = Characters.frameAt(c, walking, timeMs)
}

/**
 * Draws a crisp, nearest-neighbour scaled sprite (ARGB pixels, [PixelArt.SIZE] square) filling the largest
 * square that fits. Pixels are drawn as rectangles snapped to whole screen pixels, so there are no seams.
 */
fun DrawScope.drawSprite(px: IntArray) {
    val n = PixelArt.SIZE
    val side = minOf(size.width, size.height)
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    val cell = side / n
    for (y in 0 until n) {
        var x = 0
        while (x < n) {
            val argb = px[y * n + x]
            if (argb ushr 24 == 0) { x++; continue }
            // A run of equal colours is one rectangle.
            var end = x + 1
            while (end < n && px[y * n + end] == argb) end++
            val x0 = floor(left + x * cell)
            val x1 = ceil(left + end * cell)
            val y0 = floor(top + y * cell)
            val y1 = ceil(top + (y + 1) * cell)
            drawRect(Color(argb), topLeft = Offset(x0, y0), size = Size(x1 - x0, y1 - y0))
            x = end
        }
    }
}

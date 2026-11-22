package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.mistysteps.app.character.Character
import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.PixelArt
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The duck on its phone, which is exactly what the last onboarding page asks you not to do on a walk: a pixel-art
 * phone in front of the chest whose screen keeps scrolling, while the duck breathes and blinks.
 */
@Composable
fun DuckPhone(character: Character, size: Dp, modifier: Modifier = Modifier) {
    val frames = remember(character) { SpriteFrames(character) }
    val ticker = rememberTicker(stepMs = 150)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val t = ticker.value
                drawSprite(frames.at(walking = false, timeMs = t))
                drawPhone((t / 150).toInt())
            },
    )
}

// Phone footprint in the sprite's 18x18 grid: a 4 wide, 7 tall body in front of the chest.
private const val PHONE_X = 14
private const val PHONE_Y = 7
private const val PHONE_W = 4
private const val PHONE_H = 7

private val Frame = Color(PixelArt.OUTLINE)
private val Case = Color(0xFF3C4258)
private val Glow = Color(0xFFBFE3FF)
private val Dim = Color(0xFF5E86B8)

private fun DrawScope.drawPhone(step: Int) {
    val n = PixelArt.SIZE
    val side = minOf(size.width, size.height)
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    val cell = side / n

    fun cellRect(x: Int, y: Int, c: Color) {
        val x0 = floor(left + x * cell)
        val x1 = ceil(left + (x + 1) * cell)
        val y0 = floor(top + y * cell)
        val y1 = ceil(top + (y + 1) * cell)
        drawRect(c, Offset(x0, y0), Size(x1 - x0, y1 - y0))
    }

    // Case with a dark outline, then the screen inside it: lines of "text" that scroll upward.
    for (y in 0 until PHONE_H) for (x in 0 until PHONE_W) {
        val edge = x == 0 || y == 0 || x == PHONE_W - 1 || y == PHONE_H - 1
        cellRect(PHONE_X + x, PHONE_Y + y, if (edge) Frame else Case)
    }
    for (y in 1 until PHONE_H - 1) for (x in 1 until PHONE_W - 1) {
        val line = (y + step) % 3 != 0
        cellRect(PHONE_X + x, PHONE_Y + y, if (line) Glow else Dim)
    }
}

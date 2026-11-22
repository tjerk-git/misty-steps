package com.mistysteps.app.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.floor

/**
 * A dog riding in a car, in pixel art: the onboarding shows it to say that riding a car or train does not clear the mist.
 * Two frames: the ear flaps, the wheels turn and the car bobs on the road, while the road dashes slide past.
 */
private object CarArt {
    const val W = 28
    const val H = 16

    private val palette = mapOf(
        'o' to 0xFF1B1B2A, 'B' to 0xFF4A7FB5, 'b' to 0xFF3A6595, 'c' to 0xFFCCE6F2, 'D' to 0xFFD39A52,
        'e' to 0xFFA8723A, 'w' to 0xFFF3DCB4, 'E' to 0xFF23232F, 'N' to 0xFF3A2A24, 'Y' to 0xFFF4D35E,
        'T' to 0xFF2B2B35, 'H' to 0xFFB8BDC9, 'R' to 0xFFC0504D,
    ).mapValues { Color(it.value) }

    // Rows 3 (the ear) and 14 (the hubcaps) differ between the two frames; everything else is shared.
    private val rows = listOf(
        "............................",
        "............................",
        "............................",
        "..........eeDDDDe...........",
        ".......ooooeDDDDDDooo.......",
        "......oBBBoeDDEDwwBBBo......",
        "......oBccoDDDDDwwNcBBo.....",
        "......oBccoeDDDDwwwcBBBo....",
        "......oBcccoDDDDDDccBBBBo...",
        ".ooooooBBBBBBBBBBBBBBBBBBoo.",
        ".oBBBBBBBBBBBBBBBBBBBBBBBBYo",
        ".oBBBBBBBBBBBBBBBBBBBBBBBBBo",
        ".obbbbbbbbbbbbbbbbbbbbbbbbbo",
        ".oRRooTTTTbbbbbbbbbTTTTooRRo",
        "..oo.TTHHTT.......TTHHTT.oo.",
        "......TTTT.........TTTT.....",
    )
    private val earUp = "..........eeDDDDe..........."
    private val earBack = ".........eeeDDDDDe.........."
    private val hubA = "..oo.TTHHTT.......TTHHTT.oo."
    private val hubB = "..oo.TTTHTT.......TTTHTT.oo."

    fun frame(flap: Boolean): List<String> = rows.toMutableList().also {
        it[3] = if (flap) earBack else earUp
        it[14] = if (flap) hubB else hubA
    }

    fun color(ch: Char): Color? = palette[ch]
}

/** The car scene at [width], sized by its 28:16 shape. */
@Composable
fun CarScene(width: Dp, modifier: Modifier = Modifier) {
    val ticker = rememberTicker(stepMs = 120)
    Box(
        modifier
            .width(width)
            .aspectRatio(CarArt.W / CarArt.H.toFloat())
            .drawBehind { drawCar(ticker.value); drawForbidden() },
    )
}

private fun DrawScope.drawCar(timeMs: Long) {
    val step = (timeMs / 120).toInt()
    val cell = floor(size.width / CarArt.W)
    // The car and the road dashes need the same whole-pixel cell, or the rows show seams.
    val bob = if (step % 2 == 1) 1 else 0
    val rows = CarArt.frame(flap = step % 2 == 1)
    // Road under the wheels: dashes sliding to the left, as the car drives to the right.
    val roadY = cell * (CarArt.H + 1)
    for (i in -1..CarArt.W / 4) {
        val x = ((i * 4 - step % 4) * cell)
        drawRect(Color(0x661B1B2A), Offset(x, roadY), Size(cell * 2, cell))
    }
    for (y in rows.indices) for (x in 0 until CarArt.W) {
        val c = CarArt.color(rows[y][x]) ?: continue
        drawRect(c, Offset(x * cell, (y + bob) * cell), Size(cell, cell))
    }
}

private val Forbidden = Color(0xFFD64545)

/** A big "not allowed" sign over the whole scene: a red ring with a slash from top left to bottom right. */
private fun DrawScope.drawForbidden() {
    val radius = size.height * 0.85f
    val stroke = size.height * 0.11f
    // Slightly see-through white inside the ring so the sign stands out from the car.
    drawCircle(Color(0x33FFFFFF), radius = radius, center = center)
    drawCircle(Forbidden, radius = radius, center = center, style = Stroke(width = stroke))
    val d = radius * 0.7071f
    drawLine(Forbidden, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), strokeWidth = stroke, cap = StrokeCap.Butt)
}

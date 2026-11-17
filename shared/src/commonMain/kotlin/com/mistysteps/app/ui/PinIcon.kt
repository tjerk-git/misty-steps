package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.mistysteps.app.pin.PinLabel
import com.mistysteps.app.pin.PinSprites

/** A crisp pixel icon for a pin label, [sizeDp] square. */
@Composable
fun PinIcon(label: PinLabel, sizeDp: Int, modifier: Modifier = Modifier) {
    val pixels = PinSprites.pixels(label)
    Box(modifier.size(sizeDp.dp).drawBehind { drawSprite(pixels) })
}

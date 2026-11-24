package com.mistysteps.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mistysteps.app.R
import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.standingBitmap
import com.mistysteps.app.map.PointerPlacement
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.roundToInt

private val Size = 64.dp

/**
 * Collects [pointer] here rather than in the map screen, so the screen is not recomposed on every camera frame while the
 * pointer shows. Place it in a box that fills the map.
 */
@Composable
fun OffscreenPointerLayer(pointer: StateFlow<PointerPlacement?>, onClick: () -> Unit) {
    val placement by pointer.collectAsState()
    placement?.let { OffscreenPointerButton(it, onClick) }
}

/**
 * A small round pointer on the screen edge: your character's face and an arrow toward where it is, shown while the camera is
 * looking elsewhere. A tap brings you back ([onClick] is the location button's action). The position is absolute, so a right-to-left layout does not mirror it.
 */
@Composable
fun OffscreenPointerButton(placement: PointerPlacement, onClick: () -> Unit) {
    val half = with(LocalDensity.current) { (Size / 2).toPx() }
    val character by Characters.selected.collectAsState()
    val ink = Ink.copy(alpha = 0.8f)
    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset((placement.x - half).roundToInt(), (placement.y - half).roundToInt()) }
            .size(Size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(Size)) {
            val c = Offset(size.width / 2, size.height / 2)
            rotate(degrees = (placement.angleRad * 180.0 / PI).toFloat(), pivot = c) {
                val tip = 32.dp.toPx()
                val base = 21.dp.toPx()
                val wing = 8.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(c.x + tip, c.y)
                        lineTo(c.x + base, c.y - wing)
                        lineTo(c.x + base, c.y + wing)
                        close()
                    },
                    color = ink,
                )
            }
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = 2.dp,
            modifier = Modifier.size(44.dp),
        ) {
            character?.let { c ->
                Image(
                    bitmap = c.standingBitmap().asImageBitmap(),
                    contentDescription = stringResource(R.string.show_my_location),
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(44.dp).padding(7.dp),
                )
            }
        }
    }
}

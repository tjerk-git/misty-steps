package com.mistysteps.app.map

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/** Where the off-screen pointer sits, and the direction it points: [angleRad] runs from the pointer toward the character (0 = right, clockwise, y down). */
data class PointerPlacement(val x: Float, val y: Float, val angleRad: Float)

/** How far the pointer stays from each edge of the map, in the same unit as the map size. */
data class PointerInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The pointer that says "your character is over there" when the camera is looking elsewhere. Pure geometry, shared by both apps.
 * All lengths share one unit (pixels on Android, points on iPhone).
 */
object OffscreenPointer {
    /** The hysteresis margin in dp; callers multiply by the screen density. */
    const val HYSTERESIS_DP = 24f

    /** Keeps the whole pointer (circle and arrow reach 32 dp from its centre) on screen and clear of the header and hint cards (top) and the button column and start bar (bottom; the column starts about 342 dp from the bottom on a tall iPhone). */
    private const val INSET_SIDE_DP = 36f
    private const val INSET_TOP_DP = 232f
    private const val INSET_BOTTOM_DP = 384f

    /** The insets for a screen with [density] pixels (or points) per dp. */
    fun insetsFor(density: Float) =
        PointerInsets(INSET_SIDE_DP * density, INSET_TOP_DP * density, INSET_SIDE_DP * density, INSET_BOTTOM_DP * density)

    fun hysteresisFor(density: Float) = HYSTERESIS_DP * density

    /**
     * Null when the pointer is hidden: no fix, a target that is not a real point, no map yet, camera following you, or the
     * character on-screen. Once shown ([wasShown]) it stays until the character is [hysteresis] inside the map, so a character
     * on the edge does not make it flicker. Otherwise it sits where the line from the map centre to the character meets the
     * map edge, pulled inside [insets] on each axis separately; an axis with no room left uses the centre.
     */
    fun place(
        hasFix: Boolean,
        targetX: Float,
        targetY: Float,
        width: Float,
        height: Float,
        following: Boolean,
        insets: PointerInsets,
        hysteresis: Float,
        wasShown: Boolean,
    ): PointerPlacement? {
        if (!hasFix || following || !(width > 0f) || !(height > 0f)) return null
        if (!targetX.isFinite() || !targetY.isFinite()) return null

        val margin = if (wasShown) hysteresis else 0f
        val inside = targetX >= margin && targetX <= width - margin && targetY >= margin && targetY <= height - margin
        if (inside) return null

        val cx = width / 2
        val cy = height / 2
        val dx = targetX - cx
        val dy = targetY - cy

        // Where the line from the centre to the character meets the edge of the map, then pulled into the free band on each axis
        // on its own. An axis with no free band left (a short or landscape screen) uses the centre of that axis.
        val toEdgeX = if (dx != 0f) cx / abs(dx) else Float.MAX_VALUE
        val toEdgeY = if (dy != 0f) cy / abs(dy) else Float.MAX_VALUE
        val k = min(toEdgeX, toEdgeY)
        val x = clampToBand(cx + dx * k, insets.left, width - insets.right, cx)
        val y = clampToBand(cy + dy * k, insets.top, height - insets.bottom, cy)
        return PointerPlacement(x, y, atan2(targetY - y, targetX - x))
    }

    private fun clampToBand(value: Float, min: Float, max: Float, centre: Float): Float =
        if (min <= max) value.coerceIn(min, max) else centre
}

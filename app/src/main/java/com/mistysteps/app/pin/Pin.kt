package com.mistysteps.app.pin

/**
 * Something you noticed on a walk, left on the map: a label and maybe a short note.
 * Stored only on this phone (the `pins` table).
 */
data class Pin(
    val id: Long,
    val lat: Double,
    val lon: Double,
    /** When it was placed, epoch millis. */
    val time: Long,
    /** A [PinLabel] key. Kept as text, so a key from a newer version survives untouched. */
    val labelKey: String,
    /** Trimmed, at most [PinRules.NOTE_MAX] characters; null when there's none. */
    val note: String?,
    /** The walk it was dropped on; null when placed later by long-press. */
    val sessionId: Long?,
) {
    val label: PinLabel get() = PinLabel.of(labelKey)
}

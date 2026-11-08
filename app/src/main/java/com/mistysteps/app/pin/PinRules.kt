package com.mistysteps.app.pin

import com.mistysteps.app.data.Mercator
import com.mistysteps.app.data.PointIndex

/** The rules around pins, kept free of Android so they can be tested on their own. */
object PinRules {
    /** Longest note, in characters. */
    const val NOTE_MAX = 80

    /** A GPS fix older than this is too old to drop a pin on. */
    const val FIX_MAX_AGE_MS = 30_000L

    /** A tap this close (dp) to a pin's icon opens it. */
    const val TAP_RADIUS_DP = 24f

    private val lineBreaks = Regex("\\s*[\\r\\n]+\\s*")

    /** One line, trimmed, at most [NOTE_MAX] characters (never cutting a character in half); null when empty. */
    fun cleanNote(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(lineBreaks, " ").trim()
        if (s.length > NOTE_MAX) {
            s = s.take(NOTE_MAX)
            if (Character.isHighSurrogate(s.last())) s = s.dropLast(1)
            s = s.trim()
        }
        return s.ifEmpty { null }
    }

    /** "Something else" says nothing without a note. Expects a note from [cleanNote]. */
    fun canPlace(label: PinLabel, note: String?): Boolean = label != PinLabel.OTHER || note != null

    /** The key to store after editing: an untouched label ([picked] null) keeps its original key. */
    fun keyAfterEdit(originalKey: String, picked: PinLabel?): String = picked?.key ?: originalKey

    /** True when [lat]/[lon] lies inside a cleared circle: you have been there. */
    fun isCleared(index: PointIndex, lat: Double, lon: Double): Boolean {
        val x = Mercator.x(lon)
        val y = Mercator.y(lat)
        var found = false
        index.forEachBucketIn(x, y, x, y) { b ->
            if (found) return@forEachBucketIn
            for (i in 0 until b.size) {
                val dx = b.data[3 * i] - x
                val dy = b.data[3 * i + 1] - y
                val r = b.data[3 * i + 2]
                if (dx * dx + dy * dy <= r * r) {
                    found = true
                    break
                }
            }
        }
        return found
    }

    /** Icons close up, dots further out, nothing at country scale. */
    fun mode(zoom: Double): PinMode = when {
        zoom > 14 -> PinMode.ICON
        zoom >= 10 -> PinMode.DOT
        else -> PinMode.HIDDEN
    }

    fun isFresh(fixAgeMs: Long): Boolean = fixAgeMs <= FIX_MAX_AGE_MS

    /**
     * The item closest to the tap, if it's within [maxDistPx]. [screenPos] gives an item's
     * position on screen, or null when it isn't on screen.
     */
    fun <T> pickPin(items: List<T>, tapX: Float, tapY: Float, maxDistPx: Float, screenPos: (T) -> Pair<Float, Float>?): T? {
        var best: T? = null
        var bestD2 = maxDistPx * maxDistPx
        for (item in items) {
            val (x, y) = screenPos(item) ?: continue
            val d2 = (x - tapX) * (x - tapX) + (y - tapY) * (y - tapY)
            if (d2 <= bestD2) {
                best = item
                bestD2 = d2
            }
        }
        return best
    }

    /** The pins of [incoming] that aren't here yet: same time and place counts as the same pin. */
    fun newPins(existing: List<Pin>, incoming: List<Pin>): List<Pin> {
        val seen = existing.mapTo(HashSet()) { key(it) }
        return incoming.filter { seen.add(key(it)) }
    }

    private fun key(p: Pin) = "${p.lat},${p.lon},${p.time}"

    /** For a walk's row: the first four labels in placing order, and how many more there are. */
    fun walkBadge(pins: List<Pin>): Pair<List<PinLabel>, Int> =
        pins.take(4).map { it.label } to (pins.size - 4).coerceAtLeast(0)

    /** "Your places": groups in label order, empty ones left out, newest pin first in each. */
    fun groupByLabel(pins: List<Pin>): List<Pair<PinLabel, List<Pin>>> {
        val byLabel = pins.groupBy { it.label }
        return PinLabel.entries.mapNotNull { label ->
            byLabel[label]?.let { label to it.sortedByDescending { p -> p.time } }
        }
    }
}

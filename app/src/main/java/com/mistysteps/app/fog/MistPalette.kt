package com.mistysteps.app.fog

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The colour of the mist through the day, from the sun's height where you are:
 * blue-grey at night, rosy at sunrise and sunset, white by day. Worked out from the
 * clock and your last position, so no weather service is needed.
 */
object MistPalette {

    /** [base]: the even veil (ARGB). [shadowTint], [lightTint]: multipliers for the swirl layers (RGB). */
    data class Colors(val base: Int, val shadowTint: Int, val lightTint: Int)

    private class Key(val elevation: Double, val base: Int, val shadow: Int, val light: Int)

    // Sun elevation in degrees → colours. Kept light enough that the dark status bar icons stay readable.
    private val keys = listOf(
        Key(-12.0, argb(236, 196, 203, 224), rgb(150, 160, 205), rgb(212, 220, 248)), // night
        Key(-6.0, argb(236, 214, 210, 232), rgb(178, 168, 214), rgb(232, 224, 250)),  // blue hour
        Key(-1.0, argb(236, 248, 222, 226), rgb(250, 196, 204), rgb(255, 232, 228)),  // sunrise / sunset
        Key(5.0, argb(236, 250, 236, 230), rgb(255, 222, 212), rgb(255, 244, 236)),   // golden hour
        Key(12.0, argb(236, 244, 245, 250), rgb(255, 255, 255), rgb(255, 255, 255)),  // day
    )

    fun at(epochMs: Long, lat: Double, lon: Double): Colors = forElevation(sunElevation(epochMs, lat, lon))

    fun forElevation(elevation: Double): Colors {
        if (elevation <= keys.first().elevation) return keys.first().let { Colors(it.base, it.shadow, it.light) }
        if (elevation >= keys.last().elevation) return keys.last().let { Colors(it.base, it.shadow, it.light) }
        val hi = keys.indexOfFirst { it.elevation >= elevation }
        val a = keys[hi - 1]
        val b = keys[hi]
        val t = (elevation - a.elevation) / (b.elevation - a.elevation)
        return Colors(mix(a.base, b.base, t), mix(a.shadow, b.shadow, t), mix(a.light, b.light, t))
    }

    /** The sun's height above the horizon in degrees (low-precision formula, good to ~1°). */
    fun sunElevation(epochMs: Long, lat: Double, lon: Double): Double {
        val d = epochMs / 86_400_000.0 - 10_957.5 // days since J2000 (2000-01-01 12:00 UTC)
        val g = Math.toRadians((357.529 + 0.98560028 * d).mod(360.0))
        val q = (280.459 + 0.98564736 * d).mod(360.0)
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmstDeg = (280.46061837 + 360.98564736629 * d).mod(360.0)
        val ha = Math.toRadians(gmstDeg + lon) - ra
        val phi = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(ha)))
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)

    private fun mix(c1: Int, c2: Int, t: Double): Int {
        fun ch(shift: Int): Int {
            val a = (c1 ushr shift) and 0xff
            val b = (c2 ushr shift) and 0xff
            return (a + (b - a) * t).toInt().coerceIn(0, 255) shl shift
        }
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

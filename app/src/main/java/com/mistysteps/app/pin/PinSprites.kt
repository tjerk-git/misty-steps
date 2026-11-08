package com.mistysteps.app.pin

import android.content.Context
import android.graphics.Bitmap
import com.mistysteps.app.character.PixelArt

/** The pixel icons of the pin labels, from `assets/pins.txt`. */
object PinSprites {
    const val SIZE = PixelArt.SIZE

    /** "Something else" dots, like the app's Dusk. */
    private const val OTHER_DOT = 0xFF5B6B8C.toInt()

    private lateinit var bitmaps: Map<PinLabel, Bitmap>

    fun init(context: Context) {
        val sprites = context.assets.open("pins.txt").bufferedReader().use { parse(it.readLines()) }
        bitmaps = PinLabel.entries.associateWith { label ->
            val px = sprites[label.key] ?: error("No icon for pin label '${label.key}'")
            PixelArt.bitmap(PixelArt.outline(px))
        }
    }

    /** The outlined 18x18 icon. */
    fun bitmap(label: PinLabel): Bitmap = bitmaps.getValue(label)

    fun dotColor(label: PinLabel): Int = label.group?.dotColor ?: OTHER_DOT

    /** Key to 16x16 ARGB pixels. Throws [IllegalArgumentException] naming the broken icon. */
    internal fun parse(lines: List<String>): Map<String, IntArray> {
        val out = LinkedHashMap<String, IntArray>()
        var key: String? = null
        var palette = ""
        var body: MutableList<String>? = null

        fun finish() {
            val k = key ?: return
            out[k] = build(k, palette, body.orEmpty())
        }

        for (line in lines) {
            val s = line.trim()
            when {
                s.startsWith("#") -> Unit
                s.startsWith("[") && s.endsWith("]") -> {
                    finish()
                    key = s.substring(1, s.length - 1)
                    palette = ""
                    body = null
                }
                key == null -> Unit
                "=" in s -> when (s.substringBefore("=").trim()) {
                    "palette" -> palette = s.substringAfter("=").trim()
                    "body" -> body = ArrayList()
                }
                s.isNotEmpty() -> body?.add(s)
            }
        }
        finish()
        return out
    }

    private fun build(key: String, palette: String, body: List<String>): IntArray {
        require(body.size == 16 && body.all { it.length == 16 }) { "Bad pin sprite '$key'" }
        val colours = palette.split(" ").filter { it.isNotBlank() }.associate {
            it[0] to (0xFF000000.toInt() or it.substringAfter(":").toInt(16))
        }
        val px = IntArray(16 * 16)
        body.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch != '.') px[y * 16 + x] = colours[ch] ?: throw IllegalArgumentException("Bad pin sprite '$key'")
            }
        }
        return px
    }
}

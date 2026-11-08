package com.mistysteps.app.character

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A playable wanderer: its walk cycle and standing pose as small pixel-art bitmaps (18x18 incl. outline). */
class Character(
    val id: String,
    val name: String,
    val blurb: String,
    /** Four walk frames. */
    val walk: List<Bitmap>,
    /** Two standing frames: a gentle breathing bob. */
    val idle: List<Bitmap>,
)

/** Loads the characters from `assets/characters.txt` and remembers which one you picked. */
object Characters {
    const val SIZE = 18
    private const val OUTLINE = 0xFF1B1B2A.toInt()

    lateinit var all: List<Character>
        private set

    private val _selected = MutableStateFlow<Character?>(null)
    val selected: StateFlow<Character?> = _selected.asStateFlow()

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("misty", Context.MODE_PRIVATE)
        all = context.assets.open("characters.txt").bufferedReader().use { parse(it.readLines()) }
        val id = prefs.getString("character", null)
        _selected.value = all.firstOrNull { it.id == id } ?: all.first()
    }

    fun select(c: Character) {
        _selected.value = c
        prefs.edit().putString("character", c.id).apply()
    }

    // ---- sprite sheet format: see the comment at the top of characters.txt -------------

    private class Raw(val id: String) {
        val props = HashMap<String, String>()
        val body = ArrayList<String>()
        val legs = ArrayList<ArrayList<String>>()
    }

    internal fun parse(lines: List<String>): List<Character> {
        val raws = ArrayList<Raw>()
        var cur: Raw? = null
        var block: MutableList<String>? = null
        for (line in lines) {
            val s = line.trim()
            when {
                s.startsWith("#") -> Unit
                s.startsWith("[") && s.endsWith("]") -> {
                    cur = Raw(s.substring(1, s.length - 1)).also { raws += it }
                    block = null
                }
                cur == null -> Unit
                "=" in s -> {
                    val key = s.substringBefore("=").trim()
                    val value = s.substringAfter("=").trim()
                    block = when (key) {
                        "body" -> cur.body
                        "legs" -> ArrayList<String>().also { cur.legs += it }
                        else -> { cur.props[key] = value; null }
                    }
                }
                s.isNotEmpty() -> block?.add(s)
            }
        }
        return raws.map { build(it) }
    }

    private fun build(raw: Raw): Character {
        require(raw.body.size == 16 && raw.legs.size == 4) { "Bad sprite '${raw.id}'" }
        val palette = raw.props.getValue("palette").split(" ").filter { it.isNotBlank() }.associate {
            it[0] to (0xFF000000.toInt() or it.substringAfter(":").toInt(16))
        }
        fun frame(legs: List<String>, bob: Int): Bitmap {
            val px = IntArray(16 * 16)
            raw.body.forEachIndexed { y, row ->
                val ty = y + bob
                if (ty < 16) row.forEachIndexed { x, ch -> palette[ch]?.let { px[ty * 16 + x] = it } }
            }
            legs.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch -> palette[ch]?.let { px[(13 + y) * 16 + x] = it } }
            }
            return outlined(px)
        }
        val walk = raw.legs.mapIndexed { i, legs -> frame(legs, if (i % 2 == 1) 1 else 0) }
        // Standing: legs together (the "passing" pose), breathing up and down.
        val idle = listOf(frame(raw.legs[1], 0), frame(raw.legs[1], 1))
        return Character(
            id = raw.id,
            name = raw.props["name"] ?: raw.id,
            blurb = raw.props["blurb"] ?: "",
            walk = walk,
            idle = idle,
        )
    }

    /** Pads 16x16 to 18x18 and draws a 1 px dark outline around every opaque pixel. */
    private fun outlined(src: IntArray): Bitmap {
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until 16) for (x in 0 until 16) out[(y + 1) * SIZE + x + 1] = src[y * 16 + x]
        val result = out.copyOf()
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (out[y * SIZE + x] != 0) continue
            val near = (x > 0 && out[y * SIZE + x - 1] != 0) || (x < SIZE - 1 && out[y * SIZE + x + 1] != 0) ||
                (y > 0 && out[(y - 1) * SIZE + x] != 0) || (y < SIZE - 1 && out[(y + 1) * SIZE + x] != 0)
            if (near) result[y * SIZE + x] = OUTLINE
        }
        return Bitmap.createBitmap(result, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    /** Frame to show at [timeMs]: walk cycle while moving, a slow breath while standing. */
    fun frameAt(c: Character, walking: Boolean, timeMs: Long): Bitmap =
        if (walking) c.walk[((timeMs / WALK_FRAME_MS) % 4).toInt()]
        else c.idle[((timeMs / IDLE_FRAME_MS) % 2).toInt()]

    const val WALK_FRAME_MS = 140L
    const val IDLE_FRAME_MS = 600L
}

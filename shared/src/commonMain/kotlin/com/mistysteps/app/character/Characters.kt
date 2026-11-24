package com.mistysteps.app.character

import com.mistysteps.app.postcard.WalkSteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A playable wanderer: its walk cycle and standing pose as small pixel-art frames (18x18 ARGB pixels incl. outline). */
class Character(
    val id: String,
    val name: String,
    val blurb: String,
    /** Four walk frames. */
    val walk: List<IntArray>,
    /** Two standing frames: a gentle breathing bob. */
    val idle: List<IntArray>,
    /** Metres per step, for the "steps" line on the walk postcard (a playful approximation). */
    val strideM: Double = WalkSteps.DEFAULT_STRIDE_M,
)

/** Where small settings are kept: SharedPreferences on Android, NSUserDefaults on iOS. */
interface KeyValueStore {
    fun getString(key: String): String?

    /** Stores [value], or removes the key when it is null. */
    fun putString(key: String, value: String?)
}

/** Loads the characters from the sheet in `characters.txt` and remembers which one you picked. */
object Characters {
    const val SIZE = PixelArt.SIZE

    lateinit var all: List<Character>
        private set

    private val _selected = MutableStateFlow<Character?>(null)
    val selected: StateFlow<Character?> = _selected.asStateFlow()

    private lateinit var prefs: KeyValueStore

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The names you gave your characters, by character id. A character without an entry shows its species name. */
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    fun customName(id: String): String? = _names.value[id]

    fun allCustomNames(): Map<String, String> = _names.value

    /** Gives a character a name ([CharacterNames] cleans it); an empty name takes it away again. Unknown ids are ignored. */
    fun setName(id: String, raw: String?) {
        if (all.none { it.id == id }) return
        val name = CharacterNames.clean(raw)
        _names.value = if (name == null) _names.value - id else _names.value + (id to name)
        prefs.putString("character_name_$id", name)
    }

    /** [sheet] is the lines of `characters.txt`; [store] keeps the choice and the names. */
    fun init(sheet: List<String>, store: KeyValueStore) {
        prefs = store
        all = parse(sheet)
        val id = prefs.getString("character")
        _selected.value = all.firstOrNull { it.id == id } ?: all.first()
        _names.value = all.mapNotNull { c -> CharacterNames.clean(prefs.getString("character_name_${c.id}"))?.let { c.id to it } }.toMap()
    }

    fun select(c: Character) {
        _selected.value = c
        prefs.putString("character", c.id)
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
        fun frame(legs: List<String>, bob: Int, blink: Boolean = false): IntArray {
            val px = IntArray(16 * 16)
            raw.body.forEachIndexed { y, row ->
                val ty = y + bob
                if (ty < 16) row.forEachIndexed { x, ch ->
                    // Blinking: the eye pixel borrows whatever's directly above it (fur/head), closing the lid.
                    // Works for every character for free since they all use 'E' for the eye.
                    if (blink && ch == 'E') {
                        val above = if (ty > 0) px[(ty - 1) * 16 + x] else 0
                        if (above != 0) px[ty * 16 + x] = above
                    } else {
                        palette[ch]?.let { px[ty * 16 + x] = it }
                    }
                }
            }
            legs.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch -> palette[ch]?.let { px[(13 + y) * 16 + x] = it } }
            }
            return outlined(px)
        }
        val walk = raw.legs.mapIndexed { i, legs -> frame(legs, if (i % 2 == 1) 1 else 0) }
        // Standing: legs together (the "passing" pose), breathing up and down, with an occasional blink.
        val idle = listOf(
            frame(raw.legs[1], 0),
            frame(raw.legs[1], 1),
            frame(raw.legs[1], 0),
            frame(raw.legs[1], 1, blink = true),
        )
        return Character(
            id = raw.id,
            name = raw.props["name"] ?: raw.id,
            blurb = raw.props["blurb"] ?: "",
            walk = walk,
            idle = idle,
            strideM = raw.props["stride"]?.toDoubleOrNull() ?: WalkSteps.DEFAULT_STRIDE_M,
        )
    }

    private fun outlined(src: IntArray): IntArray = PixelArt.outline(src)

    /** Frame to show at [timeMs]: walk cycle while moving, a slow breath (with the odd blink) while standing. */
    fun frameAt(c: Character, walking: Boolean, timeMs: Long): IntArray =
        if (walking) c.walk[((timeMs / WALK_FRAME_MS) % 4).toInt()]
        else c.idle[((timeMs / IDLE_FRAME_MS) % c.idle.size).toInt()]

    const val WALK_FRAME_MS = 140L
    const val IDLE_FRAME_MS = 600L
}

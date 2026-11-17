package com.mistysteps.app.character

import android.graphics.Bitmap
import android.content.SharedPreferences
import com.mistysteps.app.character.Characters.IDLE_FRAME_MS
import com.mistysteps.app.character.Characters.WALK_FRAME_MS

/** Android bitmaps of a character's frames, made once, for the map painter and the postcard (they draw with the Android canvas). */
private val cache = HashMap<Character, Pair<List<Bitmap>, List<Bitmap>>>()

private fun bitmaps(c: Character): Pair<List<Bitmap>, List<Bitmap>> = synchronized(cache) {
    cache.getOrPut(c) { c.walk.map { PixelArt.bitmap(it) } to c.idle.map { PixelArt.bitmap(it) } }
}

/** Frame to show at [timeMs], as a bitmap; same choice as [Characters.frameAt]. */
fun Characters.frameBitmapAt(c: Character, walking: Boolean, timeMs: Long): Bitmap {
    val (walk, idle) = bitmaps(c)
    return if (walking) walk[((timeMs / WALK_FRAME_MS) % 4).toInt()] else idle[((timeMs / IDLE_FRAME_MS) % idle.size).toInt()]
}

/** The first standing frame, for the postcard. */
fun Character.standingBitmap(): Bitmap = bitmaps(this).second[0]

/** [KeyValueStore] on Android's SharedPreferences. */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

package com.mistysteps.app.pin

import androidx.annotation.StringRes
import com.mistysteps.app.R

/** The groups the labels are shown in. [dotColor] is used when zoomed out, as ARGB. */
enum class PinGroup(val dotColor: Int, @StringRes val title: Int) {
    REST(0xFF6F8F55.toInt(), R.string.pin_group_rest),
    SENSES(0xFF8A78B0.toInt(), R.string.pin_group_senses),
    ANIMALS(0xFFC2863A.toInt(), R.string.pin_group_animals),
    FINDS(0xFF4C7AA3.toInt(), R.string.pin_group_finds),
}

/**
 * The ready-made things a pin can say. Only [key] is stored, so texts can be renamed or
 * translated without breaking old pins. Never change or reuse a key.
 */
enum class PinLabel(val key: String, val group: PinGroup?, @StringRes val text: Int) {
    SIT("sit", PinGroup.REST, R.string.pin_sit),
    VIEW("view", PinGroup.REST, R.string.pin_view),
    SHELTER("shelter", PinGroup.REST, R.string.pin_shelter),
    QUIET("quiet", PinGroup.SENSES, R.string.pin_quiet),
    SMELL("smell", PinGroup.SENSES, R.string.pin_smell),
    BIRDSONG("birdsong", PinGroup.SENSES, R.string.pin_birdsong),
    CROW("crow", PinGroup.ANIMALS, R.string.pin_crow),
    CAT("cat", PinGroup.ANIMALS, R.string.pin_cat),
    DUCKS("ducks", PinGroup.ANIMALS, R.string.pin_ducks),
    TREE("tree", PinGroup.FINDS, R.string.pin_tree),
    BUILDING("building", PinGroup.FINDS, R.string.pin_building),
    PATH("path", PinGroup.FINDS, R.string.pin_path),

    /** "Something else": needs a note. Also what unknown keys show as. */
    OTHER("other", null, R.string.pin_other);

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The label for a stored key; a key this version doesn't know reads as [OTHER]. */
        fun of(key: String): PinLabel = byKey[key] ?: OTHER
    }
}

/** How pins are drawn at a zoom level. */
enum class PinMode { ICON, DOT, HIDDEN }

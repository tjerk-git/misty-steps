package com.mistysteps.app.pin

/** The groups the labels are shown in. [dotColor] is used when zoomed out, as ARGB. */
enum class PinGroup(val dotColor: Int) {
    REST(0xFF6F8F55.toInt()),
    SENSES(0xFF8A78B0.toInt()),
    ANIMALS(0xFFC2863A.toInt()),
    FINDS(0xFF4C7AA3.toInt()),
}

/**
 * The ready-made things a pin can say. Only [key] is stored, so texts can be renamed or
 * translated without breaking old pins. Never change or reuse a key.
 */
enum class PinLabel(val key: String, val group: PinGroup?) {
    SIT("sit", PinGroup.REST),
    VIEW("view", PinGroup.REST),
    SHELTER("shelter", PinGroup.REST),
    QUIET("quiet", PinGroup.SENSES),
    SMELL("smell", PinGroup.SENSES),
    BIRDSONG("birdsong", PinGroup.SENSES),
    CROW("crow", PinGroup.ANIMALS),
    CAT("cat", PinGroup.ANIMALS),
    DUCKS("ducks", PinGroup.ANIMALS),
    TREE("tree", PinGroup.FINDS),
    BUILDING("building", PinGroup.FINDS),
    PATH("path", PinGroup.FINDS),

    /** "Something else": needs a note. Also what unknown keys show as. */
    OTHER("other", null);

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The label for a stored key; a key this version doesn't know reads as [OTHER]. */
        fun of(key: String): PinLabel = byKey[key] ?: OTHER
    }
}

/** How pins are drawn at a zoom level. */
enum class PinMode { ICON, DOT, HIDDEN }

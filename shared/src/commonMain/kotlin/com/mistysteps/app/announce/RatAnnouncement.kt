package com.mistysteps.app.announce

import com.mistysteps.app.maps.FirstRunStage

/** The one-time Christmas card that offers the Santa Rat. It never shows while a first-run step is still open. */
object RatAnnouncement {
    /** The saved "already shown" flag (Android SharedPreferences "misty", iPhone UserDefaults). */
    const val SEEN_KEY = "rat_xmas_2026_seen"

    /** The character the card offers. */
    const val CHARACTER_ID = "rat"

    fun due(stage: FirstRunStage, seen: Boolean): Boolean = stage == FirstRunStage.DONE && !seen
}

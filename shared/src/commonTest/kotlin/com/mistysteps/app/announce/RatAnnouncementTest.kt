package com.mistysteps.app.announce

import com.mistysteps.app.maps.FirstRunStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatAnnouncementTest {
    @Test
    fun dueOnlyWhenFirstRunIsDoneAndUnseen() {
        assertTrue(RatAnnouncement.due(FirstRunStage.DONE, seen = false))
        assertFalse(RatAnnouncement.due(FirstRunStage.DONE, seen = true))
        assertFalse(RatAnnouncement.due(FirstRunStage.ONBOARDING, seen = false))
        assertFalse(RatAnnouncement.due(FirstRunStage.PICKER, seen = false))
    }

    @Test
    fun keysAreStable() {
        assertEquals("rat_xmas_2026_seen", RatAnnouncement.SEEN_KEY)
        assertEquals("rat", RatAnnouncement.CHARACTER_ID)
    }
}

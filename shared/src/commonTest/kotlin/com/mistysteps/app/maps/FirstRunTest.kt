package com.mistysteps.app.maps

import kotlin.test.assertEquals
import kotlin.test.Test

class FirstRunTest {
    private fun stage(onboardingDone: Boolean, pickerSeen: Boolean, hasTrail: Boolean) =
        FirstRun.stage(onboardingDone, pickerSeen, hasTrail)

    @Test
    fun aNewInstallGoesThroughOnboardingThenThePicker() {
        assertEquals(FirstRunStage.ONBOARDING, stage(false, false, false))
        assertEquals(FirstRunStage.PICKER, stage(true, false, false))
        assertEquals(FirstRunStage.DONE, stage(true, true, false))
    }

    @Test
    fun anExistingUserWhoSawThePickerSeesNothing() {
        assertEquals(FirstRunStage.DONE, stage(false, true, true))
    }

    @Test
    fun anExistingUserWhoNeverSawThePickerSkipsOnboardingButGetsThePicker() {
        assertEquals(FirstRunStage.PICKER, stage(false, false, true))
    }

    @Test
    fun onboardingIsNotShownAgainOnceDone() {
        assertEquals(FirstRunStage.PICKER, stage(true, false, false))
    }

    @Test
    fun everythingSeenShowsNothing() {
        assertEquals(FirstRunStage.DONE, stage(true, true, true))
    }
}

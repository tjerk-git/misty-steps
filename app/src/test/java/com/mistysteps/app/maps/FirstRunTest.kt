package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunTest {
    private fun stage(onboardingDone: Boolean, consentAsked: Boolean, pickerSeen: Boolean, hasTrail: Boolean) =
        FirstRun.stage(onboardingDone, consentAsked, pickerSeen, hasTrail)

    @Test
    fun aNewInstallGoesThroughOnboardingConsentAndPickerInOrder() {
        assertEquals(FirstRunStage.ONBOARDING, stage(false, false, false, false))
        assertEquals(FirstRunStage.CONSENT, stage(true, false, false, false))
        assertEquals(FirstRunStage.PICKER, stage(true, true, false, false))
        assertEquals(FirstRunStage.DONE, stage(true, true, true, false))
    }

    @Test
    fun anExistingUserWhoSawThePickerOnlyGetsTheConsentStepOnce() {
        assertEquals(FirstRunStage.CONSENT, stage(false, false, true, true))
        assertEquals(FirstRunStage.DONE, stage(false, true, true, true))
    }

    @Test
    fun anExistingUserWhoNeverSawThePickerSkipsOnboardingButGetsConsentThenPicker() {
        assertEquals(FirstRunStage.CONSENT, stage(false, false, false, true))
        assertEquals(FirstRunStage.PICKER, stage(false, true, false, true))
    }

    @Test
    fun onboardingIsNotShownAgainOnceDone() {
        assertEquals(FirstRunStage.CONSENT, stage(true, false, false, false))
    }

    @Test
    fun everythingAnsweredShowsNothing() {
        assertEquals(FirstRunStage.DONE, stage(true, true, true, true))
    }

    @Test
    fun consentAskedOnceIsNeverAskedAgain() {
        // Whatever else is still due, an answered consent step is skipped.
        assertEquals(FirstRunStage.PICKER, stage(false, true, false, true))
        assertEquals(FirstRunStage.DONE, stage(false, true, true, false))
    }
}

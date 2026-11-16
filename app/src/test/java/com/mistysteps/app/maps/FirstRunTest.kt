package com.mistysteps.app.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunTest {
    @Test
    fun newInstallSeesOnboardingThenPicker() {
        assertEquals(FirstRunScreen.ONBOARDING_THEN_PICKER, FirstRun.decide(onboardingDone = false, pickerSeen = false, hasTrailPoints = false))
    }

    @Test
    fun existingUserSkipsOnboardingAndSeesThePickerOnce() {
        assertEquals(FirstRunScreen.PICKER, FirstRun.decide(onboardingDone = false, pickerSeen = false, hasTrailPoints = true))
    }

    @Test
    fun onboardingDoneButPickerNotSeenShowsThePicker() {
        assertEquals(FirstRunScreen.PICKER, FirstRun.decide(onboardingDone = true, pickerSeen = false, hasTrailPoints = false))
    }

    @Test
    fun everythingSeenShowsNothing() {
        assertEquals(FirstRunScreen.NONE, FirstRun.decide(onboardingDone = true, pickerSeen = true, hasTrailPoints = true))
    }

    @Test
    fun pickerSeenWithoutOnboardingShowsNothing() {
        assertEquals(FirstRunScreen.NONE, FirstRun.decide(onboardingDone = false, pickerSeen = true, hasTrailPoints = false))
    }
}

package com.mistysteps.app.maps

/** What to show on top of the map when the app opens. */
enum class FirstRunScreen { ONBOARDING_THEN_PICKER, PICKER, NONE }

object FirstRun {
    /**
     * A new install sees the onboarding and then the picker. Someone who already has trail points knows the
     * app: they only see the picker, once. Everything seen shows nothing.
     */
    fun decide(onboardingDone: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunScreen = when {
        pickerSeen -> FirstRunScreen.NONE
        onboardingDone || hasTrailPoints -> FirstRunScreen.PICKER
        else -> FirstRunScreen.ONBOARDING_THEN_PICKER
    }
}

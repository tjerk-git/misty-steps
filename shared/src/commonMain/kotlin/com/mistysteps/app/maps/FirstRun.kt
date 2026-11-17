package com.mistysteps.app.maps

/** The steps shown on top of the map when the app opens, in this order. */
enum class FirstRunStage { ONBOARDING, CONSENT, PICKER, DONE }

object FirstRun {
    /**
     * The first step that is still due. A new install sees the onboarding, then the question about counting opens, then the
     * province picker. Someone who already has trail points knows the app: no onboarding, only what they haven't seen. An
     * answered consent step is never asked again.
     */
    fun stage(onboardingDone: Boolean, consentAsked: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunStage = when {
        !onboardingDone && !pickerSeen && !hasTrailPoints -> FirstRunStage.ONBOARDING
        !consentAsked -> FirstRunStage.CONSENT
        !pickerSeen -> FirstRunStage.PICKER
        else -> FirstRunStage.DONE
    }
}

package com.mistysteps.app.maps

/** The steps shown on top of the map when the app opens, in this order. */
enum class FirstRunStage { ONBOARDING, PICKER, DONE }

object FirstRun {
    /**
     * The first step that is still due. A new install sees the onboarding, then the province picker. Someone who already has
     * trail points knows the app: no onboarding, only what they haven't seen.
     */
    fun stage(onboardingDone: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunStage = when {
        !onboardingDone && !pickerSeen && !hasTrailPoints -> FirstRunStage.ONBOARDING
        !pickerSeen -> FirstRunStage.PICKER
        else -> FirstRunStage.DONE
    }
}

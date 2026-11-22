package com.mistysteps.app.eraser

/** Counts down the free taste of the eraser: [EraserRules.TASTE_M] of drag in all, once per install. [save] stores the amount used so far. */
class TasteLedger(usedM: Double, private val save: (Double) -> Unit) {
    var usedM: Double = usedM.coerceAtLeast(0.0)
        private set

    val metersLeft: Double get() = (EraserRules.TASTE_M - usedM).coerceAtLeast(0.0)

    fun spend(meters: Double) {
        if (meters <= 0.0) return
        usedM += meters
        save(usedM)
    }

    /** For the debug switch only. */
    fun reset() {
        usedM = 0.0
        save(0.0)
    }
}

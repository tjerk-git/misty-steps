package com.mistysteps.app.eraser

import kotlinx.coroutines.flow.StateFlow

/** What the person may do with the eraser right now. */
sealed interface EraserState {
    /** This build cannot sell it (F-Droid): no button. */
    object NotAvailable : EraserState

    /** Not bought and the free taste is used up: tapping the button shows the purchase sheet. */
    object Locked : EraserState

    /** The free taste: [metersLeft] of drag still to go. */
    data class Taste(val metersLeft: Double) : EraserState

    object Unlocked : EraserState
}

fun resolveEraserState(storeAvailable: Boolean, owned: Boolean, tasteUsedM: Double): EraserState = when {
    !storeAvailable -> EraserState.NotAvailable
    owned -> EraserState.Unlocked
    EraserRules.TASTE_M - tasteUsedM > 0.0 -> EraserState.Taste(EraserRules.TASTE_M - tasteUsedM)
    else -> EraserState.Locked
}

enum class BuyResult { BOUGHT, CANCELLED, UNAVAILABLE }

/** Buying the eraser. The Play build has a real one; every other build has one that is not available. */
interface EraserPurchases {
    val storeAvailable: Boolean
    val owned: StateFlow<Boolean>

    /** The price as the store shows it, e.g. "4,99", once known. */
    val priceText: StateFlow<String?>

    suspend fun buy(): BuyResult
}

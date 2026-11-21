package com.mistysteps.app.eraser

import android.content.Context
import android.provider.Settings
import com.mistysteps.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Builds that cannot sell the eraser (F-Droid): no button at all. */
object NoPurchases : EraserPurchases {
    override val storeAvailable = false
    override val owned: StateFlow<Boolean> = MutableStateFlow(false)
    override val priceText: StateFlow<String?> = MutableStateFlow(null)
    override suspend fun buy() = BuyResult.UNAVAILABLE
}

/** Where the screen finds out what the person may do with the eraser. */
object EraserAccessHolder {
    /** Set once at app start: the Play build puts the real one here. */
    @Volatile
    var purchases: EraserPurchases = NoPurchases

    private var ledger: TasteLedger? = null

    fun ledger(context: Context): TasteLedger = ledger ?: run {
        val prefs = context.applicationContext.getSharedPreferences("eraser", Context.MODE_PRIVATE)
        TasteLedger(prefs.getFloat("taste_used_m", 0f).toDouble()) { prefs.edit().putFloat("taste_used_m", it.toFloat()).apply() }
            .also { ledger = it }
    }

    /**
     * Debug builds only: `adb shell settings put global misty_eraser 0|1|2` forces the unlocked state (0), the locked
     * one (1) or the free taste (2, reset to a full 100 m when the screen starts); anything else follows the real state. Release builds never read it.
     */
    fun debugOverride(context: Context): Int? {
        if (!BuildConfig.DEBUG) return null
        return Settings.Global.getInt(context.contentResolver, "misty_eraser", -1).takeIf { it in 0..2 }
    }

    /** What the person may do now. [owned] is the purchase state, [usedM] what the taste has used. */
    fun state(context: Context, owned: Boolean, usedM: Double): EraserState = when (debugOverride(context)) {
        0 -> EraserState.Unlocked
        1 -> EraserState.Locked
        2 -> resolveEraserState(true, false, usedM) // a free taste that counts down for real (the screen resets it at start)
        else -> resolveEraserState(purchases.storeAvailable, owned, usedM)
    }
}

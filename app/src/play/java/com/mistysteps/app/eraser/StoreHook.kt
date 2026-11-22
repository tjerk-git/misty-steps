package com.mistysteps.app.eraser

import android.app.Application

/** The Play build (`-Pstore=play`): the eraser is sold through Google Play Billing. */
object StoreHook {
    fun purchases(app: Application): EraserPurchases = PlayPurchases(app)
}

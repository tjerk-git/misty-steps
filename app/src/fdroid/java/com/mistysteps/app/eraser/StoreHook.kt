package com.mistysteps.app.eraser

import android.app.Application

/** The default build (F-Droid, debug, release): no store, so no eraser button and no billing code. The Play build has its own copy of this file. */
object StoreHook {
    fun purchases(app: Application): EraserPurchases = NoPurchases
}

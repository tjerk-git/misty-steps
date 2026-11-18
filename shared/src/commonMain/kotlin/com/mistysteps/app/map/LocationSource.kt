package com.mistysteps.app.map

import kotlinx.coroutines.flow.StateFlow

/**
 * Where you are, as the shared logic sees it. Each platform implements this on its own location API
 * (Android: LocationManager, iOS: Core Location); [fix] always holds the best recent [Fix].
 */
interface LocationSource {
    val fix: StateFlow<Fix?>

    /** Starts listening; the caller has checked the location permission. */
    fun start()

    fun stop()
}

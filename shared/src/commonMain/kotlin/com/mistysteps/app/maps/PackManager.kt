package com.mistysteps.app.maps

import kotlinx.coroutines.flow.StateFlow

/** The map packs on this device and what can be done with them; each platform downloads and stores them its own way. */
interface PackManager {
    /** Changes on every pack change, so screens read the states again. */
    val version: StateFlow<Int>

    /** True when the catalog needs a newer app than this one. */
    val needsAppUpdate: StateFlow<Boolean>

    fun state(entry: PackEntry): PackState

    /** Starts a download in the background; [unmeteredOnly] waits for wifi. */
    fun download(entry: PackEntry, unmeteredOnly: Boolean)

    /** Stops a download and deletes what is on the device of this pack. */
    fun remove(id: String)

    /** Whether the current connection is metered (mobile data). */
    fun isMetered(): Boolean

    /** True when this province's neighbourhoods-and-streets file is on the device. Platforms without them say no. */
    fun hasStats(id: String): Boolean = false
}

/** Download progress in whole percent. */
fun percent(state: PackState.Downloading): Int =
    if (state.total > 0) (state.bytes * 100 / state.total).toInt().coerceIn(0, 100) else 0

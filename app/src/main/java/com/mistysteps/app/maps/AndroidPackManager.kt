package com.mistysteps.app.maps

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.StateFlow

/** The map packs on Android: files under [MapsGraph], downloads through WorkManager ([PackDownloads]). */
class AndroidPackManager(private val context: Context) : PackManager {
    override val version: StateFlow<Int> get() = MapsGraph.packs.version

    override val needsAppUpdate: StateFlow<Boolean> get() = MapsGraph.catalogStore.needsAppUpdate

    override fun state(entry: PackEntry): PackState = MapsGraph.packs.state(entry)

    override fun download(entry: PackEntry, unmeteredOnly: Boolean) = PackDownloads.enqueue(context, entry, unmeteredOnly)

    override fun remove(id: String) {
        PackDownloads.cancel(context, id)
        MapsGraph.packs.remove(id)
    }

    override fun isMetered(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}

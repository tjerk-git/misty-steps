package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.maps.AndroidPackManager
import com.mistysteps.app.maps.Catalog
import com.mistysteps.app.maps.MapsGraph
import com.mistysteps.app.maps.PackManager

/** The catalog: the cached copy straight away, then a refresh when online. Null while there is none. */
@Composable
fun rememberCatalog(): State<Catalog?> = produceState(MapsGraph.catalogStore.cached()) {
    value = MapsGraph.catalogStore.refresh() ?: value
}

/** The pack manager for the shared map screens. */
@Composable
fun rememberPackManager(): PackManager {
    val context = LocalContext.current
    return remember { AndroidPackManager(context) }
}

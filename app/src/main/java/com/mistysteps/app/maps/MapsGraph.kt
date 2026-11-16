package com.mistysteps.app.maps

import android.content.Context
import java.io.File

/** The map packs, catalog and province index, shared by the screens and the download job. Set up once in `MistyApplication`. */
object MapsGraph {
    lateinit var packs: ProvincePacks
        private set
    lateinit var catalogStore: CatalogStore
        private set
    lateinit var provinces: ProvinceIndex
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        packs = ProvincePacks(File(app.filesDir, "maps/nl"))
        catalogStore = CatalogStore(app)
        provinces = ProvinceIndex.load(app)
    }
}

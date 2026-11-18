package com.mistysteps.app.maps

import android.content.Context

/** Reads the province index from the app's assets (the parsing itself is shared with iOS). */
fun ProvinceIndex.Companion.load(context: Context, asset: String = ProvinceIndex.ASSET): ProvinceIndex =
    context.assets.open(asset).use { ProvinceIndex.parse(it.bufferedReader().readText()) }

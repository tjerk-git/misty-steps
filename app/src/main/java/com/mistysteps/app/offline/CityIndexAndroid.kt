package com.mistysteps.app.offline

import android.content.Context
import java.io.InputStream

/** The country index from the app's assets. */
fun CityIndex.Companion.load(context: Context, asset: String = CityIndex.NL_ASSET): CityIndex =
    context.assets.open(asset).use { read(it) }

fun CityIndex.Companion.read(input: InputStream): CityIndex = parse(input.bufferedReader().readText())

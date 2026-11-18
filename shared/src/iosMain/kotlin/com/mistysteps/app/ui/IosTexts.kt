package com.mistysteps.app.ui

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.toast_looking
import com.mistysteps.app.resources.toast_pin_fog
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** Short texts the Swift screens show themselves, from the shared (translated) strings. */
fun pinFogHint(): String = runBlocking { getString(Res.string.toast_pin_fog) }

fun pinLookingHint(): String = runBlocking { getString(Res.string.toast_looking) }

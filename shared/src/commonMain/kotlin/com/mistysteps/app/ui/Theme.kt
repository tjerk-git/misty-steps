package com.mistysteps.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF2E3550)
val Dusk = Color(0xFF5B6B8C)
val Frost = Color(0xF2FAFBFF)
val Ember = Color(0xFFB4536A)

@Composable
fun MistyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            secondary = Dusk,
            surface = Frost,
            onSurface = Ink,
        ),
        content = content,
    )
}

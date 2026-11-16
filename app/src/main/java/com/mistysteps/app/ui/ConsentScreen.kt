package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder until the real consent screen (Task 4): continues without agreeing. */
@Composable
fun ConsentScreen(onAnswer: (allowed: Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().blockTouchesBelow()) {
        Button(onClick = { onAnswer(false) }) { Text("Continue") }
    }
}

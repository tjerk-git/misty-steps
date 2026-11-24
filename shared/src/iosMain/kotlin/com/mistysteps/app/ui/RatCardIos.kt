package com.mistysteps.app.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The Santa Rat card on top of the map. The view is transparent, so the map shows through the dimmed area; [onTry] and
 * [onLater] say what was chosen, once, and the caller then takes the view away.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun ratCardViewController(onTry: () -> Unit, onLater: () -> Unit): UIViewController =
    ComposeUIViewController(configure = { opaque = false }) {
        MistyTheme { RatCard(onTry, onLater) }
    }

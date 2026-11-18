package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import com.mistysteps.app.pin.PinBook
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

/**
 * The pin sheets on top of the map: [state] says whether a new pin is being left or an existing one looked at. The view
 * is transparent, so the map shows through the dimmed area; [onDone] is called when the sheet should go away.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun pinSheetViewController(book: PinBook, state: PinSheetState, onDone: () -> Unit): UIViewController =
    ComposeUIViewController(configure = { opaque = false }) {
        MistyTheme { PinSheets(book, state, onDone) }
    }

@Composable
private fun PinSheets(book: PinBook, state: PinSheetState, onDone: () -> Unit) {
    val pins by book.pins.collectAsState()
    when (state) {
        is PinSheetState.New -> NewPinSheet(
            onPlace = { key, note ->
                book.add(state.lat, state.lon, key, note, state.sessionId, (NSDate().timeIntervalSince1970 * 1000).toLong())
                onDone()
            },
            onDismiss = onDone,
        )
        is PinSheetState.Open -> pins.firstOrNull { it.id == state.pinId }?.let { pin ->
            PinDetailSheet(
                pin = pin,
                onSave = { key, note -> book.update(pin.id, key, note) },
                onDelete = { book.delete(pin.id); onDone() },
                onDismiss = onDone,
            )
        } ?: onDone()
    }
}

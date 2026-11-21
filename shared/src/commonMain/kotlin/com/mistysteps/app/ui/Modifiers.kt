package com.mistysteps.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A full-screen page that scrolls. Content stops at the status bar (so it never runs under the clock)
 * but may scroll behind the navigation bar at the bottom.
 */
@Composable
fun Modifier.scrollingPage(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    .verticalScroll(rememberScrollState())
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

/**
 * Full-screen pages sit on top of the map: stop taps falling through to the buttons underneath.
 *
 * A pointer input on the page is enough for that: Compose gives touches to the top-most sibling that has one, so what is
 * underneath never sees them. The touches must NOT be consumed here. A click inside the page (a button, a list row) is
 * cancelled when any parent consumed one of its move events in the Final pass, and a real finger sends small moves between
 * down and up. Consuming made every button on a full-screen page dead for a real touch (a Galaxy S21's panel reports
 * those moves) while a synthetic tap, which has none, still worked.
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial)
    }
}

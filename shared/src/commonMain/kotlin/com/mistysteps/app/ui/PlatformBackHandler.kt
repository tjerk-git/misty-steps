package com.mistysteps.app.ui

import androidx.compose.runtime.Composable

/** Reacts to the system Back action. Android's back button/gesture; iOS has no equivalent, so nothing happens there. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

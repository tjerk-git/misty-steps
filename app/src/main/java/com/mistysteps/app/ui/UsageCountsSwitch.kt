package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.stats.Analytics

/** The shared usage-count card, kept on Android's [Analytics] settings. */
@Composable
fun UsageCountsSwitch() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Analytics.isOptedIn(context)) }
    UsageCountsCard(
        optedIn = on,
        onOptedInChange = {
            on = it
            Analytics.setOptedIn(context, it)
        },
        versionName = BuildConfig.VERSION_NAME,
    )
}

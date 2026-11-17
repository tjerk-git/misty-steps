package com.mistysteps.app.ui

import com.mistysteps.app.R

/** The words in a duration from Android string resources, for code outside Compose. */
fun durationText(res: android.content.res.Resources) = DurationText(
    res.getString(R.string.duration_under_minute),
    res.getString(R.string.duration_min),
    res.getString(R.string.duration_h_min),
)

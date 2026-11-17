package com.mistysteps.app.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

actual fun formatNumber(value: Double, decimals: Int, grouping: Boolean): String =
    String.format(Locale.getDefault(), if (grouping) "%,.${decimals}f" else "%.${decimals}f", value)

actual fun formatDateTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

actual fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

actual fun countryName(region: String): String =
    Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault())

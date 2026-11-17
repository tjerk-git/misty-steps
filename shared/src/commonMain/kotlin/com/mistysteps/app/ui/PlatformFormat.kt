package com.mistysteps.app.ui

/**
 * Number and date formatting in the person's language and region (decimal comma or point, date order). Each platform
 * uses its own formatter; the rounding and wording rules around them are shared ([formatArea] and friends).
 */
expect fun formatNumber(value: Double, decimals: Int, grouping: Boolean = false): String

/** A date with time, short, like "17 Nov 2026, 21:45". */
expect fun formatDateTime(epochMs: Long): String

/** A date, like "17 Nov 2026". */
expect fun formatDate(epochMs: Long): String

/** The name of the country with ISO [region] code in the phone's language, or blank when unknown. */
expect fun countryName(region: String): String

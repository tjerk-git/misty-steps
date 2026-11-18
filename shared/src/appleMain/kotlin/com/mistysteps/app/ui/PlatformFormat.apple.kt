package com.mistysteps.app.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatNumber(value: Double, decimals: Int, grouping: Boolean): String {
    val f = NSNumberFormatter()
    f.numberStyle = NSNumberFormatterDecimalStyle
    f.minimumFractionDigits = decimals.toULong()
    f.maximumFractionDigits = decimals.toULong()
    f.usesGroupingSeparator = grouping
    return f.stringFromNumber(NSNumber(value)) ?: value.toString()
}

private fun dateText(epochMs: Long, time: Boolean): String {
    val f = NSDateFormatter()
    f.dateStyle = NSDateFormatterMediumStyle
    f.timeStyle = if (time) NSDateFormatterShortStyle else NSDateFormatterNoStyle
    return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
}

actual fun formatDateTime(epochMs: Long): String = dateText(epochMs, time = true)

actual fun formatDate(epochMs: Long): String = dateText(epochMs, time = false)

actual fun countryName(region: String): String = NSLocale.currentLocale.displayNameForKey(NSLocaleCountryCode, region) ?: ""

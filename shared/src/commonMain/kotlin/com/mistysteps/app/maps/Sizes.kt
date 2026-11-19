package com.mistysteps.app.maps

import com.mistysteps.app.ui.formatNumber

/** "62 MB" or "1.2 GB": decimal units (1 MB = 1,000,000 bytes), in the phone's number format. */
fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) formatNumber(bytes / 1e9, 1) + " GB"
    else formatNumber(bytes / 1e6, 0) + " MB"

/** A download speed: "3.2 MB" or "850 KB" (the caller adds "/s"). */
fun formatSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond >= 1_000_000L) formatNumber(bytesPerSecond / 1e6, 1) + " MB"
    else formatNumber(bytesPerSecond / 1e3, 0) + " KB"

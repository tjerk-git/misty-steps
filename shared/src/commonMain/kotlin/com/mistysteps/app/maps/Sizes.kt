package com.mistysteps.app.maps

import com.mistysteps.app.ui.formatNumber

/** "62 MB" or "1.2 GB": decimal units (1 MB = 1,000,000 bytes), in the phone's number format. */
fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) formatNumber(bytes / 1e9, 1) + " GB"
    else formatNumber(bytes / 1e6, 0) + " MB"

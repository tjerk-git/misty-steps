package com.mistysteps.app.maps

import java.util.Locale

/** "62 MB" or "1.2 GB": decimal units (1 MB = 1,000,000 bytes), in the phone's number format. */
fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9)
    else String.format(Locale.getDefault(), "%.0f MB", bytes / 1e6)

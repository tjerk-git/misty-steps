package com.mistysteps.app.maps

object DownloadPolicy {
    /** From this size on (decimal megabytes, as the picker shows them) mobile data gets a warning first. */
    const val WARN_BYTES = 50_000_000L

    fun needsDataWarning(metered: Boolean, bytes: Long): Boolean = metered && bytes >= WARN_BYTES

    /** The download and the temporary copy while checking it need a fifth more room than the file itself. */
    fun hasSpaceFor(bytes: Long, freeBytes: Long): Boolean = freeBytes >= bytes + bytes / 5
}

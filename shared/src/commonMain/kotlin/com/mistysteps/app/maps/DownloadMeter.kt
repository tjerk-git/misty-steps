package com.mistysteps.app.maps

/**
 * Works out how fast a download is going from the byte counts it reports, and from that how long is left.
 * The speed is smoothed so the estimate does not jump around with every chunk.
 */
class DownloadMeter {
    private var lastMs = -1L
    private var lastBytes = 0L
    private var rate = 0.0

    /** Bytes per second, or 0 until there is a measurement. */
    val bytesPerSecond: Long get() = rate.toLong()

    /** Reports that [bytes] had arrived at [nowMs] (any steady clock, in milliseconds). */
    fun update(nowMs: Long, bytes: Long) {
        if (lastMs < 0 || bytes < lastBytes) { // first sample, or the download started over
            lastMs = nowMs
            lastBytes = bytes
            rate = 0.0
            return
        }
        if (bytes == lastBytes) return
        val seconds = (nowMs - lastMs) / 1000.0
        if (seconds < MIN_WINDOW_S) return // too soon to say; keep collecting
        val now = (bytes - lastBytes) / seconds
        rate = if (rate == 0.0) now else rate * (1 - SMOOTHING) + now * SMOOTHING
        lastMs = nowMs
        lastBytes = bytes
    }

    /** Whole seconds until [total] bytes have arrived, or null while the speed is unknown. */
    fun secondsLeft(bytes: Long, total: Long): Int? =
        if (rate <= 0.0 || total <= 0) null else ((total - bytes).coerceAtLeast(0) / rate).toInt() + 1

    private companion object {
        const val MIN_WINDOW_S = 0.5
        const val SMOOTHING = 0.3
    }
}

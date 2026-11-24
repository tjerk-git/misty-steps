package com.mistysteps.app.stats

import kotlin.math.roundToLong

/**
 * A small joke on the map: how much mist you cleared, told in buckets. One bucket is 10 litres and the mist is taken as 2 m deep
 * (head height), so one square metre of cleared ground is 200 buckets of mist.
 */
object MistBuckets {
    const val PER_SQUARE_METRE = 200.0

    /** The buckets in [areaM2] square metres; 0 for no ground or numbers that are not real. */
    fun count(areaM2: Double): Long {
        if (!areaM2.isFinite() || areaM2 <= 0.0) return 0L
        val buckets = areaM2 * PER_SQUARE_METRE
        return if (buckets >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else buckets.roundToLong()
    }

    /** Exact below 100, two significant digits from 100 up: 7,812,345 reads as 7,800,000. */
    fun readable(buckets: Long): Long {
        if (buckets < 100L) return buckets
        var magnitude = 10L
        while (buckets / magnitude >= 100L) {
            if (magnitude > Long.MAX_VALUE / 10) return buckets
            magnitude *= 10L
        }
        val rounded = buckets / magnitude + if (buckets % magnitude * 2 >= magnitude) 1L else 0L
        return rounded * magnitude
    }
}

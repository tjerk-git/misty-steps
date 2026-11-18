package com.mistysteps.app.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * [PointIndex.snapshotIn] as raw bytes (little-endian doubles: x, y, radius per spot), so Swift can read
 * thousands of spots in one go instead of one bridged array access per number.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun PointIndex.snapshotData(minX: Double, minY: Double, maxX: Double, maxY: Double): NSData {
    val spots = snapshotIn(minX, minY, maxX, maxY)
    if (spots.isEmpty()) return NSData()
    return spots.usePinned { NSData.create(bytes = it.addressOf(0), length = (spots.size * 8).convert()) }
}

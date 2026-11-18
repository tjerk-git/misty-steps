package com.mistysteps.app.data

/**
 * Where the cleared spots are kept on the device. Android implements it on SQLiteOpenHelper
 * (TrailDatabase), iOS on SQLite3. Calls may block, so callers use a background thread.
 */
interface PointStore {
    fun insert(points: List<TrailPoint>)

    /** Every stored spot, oldest first. */
    fun loadAll(): List<TrailPoint>

    fun countPoints(): Long
}

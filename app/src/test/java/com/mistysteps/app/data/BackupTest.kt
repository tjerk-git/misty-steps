package com.mistysteps.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val sample = Backup(
        exportedAt = 1_794_150_000_000,
        appVersion = "1.1.0",
        distanceM = 21_700.5,
        character = "horse",
        sessions = listOf(
            Session(1_794_142_883_836, 1_794_142_883_836, 1_794_144_505_000, 7540.0, 575, 90, 89, 719_900.0),
            Session(1_794_151_469_942, 1_794_151_469_942, null), // never finished: nulls survive
        ),
        points = listOf(
            TrailPoint(52.3794, 4.8631, 1_794_142_884_000, 100.0, 1_794_142_883_836),
            TrailPoint(52.3801, 4.8640, 1_794_142_886_000, 50.0, 1_794_142_883_836),
            TrailPoint(53.2000, 5.8000, 1_700_000_000_000, 50.0, null), // from before walks were logged
        ),
    )

    @Test
    fun roundTripsEverything() {
        val back = Backup.fromJson(sample.toJson())
        assertEquals(sample, back)
        assertNull(back.sessions[1].endTime)
        assertNull(back.points[2].sessionId)
    }

    @Test
    fun saysClearlyWhenAFileIsntABackup() {
        val notJson = assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("hello") }
        assertEquals("This file isn't a Misty Steps backup.", notJson.message)
        val otherJson = assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""{"type":"gpx"}""") }
        assertEquals("This file isn't a Misty Steps backup.", otherJson.message)
    }

    @Test
    fun refusesBackupsFromANewerFormat() {
        val newer = sample.toJson().replace("\"version\":1", "\"version\":2")
        val e = assertThrows(IllegalArgumentException::class.java) { Backup.fromJson(newer) }
        assertTrue(e.message!!.contains("newer Misty Steps"))
    }

    @Test
    fun refusesBrokenCoordinates() {
        val broken = sample.copy(points = listOf(TrailPoint(123.0, 5.0, 0L)))
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson(broken.toJson()) }
    }
}

package com.mistysteps.app.data

import android.os.Handler
import android.os.Looper
import com.mistysteps.app.pin.Pin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Your pins, kept in memory for drawing and written to the trail database on a background
 * thread. Call from the main thread unless a function says otherwise.
 */
object PinStore {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var db: TrailDatabase

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    /** Every pin, newest first. */
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val _justPlaced = MutableStateFlow<Long?>(null)
    /** The pin placed a moment ago, so the map can drop it in with a bounce. */
    val justPlaced: StateFlow<Long?> = _justPlaced.asStateFlow()

    /** Called by [FogStore.init] with the shared database. */
    fun init(database: TrailDatabase) {
        db = database
        reload()
    }

    /** Reads every pin from the database again (after an import). Any thread. */
    fun reload() {
        io.execute {
            val all = db.loadPins()
            main.post { _pins.value = all }
        }
    }

    fun add(lat: Double, lon: Double, labelKey: String, note: String?, sessionId: Long?) {
        val draft = Pin(0, lat, lon, System.currentTimeMillis(), labelKey, note, sessionId)
        io.execute {
            val id = db.insertPin(draft)
            main.post {
                _pins.value = listOf(draft.copy(id = id)) + _pins.value
                _justPlaced.value = id
                main.postDelayed({ if (_justPlaced.value == id) _justPlaced.value = null }, BOUNCE_MS)
            }
        }
    }

    fun update(id: Long, labelKey: String, note: String?) {
        _pins.value = _pins.value.map { if (it.id == id) it.copy(labelKey = labelKey, note = note) else it }
        io.execute { db.updatePin(id, labelKey, note) }
    }

    fun delete(id: Long) {
        _pins.value = _pins.value.filter { it.id != id }
        io.execute { db.deletePin(id) }
    }

    fun byId(id: Long): Pin? = _pins.value.firstOrNull { it.id == id }

    /** The pins dropped on one walk, in the order they were placed. */
    fun forWalk(sessionId: Long): List<Pin> = _pins.value.filter { it.sessionId == sessionId }.sortedBy { it.time }

    /** How long a new pin's drop-in lasts. */
    const val BOUNCE_MS = 600L
}

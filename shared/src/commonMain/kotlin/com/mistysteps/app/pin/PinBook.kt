package com.mistysteps.app.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the pins are kept on the device; Android has its trail database, iOS its SQLite file. */
interface PinStorage {
    /** Returns the new pin's id. */
    fun insertPin(p: Pin): Long

    fun updatePin(id: Long, labelKey: String, note: String?)

    fun deletePin(id: Long)

    /** Newest first. */
    fun loadPins(): List<Pin>
}

/**
 * Your pins, in memory for drawing and written to [storage] as they change. Main thread only (small writes, done at once).
 * [onChanged] tells the map to draw the pins again.
 */
class PinBook(private val storage: PinStorage) {
    private val _pins = MutableStateFlow(storage.loadPins())

    /** Every pin, newest first. */
    val pins: StateFlow<List<Pin>> = _pins

    var onChanged: (() -> Unit)? = null

    fun add(lat: Double, lon: Double, labelKey: String, note: String?, sessionId: Long?, nowMs: Long): Pin {
        val draft = Pin(0, lat, lon, nowMs, labelKey, note, sessionId)
        val pin = draft.copy(id = storage.insertPin(draft))
        _pins.value = listOf(pin) + _pins.value
        onChanged?.invoke()
        return pin
    }

    fun update(id: Long, labelKey: String, note: String?) {
        storage.updatePin(id, labelKey, note)
        _pins.value = _pins.value.map { if (it.id == id) it.copy(labelKey = labelKey, note = note) else it }
        onChanged?.invoke()
    }

    fun delete(id: Long) {
        storage.deletePin(id)
        _pins.value = _pins.value.filter { it.id != id }
        onChanged?.invoke()
    }

    /** Reads every pin from the storage again (after an import). */
    fun reload() {
        _pins.value = storage.loadPins()
        onChanged?.invoke()
    }

    fun byId(id: Long): Pin? = _pins.value.firstOrNull { it.id == id }

    /** The pins dropped on one walk, in the order they were placed. */
    fun forWalk(sessionId: Long): List<Pin> = _pins.value.filter { it.sessionId == sessionId }.sortedBy { it.time }
}

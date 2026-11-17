package com.mistysteps.app.stats

/** A set of primitive longs (no boxing), for the millions of ~10 m cells a big trail clears. */
class LongHashSet(initialCapacity: Int = 16) {
    private var keys = LongArray(powerOfTwoAtLeast(initialCapacity * 2))
    private var hasZero = false // 0 marks an empty slot, so it is kept apart
    var size = 0
        private set

    private fun slot(key: Long, mask: Int): Int {
        var h = key * -7046029254386353131L
        h = h xor (h ushr 32)
        return h.toInt() and mask
    }

    fun add(key: Long): Boolean {
        if (key == 0L) {
            if (hasZero) return false
            hasZero = true
            size++
            return true
        }
        if ((size + 1) * 2 > keys.size) grow()
        val mask = keys.size - 1
        var i = slot(key, mask)
        while (true) {
            val k = keys[i]
            if (k == key) return false
            if (k == 0L) {
                keys[i] = key
                size++
                return true
            }
            i = (i + 1) and mask
        }
    }

    operator fun contains(key: Long): Boolean {
        if (key == 0L) return hasZero
        val mask = keys.size - 1
        var i = slot(key, mask)
        while (true) {
            val k = keys[i]
            if (k == key) return true
            if (k == 0L) return false
            i = (i + 1) and mask
        }
    }

    inline fun forEach(action: (Long) -> Unit) {
        if (containsZero()) action(0L)
        for (k in rawKeys()) if (k != 0L) action(k)
    }

    @PublishedApi internal fun containsZero(): Boolean = hasZero

    @PublishedApi internal fun rawKeys(): LongArray = keys

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2)
        val mask = keys.size - 1
        for (k in old) {
            if (k == 0L) continue
            var i = slot(k, mask)
            while (keys[i] != 0L) i = (i + 1) and mask
            keys[i] = k
        }
    }

    private companion object {
        fun powerOfTwoAtLeast(n: Int): Int {
            var p = 16
            while (p < n) p *= 2
            return p
        }
    }
}

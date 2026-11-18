package com.mistysteps.app.character

/** The rules for the name you give a character: short, tidy, and never an empty one. */
object CharacterNames {
    const val MAX_LENGTH = 20

    /**
     * [raw] trimmed, with runs of spaces and line breaks collapsed to one space, control characters dropped and at most
     * [MAX_LENGTH] characters kept (whole characters, never half of one). Null when nothing is left: that means "no name",
     * and the character shows its species name again.
     */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        val out = StringBuilder()
        var count = 0
        var pendingSpace = false
        var i = 0
        while (i < raw.length && count < MAX_LENGTH) {
            val c = raw[i]
            // A surrogate pair is one character to the user: keep both halves together.
            val pair = c.isHighSurrogate() && i + 1 < raw.length && raw[i + 1].isLowSurrogate()
            i += if (pair) 2 else 1
            when {
                !pair && c.isWhitespace() -> pendingSpace = out.isNotEmpty()
                !pair && c.isISOControl() -> Unit
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        count++
                        pendingSpace = false
                        if (count >= MAX_LENGTH) break
                    }
                    out.append(c)
                    if (pair) out.append(raw[i - 1])
                    count++
                }
            }
        }
        return out.toString().ifEmpty { null }
    }
}

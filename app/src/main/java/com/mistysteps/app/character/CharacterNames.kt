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
            val cp = raw.codePointAt(i)
            i += java.lang.Character.charCount(cp)
            when {
                java.lang.Character.isWhitespace(cp) -> pendingSpace = out.isNotEmpty()
                java.lang.Character.isISOControl(cp) -> Unit
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        count++
                        pendingSpace = false
                        if (count >= MAX_LENGTH) break
                    }
                    out.appendCodePoint(cp)
                    count++
                }
            }
        }
        return out.toString().ifEmpty { null }
    }
}

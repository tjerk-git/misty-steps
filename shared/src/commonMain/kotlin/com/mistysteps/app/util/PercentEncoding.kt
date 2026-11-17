package com.mistysteps.app.util

/** UTF-8 percent-encoding for a URL query value (what Android's Uri.encode does): letters, digits and `-_.~!'()*` stay. */
fun percentEncode(s: String): String {
    val out = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val plain = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c.toChar() in "-_.~!'()*"
        if (plain) out.append(c.toChar()) else out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
    return out.toString()
}

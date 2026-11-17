package com.mistysteps.app.stats

/**
 * The anonymous open count: when to send it and what it says. Nothing here knows who the person is, and the message holds
 * nothing but the app version and the platform. Off until the person agrees (see [Analytics]).
 */
object OpenPing {
    const val URL = "https://stats.potloodgum.com/v1/open"

    /** At most one ping this often, so a process killed and restarted does not count twice. */
    const val MIN_GAP_MS = 30L * 60 * 1000

    /** The same pattern the Worker checks: `1.4.0`, or `1.4.0-beta`. */
    private val VERSION = Regex("""\d+\.\d+\.\d+(-[a-z]+)?""")

    fun shouldPing(optedIn: Boolean, release: Boolean, lastPingAt: Long, now: Long): Boolean =
        optedIn && release && now - lastPingAt >= MIN_GAP_MS

    /** The exact body that is sent: `{"v":"1.4.0","os":"android"}`. */
    fun payload(versionName: String): String {
        require(VERSION.matches(versionName)) { "version not accepted by the server: $versionName" }
        return """{"v":"$versionName","os":"android"}"""
    }
}

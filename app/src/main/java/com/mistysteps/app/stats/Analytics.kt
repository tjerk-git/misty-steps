package com.mistysteps.app.stats

import android.content.Context
import com.mistysteps.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * The anonymous open count, off until the person says yes. Keeps the answer in the `misty` preferences and sends the ping
 * ([OpenPing]) once per app start when it is allowed: on a background thread, with short timeouts, no retry and no queue.
 * A lost ping is simply a lost count.
 */
object Analytics {
    private const val PREFS = "misty"
    private const val KEY_OPT_IN = "analytics_opt_in"
    private const val KEY_LAST_PING = "analytics_last_ping"
    private const val TIMEOUT_MS = 5_000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOptedIn(context: Context): Boolean = prefs(context).getBoolean(KEY_OPT_IN, false)

    /** Switching on does not send anything by itself; the next app start does. */
    fun setOptedIn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPT_IN, value).apply()
    }

    /** Called once at app start. Sends at most one ping per [OpenPing.MIN_GAP_MS], and only when opted in on a release build. */
    fun pingIfAllowed(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (!OpenPing.shouldPing(isOptedIn(context), !BuildConfig.DEBUG, prefs.getLong(KEY_LAST_PING, 0L), now)) return
        val body = try {
            OpenPing.payload(BuildConfig.VERSION_NAME)
        } catch (e: IllegalArgumentException) {
            return // a version name the server would refuse: send nothing
        }
        // Remembered before sending, so a slow or failing request cannot cause a second ping.
        prefs.edit().putLong(KEY_LAST_PING, now).apply()
        Thread({ send(body) }, "open-ping").start()
    }

    private fun send(body: String) {
        try {
            val connection = URL(OpenPing.URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.responseCode // read the answer so the request completes; its content is not used
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Offline or the server is down: nothing to do.
        }
    }
}

package com.mistysteps.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R

/** What goes below the message of a feedback email: the app version and the phone, visible so people can remove it. */
fun feedbackDetails(): String =
    "\n\n\n—\nMisty Steps ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n"

/**
 * Opens the mail app with a feedback email to [FEEDBACK_EMAIL]. The app version and phone model
 * are filled in below the message, visible, so people can see (and remove) what they send.
 */
fun sendFeedback(context: Context) {
    val body = feedbackDetails()
    val uri = Uri.parse(
        "mailto:$FEEDBACK_EMAIL?subject=" + Uri.encode("Misty Steps feedback") + "&body=" + Uri.encode(body)
    )
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_mail_app, FEEDBACK_EMAIL), Toast.LENGTH_LONG).show()
    }
}

/** Opens a web page in the browser. */
fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}

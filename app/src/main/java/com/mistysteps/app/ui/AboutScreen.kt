package com.mistysteps.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R
import com.mistysteps.app.character.Characters

const val DONATE_URL = "https://buymeacoffee.com/tjerk"
const val STUDIO_URL = "https://potloodgum.com"
const val FEEDBACK_EMAIL = "debug@potloodgum.com"

/** Who made the app and why, with a way to support it. */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Text(
                    stringResource(R.string.about_title),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(20.dp))

            // Your wanderer, waving you through.
            val character by Characters.selected.collectAsState()
            val frames = remember(character) { character?.let { SpriteFrames(it) } }
            val ticker = rememberTicker(stepMs = Characters.WALK_FRAME_MS)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .drawBehind { frames?.let { drawSprite(it.at(walking = true, timeMs = ticker.value)) } },
            )
            Spacer(Modifier.height(20.dp))

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.about_maker), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.about_why), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.about_promise), color = Dusk, fontSize = 14.sp, lineHeight = 20.sp)
                    TextButton(onClick = { open(context, STUDIO_URL) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("potloodgum.com", color = Ember)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.about_donate_text), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { open(context, DONATE_URL) },
                        colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.about_donate_button), fontSize = 16.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.about_feedback_text), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { sendFeedback(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                    ) { Text(stringResource(R.string.about_feedback_button), fontSize = 16.sp) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.about_credits), color = Dusk, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", color = Dusk, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Opens the mail app with a feedback email to [FEEDBACK_EMAIL]. The app version and phone model
 * are filled in below the message, visible, so people can see (and remove) what they send.
 */
fun sendFeedback(context: Context) {
    val body = "\n\n\n—\nMisty Steps ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n"
    val uri = Uri.parse(
        "mailto:$FEEDBACK_EMAIL?subject=" + Uri.encode("Misty Steps feedback") + "&body=" + Uri.encode(body)
    )
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No mail app found. Write to $FEEDBACK_EMAIL", Toast.LENGTH_LONG).show()
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

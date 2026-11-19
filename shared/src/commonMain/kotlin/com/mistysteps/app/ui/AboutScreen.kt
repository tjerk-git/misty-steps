package com.mistysteps.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.mistysteps.app.util.percentEncode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.about_credits
import com.mistysteps.app.resources.about_feedback_button
import com.mistysteps.app.resources.about_feedback_text
import com.mistysteps.app.resources.no_mail_app
import com.mistysteps.app.resources.about_maker
import com.mistysteps.app.resources.about_promise
import com.mistysteps.app.resources.about_title
import com.mistysteps.app.resources.about_why
import com.mistysteps.app.resources.back
import com.mistysteps.app.resources.version_plain
import org.jetbrains.compose.resources.stringResource
import com.mistysteps.app.character.Characters

const val STUDIO_URL = "https://potloodgum.com"
const val FEEDBACK_EMAIL = "debug@potloodgum.com"

/**
 * Who made the app and why. The platform says which [versionName] this is and what to
 * put below the message of a feedback email ([feedbackDetails]: version, phone model; shown, so people can remove it).
 */
@Composable
fun AboutScreen(versionName: String, feedbackDetails: String, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val links = LocalUriHandler.current
    var mailFailed by remember { mutableStateOf(false) }
    fun open(url: String) { try { links.openUri(url) } catch (e: Exception) { /* no app for it: nothing to do */ } }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), tint = Ink)
                }
                Text(
                    stringResource(Res.string.about_title),
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
                    Text(stringResource(Res.string.about_maker), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.about_why), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.about_promise), color = Dusk, fontSize = 14.sp, lineHeight = 20.sp)
                    TextButton(onClick = { open(STUDIO_URL) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("potloodgum.com", color = Ember)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))


            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.about_feedback_text), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val mail = "mailto:$FEEDBACK_EMAIL?subject=" + percentEncode("Misty Steps feedback") +
                                "&body=" + percentEncode(feedbackDetails)
                            mailFailed = try { links.openUri(mail); false } catch (e: Exception) { true }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                    ) { Text(stringResource(Res.string.about_feedback_button), fontSize = 16.sp) }
                    if (mailFailed) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.no_mail_app, FEEDBACK_EMAIL), color = Dusk, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(Res.string.about_credits), color = Dusk, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.version_plain, versionName), color = Dusk, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

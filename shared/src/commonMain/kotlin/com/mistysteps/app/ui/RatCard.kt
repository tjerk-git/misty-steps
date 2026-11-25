package com.mistysteps.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mistysteps.app.announce.RatAnnouncement
import com.mistysteps.app.character.Characters
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.rat_card_body
import com.mistysteps.app.resources.rat_card_later
import com.mistysteps.app.resources.rat_card_title
import com.mistysteps.app.resources.rat_card_try
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time Christmas card that offers the Santa Rat: the rat walking, a greeting, and two buttons. A tap outside the card
 * counts as "Not now". Whatever is chosen is reported once, however fast the taps come. Without a rat in the character list
 * it closes itself at once ([onLater]).
 */
@Composable
fun RatCard(onTry: () -> Unit, onLater: () -> Unit) {
    val rat = remember { Characters.all.firstOrNull { it.id == RatAnnouncement.CHARACTER_ID } }
    if (rat == null) {
        LaunchedEffect(Unit) { onLater() }
        return
    }
    val frames = remember(rat) { SpriteFrames(rat) }
    val ticker = rememberTicker()
    var handled by remember { mutableStateOf(false) }
    fun once(action: () -> Unit) {
        if (!handled) {
            handled = true
            action()
        }
    }

    Dialog(onDismissRequest = { once(onLater) }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(96.dp)) { drawSprite(frames.at(walking = true, timeMs = ticker.value)) }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.rat_card_title),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.rat_card_body),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = Dusk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { once(onTry) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent)) { Text(stringResource(Res.string.rat_card_try)) }
                TextButton(onClick = { once(onLater) }) { Text(stringResource(Res.string.rat_card_later), color = Dusk) }
            }
        }
    }
}

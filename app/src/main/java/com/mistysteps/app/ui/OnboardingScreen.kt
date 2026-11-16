package com.mistysteps.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.R
import com.mistysteps.app.character.Characters

private const val PAGES = 3

/**
 * Three short screens for a new install: what the app is, how it works and the privacy promise.
 * [onDone] runs after the last page ("Choose provinces"), [onSkip] from the Skip link; the caller shows the
 * province picker after either.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onSkip: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { if (page > 0) page-- else onSkip() }

    val titles = listOf(R.string.onboarding_1_title, R.string.onboarding_2_title, R.string.onboarding_3_title)
    val bodies = listOf(R.string.onboarding_1_body, R.string.onboarding_2_body, R.string.onboarding_3_body)

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip), color = Dusk) }
            }
            Spacer(Modifier.weight(1f))
            Illustration(page)
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(titles[page]),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(bodies[page]),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = Dusk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PAGES) { i ->
                    Box(Modifier.size(8.dp).background(if (i == page) Ink else Color(0xFFB9C1D6), CircleShape))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (page < PAGES - 1) page++ else onDone() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            ) {
                Text(
                    stringResource(if (page < PAGES - 1) R.string.onboarding_next else R.string.onboarding_choose),
                    fontSize = 16.sp,
                )
            }
        }
    }
}

/** Your wanderer: standing in a patch of mist, then walking inside the clearing ring, then standing again. */
@Composable
private fun Illustration(page: Int) {
    val character by Characters.selected.collectAsState()
    val frames = remember(character) { character?.let { SpriteFrames(it) } }
    val ticker = rememberTicker(stepMs = Characters.WALK_FRAME_MS)
    Box(
        Modifier
            .size(200.dp)
            .drawBehind {
                // Mist behind the character.
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xE6FFFFFF), Color(0x00FFFFFF)), center = center, radius = size.minDimension / 2),
                    radius = size.minDimension / 2,
                )
                // The cleared ring, as on the map, on the page about clearing.
                if (page == 1) {
                    drawCircle(Ember.copy(alpha = 0.18f), radius = size.minDimension * 0.34f, center = Offset(center.x, center.y))
                    drawCircle(Ember.copy(alpha = 0.7f), radius = size.minDimension * 0.34f, center = center, style = Stroke(width = 3.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .drawBehind { frames?.let { drawSprite(it.at(walking = page == 1, timeMs = ticker.value)) } },
        )
    }
}

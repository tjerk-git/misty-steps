package com.mistysteps.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.consent_body
import com.mistysteps.app.resources.consent_no
import com.mistysteps.app.resources.consent_title
import com.mistysteps.app.resources.consent_yes
import org.jetbrains.compose.resources.stringResource

/**
 * Asks once whether the app may count, anonymously, how often it is opened. Both answers look and weigh the same; Back
 * counts as "No thanks". [onAnswer] gets what the person chose.
 */
@Composable
fun ConsentScreen(onAnswer: (allowed: Boolean) -> Unit) {
    PlatformBackHandler { onAnswer(false) }
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
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.consent_title),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.consent_body),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = Dusk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            val colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)
            Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth(), colors = colors) {
                Text(stringResource(Res.string.consent_yes), fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth(), colors = colors) {
                Text(stringResource(Res.string.consent_no), fontSize = 16.sp)
            }
        }
    }
}

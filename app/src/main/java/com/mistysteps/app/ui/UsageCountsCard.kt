package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R
import com.mistysteps.app.stats.Analytics
import com.mistysteps.app.stats.OpenPing

/**
 * The anonymous open count, as a switch: off until the person turned it on, and they can turn it off again at any time. The
 * switch never sends anything by itself; "What is sent" shows the exact message.
 */
@Composable
fun UsageCountsCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Analytics.isOptedIn(context)) }
    var showSent by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.usage_title).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.usage_body),
                    color = Ink,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = on,
                    onCheckedChange = {
                        on = it
                        Analytics.setOptedIn(context, it)
                        Analytics.markAsked(context)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink),
                )
            }
            TextButton(onClick = { showSent = true }) { Text(stringResource(R.string.usage_what), color = Ember) }
        }
    }

    if (showSent) {
        AlertDialog(
            onDismissRequest = { showSent = false },
            title = { Text(stringResource(R.string.usage_what)) },
            text = {
                Column {
                    Text(
                        OpenPing.payload(BuildConfig.VERSION_NAME.substringBefore("-")),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.usage_sent_note))
                }
            },
            confirmButton = { TextButton(onClick = { showSent = false }) { Text(stringResource(R.string.usage_close)) } },
        )
    }
}

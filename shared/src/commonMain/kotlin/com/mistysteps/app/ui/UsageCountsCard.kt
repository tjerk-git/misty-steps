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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.usage_body
import com.mistysteps.app.resources.usage_close
import com.mistysteps.app.resources.usage_sent_note
import com.mistysteps.app.resources.usage_title
import com.mistysteps.app.resources.usage_what
import org.jetbrains.compose.resources.stringResource
import com.mistysteps.app.stats.OpenPing

/**
 * The anonymous open count, as a switch: off until the person turned it on, and they can turn it off again at any time. The
 * switch never sends anything by itself; "What is sent" shows the exact message. The platform keeps the choice
 * ([optedIn], [onOptedInChange]) and says which app version is in the message.
 */
@Composable
fun UsageCountsCard(optedIn: Boolean, onOptedInChange: (Boolean) -> Unit, versionName: String) {
    var showSent by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(Res.string.usage_title).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.usage_body),
                    color = Ink,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = optedIn,
                    onCheckedChange = onOptedInChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink),
                )
            }
            TextButton(onClick = { showSent = true }) { Text(stringResource(Res.string.usage_what), color = Ember) }
        }
    }

    if (showSent) {
        AlertDialog(
            onDismissRequest = { showSent = false },
            title = { Text(stringResource(Res.string.usage_what)) },
            text = {
                Column {
                    Text(
                        OpenPing.payload(versionName.substringBefore("-")),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.usage_sent_note))
                }
            },
            confirmButton = { TextButton(onClick = { showSent = false }) { Text(stringResource(Res.string.usage_close)) } },
        )
    }
}

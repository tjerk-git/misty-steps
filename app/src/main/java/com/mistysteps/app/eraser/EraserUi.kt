package com.mistysteps.app.eraser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mistysteps.app.R
import com.mistysteps.app.ui.Dusk
import com.mistysteps.app.ui.Ember
import com.mistysteps.app.ui.Ink

/** The round button on the map that picks the eraser up; filled in Ember while it is in hand. */
@Composable
fun EraserButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (active) Ember else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else Ink,
        shape = CircleShape,
    ) {
        Icon(painterResource(R.drawable.ic_eraser), contentDescription = stringResource(R.string.eraser_button))
    }
}

/** Shown while the eraser is in hand: take back a stroke, or put it down. */
@Composable
fun EraserBar(canUndo: Boolean, onUndo: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onUndo, enabled = canUndo) { Text(stringResource(R.string.eraser_undo), color = if (canUndo) Ink else Dusk) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.eraser_done), color = Ember) }
        }
    }
}

/** What you see when you tap the eraser without owning it: the pitch, the price, Buy and Not now. */
@Composable
fun EraserSheet(priceText: String?, tasteUsed: Boolean, onBuy: () -> Unit, onNotNow: () -> Unit) {
    Dialog(onDismissRequest = onNotNow) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, tint = Ember)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.eraser_sheet_title),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = Ink,
                )
                if (tasteUsed) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.eraser_taste_used), fontSize = 14.sp, color = Ember)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.eraser_sheet_body), fontSize = 15.sp, lineHeight = 21.sp, color = Ink)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onNotNow) { Text(stringResource(R.string.eraser_sheet_not_now), color = Dusk) }
                    TextButton(onClick = onBuy, enabled = priceText != null) {
                        Text(
                            priceText?.let { stringResource(R.string.eraser_sheet_buy, it) } ?: stringResource(R.string.eraser_unavailable),
                            color = Ember,
                        )
                    }
                }
            }
        }
    }
}

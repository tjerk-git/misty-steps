package com.mistysteps.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistysteps.app.data.Mercator

/** Proof that Compose code in :shared runs on both platforms; replaced by the real screens as they move over. */
@Composable
fun SharedHello() {
    MistyTheme {
        Surface(color = Frost) {
            Column(Modifier.padding(24.dp)) {
                Text("Misty Steps, shared Compose", color = Ink)
                Text("Leeuwarden x ${Mercator.x(5.7936)}", color = Dusk)
            }
        }
    }
}

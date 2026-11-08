package com.mistysteps.app.ui

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.postcard.PostcardRenderer
import com.mistysteps.app.postcard.PostcardShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A postcard of one walk, shown when a walk ends and when you tap one in the walk log. */
@Composable
fun PostcardScreen(walkId: Long, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val name = remember(walkId) { "misty-steps-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(walkId)) }

    LaunchedEffect(walkId) {
        val walk = withContext(Dispatchers.IO) { FogStore.loadWalk(walkId) }
        if (walk == null || walk.second.isEmpty()) {
            failed = true
            return@LaunchedEffect
        }
        card = try {
            PostcardRenderer.render(context, walk.first, walk.second, Characters.selected.value)
        } catch (e: Exception) {
            failed = true
            null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Color(0xE62E3550)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Your walk",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val bmp = card
                when {
                    bmp != null -> Image(
                        bmp.asImageBitmap(),
                        contentDescription = "Postcard of your walk",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    failed -> Text("Couldn't draw this walk.", color = Color.White)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Painting your postcard…", color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val bmp = card
                OutlinedButton(
                    onClick = {
                        if (bmp == null) return@OutlinedButton
                        scope.launch {
                            val ok = PostcardShare.saveToGallery(context, bmp, name)
                            val msg = when {
                                ok -> "Saved to Pictures › Misty Steps"
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Use Share to save it on this phone"
                                else -> "Couldn't save the postcard"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Save") }
                Button(
                    onClick = { if (bmp != null) scope.launch { PostcardShare.share(context, bmp, name) } },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                ) { Text("Share") }
            }
            TextButton(onClick = onClose) { Text("Back to the mist", color = Color.White) }
        }
    }
}

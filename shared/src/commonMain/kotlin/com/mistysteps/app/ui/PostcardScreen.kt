package com.mistysteps.app.ui

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
import androidx.compose.ui.graphics.ImageBitmap
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.postcard_back
import com.mistysteps.app.resources.postcard_description
import com.mistysteps.app.resources.postcard_failed
import com.mistysteps.app.resources.postcard_painting
import com.mistysteps.app.resources.postcard_title
import com.mistysteps.app.resources.save
import com.mistysteps.app.resources.share
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * What the postcard screen needs from the platform: painting the picture, saving it to the photos and sharing it.
 * Painting uses the map and trail of the walk, so it stays platform code.
 */
interface PostcardHost {
    /** The picture of walk [walkId], or null when there is no such walk, it is empty, or painting failed. */
    suspend fun render(walkId: Long): ImageBitmap?

    /** A file name for the picture of walk [walkId], without extension. */
    fun fileName(walkId: Long): String

    /** Saves [card] to the photos and returns what to tell the person. */
    suspend fun save(card: ImageBitmap, name: String): String

    suspend fun share(card: ImageBitmap, name: String)
}

/** A postcard of one walk, shown when a walk ends and when you tap one in the walk log. */
@Composable
fun PostcardScreen(walkId: Long, host: PostcardHost, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val name = remember(walkId) { host.fileName(walkId) }

    LaunchedEffect(walkId) {
        val picture = try { host.render(walkId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        if (picture == null) failed = true else card = picture
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
                stringResource(Res.string.postcard_title),
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
                        bmp,
                        contentDescription = stringResource(Res.string.postcard_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    failed -> Text(stringResource(Res.string.postcard_failed), color = Color.White)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(Res.string.postcard_painting), color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val bmp = card
                OutlinedButton(
                    onClick = {
                        if (bmp == null) return@OutlinedButton
                        scope.launch { message = host.save(bmp, name) }
                    },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text(stringResource(Res.string.save)) }
                Button(
                    onClick = { if (bmp != null) scope.launch { host.share(bmp, name) } },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                ) { Text(stringResource(Res.string.share)) }
            }
            message?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            TextButton(onClick = onClose) { Text(stringResource(Res.string.postcard_back), color = Color.White) }
        }
    }
}

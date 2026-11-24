package com.mistysteps.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.R
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.postcard.PostcardRenderer
import com.mistysteps.app.postcard.PostcardShare
import com.mistysteps.app.stats.AndroidStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The postcard screen's platform side on Android: [PostcardRenderer] paints, [PostcardShare] saves and shares. */
private class AndroidPostcardHost(private val context: Context) : PostcardHost {
    override suspend fun render(walkId: Long): ImageBitmap? {
        val walk = withContext(Dispatchers.IO) { FogStore.loadWalk(walkId) }
        if (walk == null || walk.second.isEmpty()) return null
        return PostcardRenderer.render(context, walk.first, walk.second, Characters.selected.value, PinStore.forWalk(walkId)).asImageBitmap()
    }

    override suspend fun walkIds(startId: Long): List<Long> {
        val sessions = AndroidStatsSource(context).sessions()
        val withPins = PinStore.pins.value.mapNotNull { it.sessionId }.toSet()
        return PostcardPaging.walkIds(sessions, withPins, startId)
    }

    override fun fileName(walkId: Long): String =
        "misty-steps-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(walkId))

    override suspend fun save(card: ImageBitmap, name: String): String {
        val ok = PostcardShare.saveToGallery(context, card.asAndroidBitmap(), name)
        return when {
            ok -> context.getString(R.string.postcard_saved)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> context.getString(R.string.postcard_use_share)
            else -> context.getString(R.string.postcard_save_failed)
        }
    }

    override suspend fun share(card: ImageBitmap, name: String) = PostcardShare.share(context, card.asAndroidBitmap(), name)
}

@Composable
fun rememberPostcardHost(): PostcardHost {
    val context = LocalContext.current
    return remember { AndroidPostcardHost(context) }
}

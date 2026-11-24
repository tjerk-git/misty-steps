package com.mistysteps.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ComposeUIViewController
import com.mistysteps.app.character.Characters
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.duration_h_min
import com.mistysteps.app.resources.duration_min
import com.mistysteps.app.resources.duration_under_minute
import com.mistysteps.app.resources.osm_credit
import com.mistysteps.app.resources.postcard_save_failed
import com.mistysteps.app.resources.postcard_saved_photos
import com.mistysteps.app.resources.postcard_with
import com.mistysteps.app.resources.walk_new_mist
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.UIKit.UIViewController
import platform.posix.memcpy

/**
 * What the postcard screen needs from Swift: painting the picture (a PNG), saving it to Photos and sharing it. Painting
 * needs the map snapshotter and the stored walk, so it stays in the iOS app. Callbacks, not suspend functions.
 */
interface PostcardPainter {
    /** The postcard of walk [walkId] as PNG data, or null when there is no such walk or painting failed. */
    fun paint(walkId: Long, done: (NSData?) -> Unit)

    fun fileName(walkId: Long): String

    fun saveToPhotos(png: NSData, done: (Boolean) -> Unit)

    fun share(png: NSData, name: String)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private class IosPostcardHost(private val painter: PostcardPainter) : PostcardHost {
    private var png: NSData? = null

    override suspend fun render(walkId: Long): ImageBitmap? = suspendCoroutine { cont ->
        painter.paint(walkId) { data ->
            png = data
            cont.resume(data?.let { runCatching { Image.makeFromEncoded(it.toByteArray()).toComposeImageBitmap() }.getOrNull() })
        }
    }

    override fun fileName(walkId: Long): String = painter.fileName(walkId)

    override suspend fun save(card: ImageBitmap, name: String): String {
        val data = png ?: return getString(Res.string.postcard_save_failed)
        val ok = suspendCoroutine { cont -> painter.saveToPhotos(data) { cont.resume(it) } }
        return getString(if (ok) Res.string.postcard_saved_photos else Res.string.postcard_save_failed)
    }

    override suspend fun share(card: ImageBitmap, name: String) {
        png?.let { painter.share(it, name) }
    }
}

/** The postcard page for walk [walkId], painted by [painter]. */
fun postcardViewController(walkId: Long, painter: PostcardPainter, onClose: () -> Unit): UIViewController =
    ComposeUIViewController {
        MistyTheme {
            val host = remember { IosPostcardHost(painter) }
            PostcardScreen(walkId, host, onClose)
        }
    }

/** The texts Swift draws on the postcard, from the shared (translated) strings and number formatting. */
object PostcardTexts {
    fun credit(): String = runBlocking { getString(Res.string.osm_credit) }

    /** "with Misty": only used when you named your character. */
    fun with(name: String): String = runBlocking { getString(Res.string.postcard_with, name) }

    /** "That is about 31,000 Trail Cat steps.": null for an unknown character or a walk without steps. */
    fun steps(characterId: String, customName: String?, distanceM: Double): String? = runBlocking {
        Characters.all.firstOrNull { it.id == characterId }?.let { postcardStepsLine(it, customName, distanceM) }
    }

    /** "1,6 km", "17 min", "13,2 ha new mist": the walk's numbers in order. */
    fun parts(distanceM: Double, durationMs: Long?, newAreaM2: Double?): List<String> = runBlocking {
        val words = DurationText(
            getString(Res.string.duration_under_minute), getString(Res.string.duration_min), getString(Res.string.duration_h_min),
        )
        buildList {
            add(formatDistance(distanceM))
            durationMs?.let { add(formatDuration(it, words)) }
            newAreaM2?.let { add(getString(Res.string.walk_new_mist, formatArea(it))) }
        }
    }
}

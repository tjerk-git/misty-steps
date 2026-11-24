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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * What the postcard screen needs from the platform: painting the picture, saving it to the photos and sharing it.
 * Painting uses the map and trail of the walk, so it stays platform code.
 */
interface PostcardHost {
    /** The picture of walk [walkId], or null when there is no such walk, it is empty, or painting failed. */
    suspend fun render(walkId: Long): ImageBitmap?

    /** The walks you can swipe through, newest first, always including [startId] (see [PostcardPaging]). */
    suspend fun walkIds(startId: Long): List<Long>

    /** A file name for the picture of walk [walkId], without extension. */
    fun fileName(walkId: Long): String

    /** Saves [card] to the photos and returns what to tell the person. */
    suspend fun save(card: ImageBitmap, name: String): String

    suspend fun share(card: ImageBitmap, name: String)
}

/**
 * The postcard of the walk you opened, which you can swipe left and right to go through your other walks (newest first).
 * It is shown when a walk ends and when you tap one in the walk log; leaving returns to where you came from.
 */
@Composable
fun PostcardScreen(walkId: Long, host: PostcardHost, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    var ids by remember(walkId) { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(walkId) {
        ids = try { host.walkIds(walkId) } catch (e: CancellationException) { throw e } catch (e: Exception) { listOf(walkId) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Color(0xE62E3550)),
    ) {
        val list = ids
        if (list == null) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.postcard_painting), color = Color.White)
            }
        } else {
            PostcardPages(list, walkId, host, onClose)
        }
    }
}

@Composable
private fun PostcardPages(ids: List<Long>, startId: Long, host: PostcardHost, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { ids.size }
    // Painting needs the map, so one picture at a time; only the page you are on and its neighbours stay in memory.
    val paintLock = remember { Mutex() }
    val cards = remember { mutableStateMapOf<Long, ImageBitmap>() }
    val failed = remember { mutableStateMapOf<Long, Boolean>() }
    var message by remember { mutableStateOf<String?>(null) }
    val page = pager.currentPage.coerceIn(0, ids.lastIndex)
    val currentId = ids[page]
    val card = cards[currentId]
    val name = remember(currentId) { host.fileName(currentId) }

    LaunchedEffect(pager.settledPage) {
        val settled = pager.settledPage.coerceIn(0, ids.lastIndex)
        val keep = ids.subList(max(0, settled - 1), min(ids.size, settled + 2)).toSet()
        cards.keys.filter { it !in keep }.forEach { cards.remove(it) }
        failed.keys.filter { it !in keep }.forEach { failed.remove(it) }
        message = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.postcard_title),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = Color.White,
        )
        if (ids.size > 1) {
            Text("${page + 1} / ${ids.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 16.dp) { index ->
            PostcardPage(ids[index], host, paintLock, cards, failed)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val bmp = card ?: return@OutlinedButton
                    scope.launch { message = host.save(bmp, name) }
                },
                enabled = card != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text(stringResource(Res.string.save)) }
            Button(
                onClick = { card?.let { scope.launch { host.share(it, name) } } },
                enabled = card != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
            ) { Text(stringResource(Res.string.share)) }
        }
        message?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        TextButton(onClick = onClose) { Text(stringResource(Res.string.postcard_back), color = Color.White) }
    }
}

/** One walk's postcard: paints itself when the page is reached, and shows the picture, the failure or the wait. */
@Composable
private fun PostcardPage(
    walkId: Long,
    host: PostcardHost,
    paintLock: Mutex,
    cards: MutableMap<Long, ImageBitmap>,
    failed: MutableMap<Long, Boolean>,
) {
    LaunchedEffect(walkId) {
        if (cards[walkId] != null || failed[walkId] == true) return@LaunchedEffect
        val picture = paintLock.withLock {
            try { host.render(walkId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        }
        if (picture == null) failed[walkId] = true else cards[walkId] = picture
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = cards[walkId]
        when {
            bmp != null -> Image(
                bmp,
                contentDescription = stringResource(Res.string.postcard_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                    .shadow(12.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp)),
            )
            failed[walkId] == true -> Text(stringResource(Res.string.postcard_failed), color = Color.White)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.postcard_painting), color = Color.White)
            }
        }
    }
}

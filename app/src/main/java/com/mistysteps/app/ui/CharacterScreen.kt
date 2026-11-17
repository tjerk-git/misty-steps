package com.mistysteps.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.mistysteps.app.character.CharacterNames
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mistysteps.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.character.Character
import com.mistysteps.app.character.Characters
import kotlinx.coroutines.delay

/**
 * A full-screen page that scrolls. Content stops at the status bar (so it never runs under the clock)
 * but may scroll behind the navigation bar at the bottom.
 */
@Composable
fun Modifier.scrollingPage(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    .verticalScroll(rememberScrollState())
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

/** Full-screen pages sit on top of the map: stop taps falling through to the buttons underneath. */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
    }
}

/** Milliseconds since the composable appeared, ticking every [stepMs]. Read it in draw lambdas only. */
@Composable
fun rememberTicker(stepMs: Long = 70): State<Long> = produceState(0L) {
    val start = System.currentTimeMillis()
    while (true) {
        delay(stepMs)
        value = System.currentTimeMillis() - start
    }
}

/** Frames of one character, converted for Compose once. */
class SpriteFrames(c: Character) {
    val walk: List<ImageBitmap> = c.walk.map { it.asImageBitmap() }
    val idle: List<ImageBitmap> = c.idle.map { it.asImageBitmap() }

    fun at(walking: Boolean, timeMs: Long): ImageBitmap =
        if (walking) walk[((timeMs / Characters.WALK_FRAME_MS) % 4).toInt()]
        else idle[((timeMs / Characters.IDLE_FRAME_MS) % idle.size).toInt()]
}

/** Draws a crisp, nearest-neighbour scaled sprite filling the largest square that fits. */
fun DrawScope.drawSprite(frame: ImageBitmap) {
    val side = minOf(size.width, size.height).toInt()
    val left = ((size.width - side) / 2).toInt()
    val top = ((size.height - side) / 2).toInt()
    drawImage(
        frame,
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(side, side),
        filterQuality = FilterQuality.None,
    )
}

@Composable
fun CharacterScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val selected by Characters.selected.collectAsState()
    val ticker = rememberTicker()
    val sprites = remember { Characters.all.map { it to SpriteFrames(it) } }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_map), tint = Ink)
                }
                Text(
                    stringResource(R.string.characters_title),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Ink,
                )
            }
            Text(
                stringResource(R.string.characters_sub),
                color = Dusk,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            )

            // A handful of characters: plain rows, no lazy list needed.
            sprites.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (c, frames) ->
                        CharacterCard(
                            character = c,
                            frames = frames,
                            isSelected = selected?.id == c.id,
                            time = { ticker.value },
                            onClick = { Characters.select(c) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: Character,
    frames: SpriteFrames,
    isSelected: Boolean,
    time: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.then(if (isSelected) Modifier.border(3.dp, Ink, shape) else Modifier),
        shape = shape,
        color = Frost,
        shadowElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFE3E8F2), RoundedCornerShape(16.dp))
                    .padding(10.dp)
                    // Time is read here, in the draw phase, so cards don't recompose every tick.
                    .drawBehind { drawSprite(frames.at(walking = isSelected, timeMs = time())) }
            )
            Spacer(Modifier.height(10.dp))
            val species = characterText(character, "name", character.name)
            val custom = Characters.names.collectAsState().value[character.id]
            var renaming by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    custom ?: species,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(onClick = { renaming = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.character_rename_cd, custom ?: species),
                        tint = Dusk,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // With your own name on the card, the species stays visible underneath.
            if (custom != null) Text(species, fontSize = 12.sp, color = Dusk)
            if (renaming) RenameDialog(character.id, species, custom) { renaming = false }
            Text(
                characterText(character, "blurb", character.blurb),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = Dusk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The translated name or blurb of a character, falling back to the English text in the sprite file. */
@Composable
private fun characterText(character: Character, part: String, fallback: String): String {
    val context = LocalContext.current
    val id = remember(character.id, part) {
        context.resources.getIdentifier("character_${character.id}_$part", "string", context.packageName)
    }
    return if (id != 0) stringResource(id) else fallback
}

/** Asks for a name for one character. Saving an empty name takes the name away: the species name shows again. */
@Composable
private fun RenameDialog(id: String, species: String, current: String?, onClose: () -> Unit) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.character_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CharacterNames.MAX_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.character_rename_hint)) },
                placeholder = { Text(species) },
                supportingText = { Text(stringResource(R.string.character_rename_empty, species)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { Characters.setName(id, text); onClose() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

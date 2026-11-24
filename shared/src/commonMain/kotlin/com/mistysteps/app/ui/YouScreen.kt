package com.mistysteps.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.character.Characters
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.back_to_map
import com.mistysteps.app.resources.character_rename_cd
import com.mistysteps.app.resources.maps_title
import com.mistysteps.app.resources.stats_privacy
import com.mistysteps.app.resources.you_about_header
import com.mistysteps.app.resources.you_change_character
import com.mistysteps.app.resources.you_settings_header
import com.mistysteps.app.resources.you_title
import org.jetbrains.compose.resources.stringResource

/** The parts of the "You" page that differ by platform: cards that need files, sharing or downloads, and feedback. */
class YouSlots(
    val mapsCard: @Composable () -> Unit,
    /** The backup card; [onImported] is called after data was added. */
    val backupCard: @Composable (onImported: () -> Unit) -> Unit,
    val usageCard: @Composable () -> Unit,
    val onFeedback: () -> Unit,
)

/**
 * Who you are and how the app is set up: your character on top (change it, give it a name), then the settings cards,
 * then About. The numbers live on the stats page ([StatsScreen]).
 */
@Composable
fun YouScreen(slots: YouSlots, versionName: String, onClose: () -> Unit, onOpenAbout: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    var picking by remember { mutableStateOf(false) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_to_map), tint = Ink)
                }
                Text(
                    stringResource(Res.string.you_title),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(16.dp))
            YourCharacter(onChange = { picking = true })

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.you_settings_header))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.maps_title), color = Dusk, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            slots.mapsCard()
            Spacer(Modifier.height(12.dp))
            slots.backupCard {}
            Spacer(Modifier.height(12.dp))
            slots.usageCard()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.stats_privacy), color = Dusk, fontSize = 12.sp, lineHeight = 16.sp)

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.you_about_header))
            Spacer(Modifier.height(8.dp))
            MadeBy(versionName, onOpenAbout)
            Spacer(Modifier.height(12.dp))
            FeedbackRow(slots.onFeedback)
            Spacer(Modifier.height(16.dp))
        }
        if (picking) CharacterSheet(onClose = { picking = false })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
}

/** Your character walking, its name (yours or the species) with a pencil to rename it, and the way to pick another. */
@Composable
private fun YourCharacter(onChange: () -> Unit) {
    val character by Characters.selected.collectAsState()
    val c = character ?: return
    val frames = remember(c) { SpriteFrames(c) }
    val ticker = rememberTicker()
    val species = characterText(c, "name", c.name)
    val custom = Characters.names.collectAsState().value[c.id]
    var renaming by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(Color(0xFFE3E8F2), RoundedCornerShape(20.dp))
                    .padding(8.dp)
                    // Time is read in the draw phase, so the page does not recompose every tick.
                    .drawBehind { drawSprite(frames.at(walking = true, timeMs = ticker.value)) }
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        custom ?: species,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = { renaming = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(Res.string.character_rename_cd, custom ?: species),
                            tint = Dusk,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (custom != null) Text(species, fontSize = 12.sp, color = Dusk)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onChange) { Text(stringResource(Res.string.you_change_character)) }
            }
        }
    }
    if (renaming) RenameDialog(c.id, species, custom) { renaming = false }
}

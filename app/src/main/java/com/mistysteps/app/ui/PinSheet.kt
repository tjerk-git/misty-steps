package com.mistysteps.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.pin.PinGroup
import com.mistysteps.app.pin.PinLabel
import com.mistysteps.app.pin.PinRules
import com.mistysteps.app.pin.PinSprites
import java.text.DateFormat
import java.util.Date

/** Which pin sheet is open on the map screen. */
sealed interface PinSheetState {
    /** Leaving a new pin here. [sessionId] is the walk it's dropped on, null for a long-press. */
    data class New(val lat: Double, val lon: Double, val sessionId: Long?) : PinSheetState

    /** Looking at (and maybe editing) an existing pin. */
    data class Open(val pinId: Long) : PinSheetState
}

/** The pin icons, converted for Compose once. */
@Composable
fun rememberPinIcons(): Map<PinLabel, ImageBitmap> =
    remember { PinLabel.entries.associateWith { PinSprites.bitmap(it).asImageBitmap() } }

/** "Leave a pin": pick a label, maybe add a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPinSheet(onPlace: (labelKey: String, note: String?) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf<PinLabel?>(null) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Frost,
    ) {
        SheetColumn {
            SheetTitle("Leave a pin")
            LabelPicker(selected = label, onSelect = { label = it })
            Spacer(Modifier.height(12.dp))
            NoteField(note, required = label == PinLabel.OTHER, onChange = { note = it })
            Spacer(Modifier.height(12.dp))
            val chosen = label
            Button(
                onClick = { if (chosen != null) onPlace(chosen.key, PinRules.cleanNote(note)) },
                enabled = chosen != null && PinRules.canPlace(chosen, PinRules.cleanNote(note)),
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("Place pin") }
        }
    }
}

/** A pin you tapped: what it says, and edit or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailSheet(
    pin: Pin,
    onSave: (labelKey: String, note: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember(pin.id) { mutableStateOf(false) }
    var picked by remember(pin.id) { mutableStateOf<PinLabel?>(null) }
    var note by remember(pin.id) { mutableStateOf(pin.note.orEmpty()) }
    var confirmDelete by remember(pin.id) { mutableStateOf(false) }
    val icons = rememberPinIcons()
    val day = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Frost,
    ) {
        SheetColumn {
            if (!editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PinIcon(icons.getValue(pin.label), 48)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(pin.label.text), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        val placed = buildList {
                            add(day.format(Date(pin.time)))
                            pin.sessionId?.let { add("on your walk of ${day.format(Date(it))}") }
                        }
                        Text(placed.joinToString(" · "), color = Dusk, fontSize = 12.sp)
                    }
                }
                pin.note?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Ink, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Ember) }
                    TextButton(onClick = { editing = true }) { Text("Edit", color = Ink) }
                }
            } else {
                SheetTitle("Edit pin")
                val shown = picked ?: pin.label
                LabelPicker(selected = shown, onSelect = { picked = it })
                Spacer(Modifier.height(12.dp))
                NoteField(note, required = shown == PinLabel.OTHER, onChange = { note = it })
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false; picked = null; note = pin.note.orEmpty() }) {
                        Text("Cancel", color = Dusk)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(PinRules.keyAfterEdit(pin.labelKey, picked), PinRules.cleanNote(note)) },
                        enabled = PinRules.canPlace(shown, PinRules.cleanNote(note)),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    ) { Text("Save") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this pin?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        content = content,
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Ink)
    Spacer(Modifier.height(12.dp))
}

/** The labels in their groups, three to a row, then "Something else". */
@Composable
private fun LabelPicker(selected: PinLabel?, onSelect: (PinLabel) -> Unit) {
    val icons = rememberPinIcons()
    for (group in PinGroup.entries) {
        Text(stringResource(group.title).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (label in PinLabel.entries.filter { it.group == group }) {
                LabelTile(label, icons.getValue(label), label == selected, Modifier.weight(1f)) { onSelect(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelTile(PinLabel.OTHER, icons.getValue(PinLabel.OTHER), selected == PinLabel.OTHER, Modifier.weight(1f)) {
            onSelect(PinLabel.OTHER)
        }
        Spacer(Modifier.weight(2f).height(1.dp))
    }
}

@Composable
private fun LabelTile(label: PinLabel, icon: ImageBitmap, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .background(if (selected) Color.White else Color.Transparent, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Ember else Color(0xFFDCE1EE), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PinIcon(icon, 40)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(label.text),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Ink,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

@Composable
private fun NoteField(note: String, required: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = note,
        onValueChange = { onChange(it.take(PinRules.NOTE_MAX)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(if (required) "Note" else "Note (optional)") },
        placeholder = { Text("the bench by the red door") },
        supportingText = { Text("${note.length}/${PinRules.NOTE_MAX}") },
    )
}

/** A crisp pixel icon, [sizeDp] square. */
@Composable
fun PinIcon(icon: ImageBitmap, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(sizeDp.dp).drawBehind { drawSprite(icon) })
}

package com.onedayonesecond.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onedayonesecond.data.Captions
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.DeletedClip
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.common.JournalImage
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.AppSheet
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.appTextFieldColors
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * What can be done with a day. Deleting sits apart at the bottom and says what it
 * leaves alone; the sheet's Close button is below everything, within thumb's reach.
 */
@Composable
fun DayOptionsSheet(
    date: LocalDate,
    clip: ClipEntity?,
    place: String?,
    onReplace: () -> Unit,
    onMove: () -> Unit,
    onCaption: () -> Unit,
    onPlace: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val divider = Modifier.padding(horizontal = ScreenPadding)
    AppSheet(onDismiss = onDismiss, title = RecapLabels.dayAndMonth(date), meta = RecapLabels.fullDate(date)) {
        if (clip != null) {
            ActionRow("Replace this second", onReplace, icon = LineIcons.Video)
            Hairline(divider)
            ActionRow("Move to another day", onMove, icon = LineIcons.Days)
            Hairline(divider)
            ActionRow(
                if (clip.caption.isEmpty()) "Add a caption" else "Edit caption",
                onCaption,
                // What it says now, so the row doubles as a way to read it.
                subtitle = clip.caption.ifEmpty { null },
                icon = LineIcons.Write,
            )
            Hairline(divider)
        }
        ActionRow(
            if (place == null) "Add a place" else "Change place",
            onPlace,
            subtitle = place,
            icon = LineIcons.Place,
        )
        if (clip != null) {
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            ActionRow(
                "Delete this second",
                onDelete,
                subtitle = "Your writing and photos stay. You can undo, and it stays in " +
                    "Recently deleted for ${DeletedClip.KEEP_DAYS} days.",
                icon = LineIcons.Trash,
            )
        }
    }
}

/** The five faces, for setting or changing a day's mood, and a way to clear it. */
@Composable
fun MoodSheet(date: LocalDate, selected: Mood?, onPick: (Mood?) -> Unit, onDismiss: () -> Unit) {
    val isToday = date == LocalDate.now()
    AppSheet(
        onDismiss = onDismiss,
        title = if (isToday) "How was today?" else "How was this day?",
        meta = RecapLabels.fullDate(date),
    ) {
        MoodRow(selected = selected, onPick = { onPick(it) })
        if (selected != null) {
            QuietButton(
                "Remove the mood",
                { onPick(null) },
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 12.dp),
            )
        }
    }
}

/**
 * Type the caption for one day's clip. It is shown on the day page and drawn above
 * the date when the reel is built. Saving it empty removes the caption.
 */
@Composable
fun CaptionDialog(date: LocalDate, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    // Open straight into typing, with the keyboard up.
    LaunchedEffect(Unit) { focus.requestFocus() }

    AppDialog(
        onDismiss = onDismiss,
        title = "Caption for ${RecapLabels.dayAndMonth(date)}",
        buttons = {
            QuietButton("Cancel", onDismiss, Modifier.weight(1f))
            RoseButton("Save", { onSave(text) }, Modifier.weight(1f))
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(Captions.MAX_LENGTH) },
            label = { Text("Caption") },
            supportingText = { Text("${text.length} of ${Captions.MAX_LENGTH}. Leave empty to remove it.") },
            // One line to type: where it wraps on the reel is decided when it is drawn.
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            colors = appTextFieldColors(),
            textStyle = AppType.Body,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** Type a day's place, or fill in where the phone is now. Saving it empty removes the place line. */
@Composable
fun PlaceDialog(
    date: LocalDate,
    current: String,
    findCurrent: (suspend () -> String?)?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    var finding by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppDialog(
        onDismiss = onDismiss,
        title = "Place for ${RecapLabels.dayAndMonth(date)}",
        buttons = {
            QuietButton("Cancel", onDismiss, Modifier.weight(1f))
            RoseButton("Save", { onSave(text) }, Modifier.weight(1f))
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(JournalRepository.MAX_PLACE_LENGTH)
                notFound = false
            },
            label = { Text("Place") },
            supportingText = { Text("For example \"Kemang, Jakarta Selatan\". Leave empty to hide the place.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            colors = appTextFieldColors(),
            textStyle = AppType.Body,
            modifier = Modifier.fillMaxWidth(),
        )
        if (findCurrent != null) {
            QuietButton(
                if (finding) "Finding where you are…" else "Use where I am now",
                onClick = {
                    finding = true
                    scope.launch {
                        val found = findCurrent()
                        finding = false
                        if (found == null) notFound = true else text = found
                    }
                },
                enabled = !finding,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (notFound) {
                MetaText(
                    "The phone couldn't tell where it is. Check that location is on and allowed " +
                        "for 1day1sec, or type the place.",
                    Modifier.padding(top = 8.dp),
                    color = Palette.Ink,
                )
            }
        }
    }
}

/** What can be done with one entry. */
@Composable
fun EntrySheet(entry: Entry, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val divider = Modifier.padding(horizontal = ScreenPadding)
    AppSheet(onDismiss = onDismiss) {
        if (entry.type != EntryType.PHOTO) {
            ActionRow(
                when {
                    entry.type == EntryType.WRITTEN -> "Edit"
                    entry.text.isNullOrEmpty() -> "Type the words yourself"
                    else -> "Correct the words"
                },
                onEdit,
                icon = LineIcons.Write,
            )
            Hairline(divider)
        }
        ActionRow("Delete this entry", onDelete, icon = LineIcons.Trash)
    }
}

@Composable
fun ConfirmDeleteEntryDialog(entry: Entry, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val thing = when (entry.type) {
        EntryType.WRITTEN -> "this writing"
        EntryType.VOICE -> "this voice note and its words"
        EntryType.NOTEBOOK_PHOTO -> "this notebook page and its words"
        EntryType.PEN -> "this pen page and its words"
        EntryType.PHOTO -> "this photo"
    }
    AppDialog(
        onDismiss = onDismiss,
        title = "Delete this entry?",
        buttons = {
            QuietButton("Keep it", onDismiss, Modifier.weight(1f))
            QuietButton("Delete", onDelete, Modifier.weight(1f))
        },
    ) {
        BodyText("This removes $thing from your phone. Unlike a deleted second, it can't be brought back.")
    }
}

/** A photo or page, whole, on a paper background. */
@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Paper).systemBarsPadding()) {
            JournalImage(
                file = file,
                description = "The photo, full size",
                contentScale = ContentScale.Fit,
                maxSidePx = 2048,
                modifier = Modifier.fillMaxSize().padding(top = 56.dp),
            )
            Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                IconAction(LineIcons.Close, "Close the photo", onDismiss)
            }
        }
    }
}

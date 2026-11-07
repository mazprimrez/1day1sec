package com.onedayonesecond.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.Captions
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.ClipMoveRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DialogDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** What can be done with a day's clip: opened by pressing and holding the day. */
@Composable
fun ClipOptionsDialog(
    clip: ClipEntity,
    onCaption: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clip.date.format(DialogDate)) },
        text = {
            Column {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCaption)
                        .padding(vertical = 14.dp),
                ) {
                    Text(
                        if (clip.caption.isEmpty()) "Add caption" else "Edit caption",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (clip.caption.isNotEmpty()) {
                        // What it says now, so the option doubles as a way to read it.
                        Text(
                            clip.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "Move to another date",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onMove)
                        .padding(vertical = 14.dp),
                )
                Text(
                    "Delete",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete)
                        .padding(vertical = 14.dp),
                )
            }
        },
        // Both actions are in the list above; only a way out is needed down here.
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick the day the clip really belongs to. Days that have not happened yet are greyed out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveClipDatePicker(
    clip: ClipEntity,
    onMove: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = ClipMoveRules.toPickerMillis(clip.date),
        selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    ClipMoveRules.isSelectable(utcTimeMillis, today)

                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )
    val picked = pickerState.selectedDateMillis?.let(ClipMoveRules::fromPickerMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // Choosing the day it is already on would move nothing.
                enabled = picked != null && picked != clip.date,
                onClick = { picked?.let(onMove) },
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    "Move the clip from ${clip.date.format(DialogDate)} to…",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                )
            },
        )
    }
}

/** Another clip is already on the chosen day; moving there removes it. */
@Composable
fun ConfirmReplaceDialog(
    target: LocalDate,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace the clip on ${target.format(DialogDate)}?") },
        text = { Text("That day already has a clip. Moving this one there removes the one that is there now.") },
        confirmButton = { TextButton(onClick = onReplace) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Type the caption for one day's clip. It is drawn above the date when the reel is
 * built. Saving it empty removes the caption.
 */
@Composable
fun CaptionDialog(
    clip: ClipEntity,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(clip.caption) }
    val focus = remember { FocusRequester() }
    // Open straight into typing, with the keyboard up.
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Caption for ${clip.date.format(DialogDate)}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Captions.MAX_LENGTH) },
                label = { Text("Shown above the date") },
                supportingText = { Text("${text.length}/${Captions.MAX_LENGTH} · leave empty to remove") },
                // One line to type: where it wraps on the reel is decided when it is drawn.
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

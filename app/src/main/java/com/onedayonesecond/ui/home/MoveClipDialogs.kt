package com.onedayonesecond.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clip.date.format(DialogDate)) },
        text = {
            Column {
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

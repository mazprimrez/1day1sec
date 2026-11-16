package com.onedayonesecond.ui.day

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.ClipMoveRules
import com.onedayonesecond.data.DeletedClip
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.appTextFieldColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pick the day a second really belongs to, by typing it or from a month calendar.
 *
 * Days that already have a second carry a dot. Days after today cannot be chosen, and
 * the dialog says why. Choosing a day that has a second shows what is there before
 * anything happens, and the button then says "Move and replace". Cancel has the
 * focus when the dialog opens, so a stray Enter does nothing.
 */
@Composable
fun MoveDialog(
    clip: ClipEntity,
    loadMonth: suspend (YearMonth) -> Map<LocalDate, ClipEntity>,
    onMove: (target: LocalDate, replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(clip.date)) }

    val monthClips by produceState(emptyMap<LocalDate, ClipEntity>(), month) { value = loadMonth(month) }
    // Looked up for the chosen day itself, which may not be in the month on show.
    val occupant by produceState<ClipEntity?>(null, selected) {
        value = selected?.let { day -> loadMonth(YearMonth.from(day))[day] }
    }

    val unreadable = text.isNotBlank() && selected == null
    val check = selected?.let { ClipMoveRules.check(clip.date, it, today, targetHasClip = occupant != null) }
    val replacing = check == ClipMoveRules.Check.NEEDS_REPLACE
    val canMove = check == ClipMoveRules.Check.OK || replacing

    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Paper)
                .border(1.dp, Palette.Hairline, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            HeadingText("Move to another day")
            MetaText("This second is on ${RecapLabels.dayAndMonth(clip.date)} ${clip.date.year}.")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed
                    selected = ClipMoveRules.parseDay(typed, today)
                    selected?.let { month = YearMonth.from(it) }
                },
                label = { Text("New day") },
                supportingText = { Text("Type a day, like \"${RecapLabels.compact(today.minusDays(1))}\", or pick one below.") },
                singleLine = true,
                isError = unreadable,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = appTextFieldColors(),
                textStyle = AppType.Body,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            MonthPicker(
                month = month,
                today = today,
                from = clip.date,
                selected = selected,
                daysWithClip = monthClips.keys,
                onMonth = { month = it },
                onPick = { day ->
                    selected = day
                    text = RecapLabels.compact(day)
                },
            )
            MetaText(
                "A dot marks a day that already has a second. Days after today can't be " +
                    "chosen: they haven't happened yet.",
                Modifier.padding(top = 8.dp),
            )

            // What choosing this day would do, said before it is done.
            val chosen = selected
            val notice = when {
                unreadable -> "That doesn't read as a day. Try something like \"${RecapLabels.compact(today)}\"."
                chosen == null -> null
                check == ClipMoveRules.Check.FUTURE ->
                    "${RecapLabels.dayAndMonth(chosen)} ${chosen.year} hasn't happened yet, so a second can't be moved there."
                check == ClipMoveRules.Check.SAME_DAY -> "This second is already on ${RecapLabels.dayAndMonth(chosen)}."
                replacing -> {
                    val caption = occupant?.caption.orEmpty()
                    "${RecapLabels.dayAndMonth(chosen)} already has a second" +
                        (if (caption.isEmpty()) "" else ", \"$caption\"") +
                        ". Moving here replaces it. The replaced second goes to Recently deleted, " +
                        "where it can be brought back for ${DeletedClip.KEEP_DAYS} days."
                }
                else -> null
            }
            if (notice != null) {
                Text(
                    notice,
                    style = AppType.Body,
                    color = Palette.Ink,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Palette.Rose, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Cancel", onDismiss, Modifier.weight(1f).focusRequester(cancelFocus))
                RoseButton(
                    text = when {
                        chosen == null || !canMove -> "Move"
                        replacing -> "Move and replace ${RecapLabels.compactDay(chosen)}"
                        else -> "Move to ${RecapLabels.compactDay(chosen)}"
                    },
                    onClick = { chosen?.let { onMove(it, replacing) } },
                    enabled = canMove,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun MonthPicker(
    month: YearMonth,
    today: LocalDate,
    from: LocalDate,
    selected: LocalDate?,
    daysWithClip: Set<LocalDate>,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = AppType.BodyStrong,
            color = Palette.Ink,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconAction(LineIcons.ChevronLeft, "Previous month", { onMonth(month.minusMonths(1)) })
        IconAction(
            LineIcons.ChevronRight,
            "Next month",
            { onMonth(month.plusMonths(1)) },
            enabled = month < YearMonth.from(today),
        )
    }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, locale),
                style = AppType.Meta,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val cells = buildList<LocalDate?> {
        repeat(month.atDay(1).dayOfWeek.value - 1) { add(null) }
        (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
        while (size % 7 != 0) add(null)
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                if (day == null) {
                    Spacer(Modifier.weight(1f).height(44.dp))
                } else {
                    PickerDay(
                        day = day,
                        isFuture = day.isAfter(today),
                        isToday = day == today,
                        isFrom = day == from,
                        isSelected = day == selected,
                        hasClip = day in daysWithClip,
                        onClick = { onPick(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerDay(
    day: LocalDate,
    isFuture: Boolean,
    isToday: Boolean,
    isFrom: Boolean,
    isSelected: Boolean,
    hasClip: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        isFuture -> "Not yet, can't be chosen"
        isFrom -> "This second is here now"
        hasClip -> "Already has a second"
        else -> "No second"
    }
    Box(
        modifier
            .height(44.dp)
            .clickable(enabled = !isFuture, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = RecapLabels.fullDate(day)
                stateDescription = status
                this.selected = isSelected
                role = Role.RadioButton
                if (isFuture) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(Palette.Blush) else Modifier)
                .then(if (isToday) Modifier.border(2.dp, Palette.Rose, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = AppType.Meta,
                color = if (isFuture) Palette.Muted.copy(alpha = 0.5f) else Palette.Ink,
            )
        }
        if (hasClip) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isFrom) Palette.Muted else Palette.Rose),
            )
        }
    }
}

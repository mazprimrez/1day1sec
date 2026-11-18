package com.onedayonesecond.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.semantics.heading
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.ContactFrame
import com.onedayonesecond.ui.theme.ContactSheet
import com.onedayonesecond.ui.theme.FrameKind
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.TextAction
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.ClipMoveRules
import com.onedayonesecond.data.DeletedClip
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.appTextFieldColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pick the day a second really belongs to, by typing it or by tapping its frame on a
 * small contact sheet of the month. It takes over the whole screen.
 *
 * Filled frames already have a second. Days after today cannot be chosen, and the
 * page says why. Choosing a day that has a second shows what is there before anything
 * happens, and the button then says "Move and replace". Cancel has the focus when the
 * page opens, so a stray Enter does nothing.
 */
@UnstableApi
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
        Column(Modifier.fillMaxSize().background(Palette.Paper).systemBarsPadding().imePadding()) {
            BackBar(onDismiss, backLabel = "Cancel", title = "Move this second", backIcon = LineIcons.Close)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
            ) {
                MetaText(
                    "It's on ${RecapLabels.weekdayAndDay(clip.date)} now. Your writing and photos stay on that day.",
                    Modifier.padding(bottom = 20.dp),
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { typed ->
                        text = typed
                        selected = ClipMoveRules.parseDay(typed, today)
                        selected?.let { month = YearMonth.from(it) }
                    },
                    label = { Text("New day") },
                    supportingText = {
                        Text("Type a date, like \"${RecapLabels.compact(today.minusDays(1))}\", or pick a frame below")
                    },
                    singleLine = true,
                    isError = unreadable,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = appTextFieldColors(),
                    textStyle = AppType.Body,
                    modifier = Modifier.fillMaxWidth(),
                )

                MonthPicker(
                    month = month,
                    today = today,
                    from = clip.date,
                    selected = selected,
                    clips = monthClips,
                    onMonth = { month = it },
                    onPick = { day ->
                        selected = day
                        text = RecapLabels.compact(day)
                    },
                )
                SmallText(
                    "Filled frames already have a second. Days after today can't hold one yet.",
                    Modifier.padding(top = 12.dp),
                )

                // What choosing this day would do, said before it is done.
                val chosen = selected
                val notice = when {
                    unreadable -> "That doesn't read as a day. Try something like \"${RecapLabels.compact(today)}\"."
                    chosen == null -> null
                    check == ClipMoveRules.Check.FUTURE ->
                        "${RecapLabels.weekdayAndDay(chosen)} hasn't happened yet, so a second can't be moved there."
                    check == ClipMoveRules.Check.SAME_DAY -> "This second is already on ${RecapLabels.dayAndMonth(chosen)}."
                    replacing -> {
                        val caption = occupant?.caption.orEmpty()
                        "${RecapLabels.weekdayAndDay(chosen)} already has a second" +
                            (if (caption.isEmpty()) "" else ", “$caption”") +
                            ". Moving here replaces it. The old one goes to Recently deleted " +
                            "for ${DeletedClip.KEEP_DAYS} days."
                    }
                    else -> null
                }
                if (notice != null) {
                    InlineAlert(
                        title = notice,
                        icon = if (replacing) LineIcons.Replace else LineIcons.Warning,
                        warning = true,
                        modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(start = ScreenPadding - 12.dp, top = 12.dp, end = ScreenPadding, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chosen = selected
                TextAction("Cancel", onDismiss, Modifier.focusRequester(cancelFocus))
                RoseButton(
                    text = when {
                        chosen == null || !canMove -> "Move"
                        replacing -> "Move and replace"
                        else -> "Move to ${RecapLabels.compactDay(chosen)}"
                    },
                    onClick = { chosen?.let { onMove(it, replacing) } },
                    enabled = canMove,
                )
            }
        }
    }
}

/** The month as a small contact sheet, with the arrows that turn to another month. */
@UnstableApi
@Composable
private fun MonthPicker(
    month: YearMonth,
    today: LocalDate,
    from: LocalDate,
    selected: LocalDate?,
    clips: Map<LocalDate, ClipEntity>,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = AppType.Heading,
            color = Palette.Ink,
            modifier = Modifier.weight(1f).semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        IconAction(LineIcons.ChevronLeft, "Previous month", { onMonth(month.minusMonths(1)) })
        IconAction(
            LineIcons.ChevronRight,
            "Next month",
            { onMonth(month.plusMonths(1)) },
            enabled = month < YearMonth.from(today),
            modifier = Modifier.offset(x = 12.dp),
        )
    }
    ContactSheet(count = month.lengthOfMonth(), rowGap = 6.dp, modifier = Modifier.padding(top = 4.dp)) { index, cell ->
        val day = month.atDay(index + 1)
        val clip = clips[day]
        val isFuture = day.isAfter(today)
        ContactFrame(
            kind = when {
                isFuture -> FrameKind.Future
                clip != null -> FrameKind.Kept
                else -> FrameKind.Missed
            },
            label = RecapLabels.fullDate(day),
            stateLabel = when {
                isFuture -> "After today, can't be chosen"
                day == from -> "This second is here now"
                clip != null -> "Already has a second"
                day == today -> "Today, empty"
                else -> "Empty"
            },
            number = "%02d".format(day.dayOfMonth),
            today = day == today,
            selected = day == selected,
            frameHeight = 43.dp,
            onClick = { onPick(day) },
            modifier = cell,
        ) {
            ClipStill(clip, Modifier.fillMaxSize())
        }
    }
}

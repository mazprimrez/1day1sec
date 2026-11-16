package com.onedayonesecond.ui.recaps

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.data.RecapFailureReason
import com.onedayonesecond.journal.Direction
import com.onedayonesecond.journal.PixelKind
import com.onedayonesecond.journal.Pixels
import com.onedayonesecond.journal.RecapContent
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.TitleText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * One layout for the week, the month and the year: the period, a title and the quote
 * in the user's own words, one picture of the days, then the chapters.
 */
@UnstableApi
@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPixels: (Int) -> Unit,
    onWrite: (LocalDate) -> Unit,
    viewModel: RecapViewModel = viewModel(factory = RecapViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period = state.period
    val today = state.today
    var playing by remember { mutableStateOf(false) }
    val status = state.status
    val clips = remember(state.clips) { state.clips.values.sortedBy { it.date } }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back to recaps")

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 1. The period, with a way to step to the one before (and after, once there is one).
            Row(
                Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaText(RecapLabels.long(period), Modifier.weight(1f))
                IconAction(LineIcons.ChevronLeft, "Previous ${period.noun}", { onOpenRecap(period.previous()) })
                IconAction(
                    LineIcons.ChevronRight,
                    "Next ${period.noun}",
                    { onOpenRecap(period.next()) },
                    enabled = !period.next().start.isAfter(today),
                )
            }

            // 2. Title and quote.
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                if (status is RecapStatus.Ready) {
                    TitleText(status.recap.title)
                    if (status.recap.quote.isNotEmpty()) {
                        Text(
                            "“${status.recap.quote}”",
                            style = AppType.Quote,
                            color = Palette.Rose,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        MetaText(
                            RecapLabels.quoteSource(period, status.recap.quoteDate),
                            Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    TitleText(
                        when (period) {
                            is RecapPeriod.Week -> if (today in period.start..period.end) "This week" else "That week"
                            is RecapPeriod.Month -> period.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            is RecapPeriod.Year -> period.year.toString()
                        },
                    )
                }
            }

            // 3. One picture of the days.
            Spacer(Modifier.height(24.dp))
            when (period) {
                is RecapPeriod.Week -> WeekTiles(period, state, onOpenDay)
                is RecapPeriod.Month -> MonthTiles(period, state, onOpenDay)
                is RecapPeriod.Year -> YearPreview(period.year, state.moods, today) { onOpenPixels(period.year) }
            }
            MetaText(
                if (period is RecapPeriod.Year) {
                    "Each square is a day, coloured by mood. Darker is a harder day."
                } else {
                    "The bar under each day is your mood. Darker is a harder day."
                },
                Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
            )

            // 4. Play the period's seconds back to back.
            if (clips.isNotEmpty()) {
                val what = if (today in period.start..period.end) "this ${period.noun}" else "that ${period.noun}"
                QuietButton(
                    "Play $what, ${clips.size} second${if (clips.size == 1) "" else "s"}",
                    { playing = true },
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // 5. The chapters, or why there are none yet.
            when (status) {
                is RecapStatus.Ready -> Chapters(period, status.content, state.name)
                RecapStatus.Loading -> Unit
                RecapStatus.Writing -> WritingPlaceholder()
                is RecapStatus.NotEnough -> NotEnough(period, status.daysWritten, today) { onWrite(today) }
                is RecapStatus.NotDue -> NotDue(period, status)
                is RecapStatus.Missing -> Missing(status.failure, viewModel::writeNow)
                RecapStatus.NotSetUp -> StateNote(
                    "Letters can't be written yet: the AI service isn't set up in this build of the app. " +
                        "Your entries are safe.",
                )
            }

            // 6.
            Hairline(Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp))
            MetaText(
                "A reflection from your own entries, not therapy.",
                Modifier.padding(horizontal = ScreenPadding, vertical = 20.dp),
            )
        }
    }

    if (playing) PlayDialog(clips, onDismiss = { playing = false })
}

// The picture

@Composable
private fun MoodBar(mood: Mood?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .then(
                if (mood != null) Modifier.background(Palette.mood(mood)) else Modifier.border(1.dp, Palette.Hairline, shape),
            ),
    )
}

@UnstableApi
@Composable
private fun DayTile(
    date: LocalDate,
    clip: ClipEntity?,
    mood: Mood?,
    isFuture: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(TileShape)
            .clickable(enabled = !isFuture, role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = RecapLabels.fullDate(date)
                stateDescription = listOf(
                    if (clip != null) "Second kept" else "No second",
                    if (mood != null) "mood ${mood.label.lowercase()}" else "no mood",
                ).joinToString(", ")
                if (!isFuture) role = Role.Button
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (clip != null) {
            ClipStill(clip, Modifier.fillMaxWidth().aspectRatio(CLIP_ASPECT))
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CLIP_ASPECT)
                    .then(if (isFuture) Modifier else Modifier.border(1.dp, Palette.Hairline, TileShape)),
            )
        }
        if (!isFuture) MoodBar(mood) else Spacer(Modifier.height(4.dp))
    }
}

@UnstableApi
@Composable
private fun WeekTiles(week: RecapPeriod.Week, state: RecapUiState, onOpenDay: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    Row(Modifier.padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L..6L).forEach { offset ->
            val date = week.start.plusDays(offset)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MetaText(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.padding(bottom = 4.dp).clearAndSetSemantics { },
                )
                DayTile(
                    date = date,
                    clip = state.clips[date],
                    mood = state.moods[date],
                    isFuture = date.isAfter(state.today),
                    onOpen = { onOpenDay(date) },
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun MonthTiles(month: RecapPeriod.Month, state: RecapUiState, onOpenDay: (LocalDate) -> Unit) {
    val cells = buildList<LocalDate?> {
        repeat(month.start.dayOfWeek.value - 1) { add(null) }
        var day = month.start
        while (!day.isAfter(month.end)) {
            add(day)
            day = day.plusDays(1)
        }
        while (size % 7 != 0) add(null)
    }
    Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayTile(
                            date = date,
                            clip = state.clips[date],
                            mood = state.moods[date],
                            isFuture = date.isAfter(state.today),
                            onOpen = { onOpenDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** A small year in pixels that opens the full one. */
@Composable
private fun YearPreview(year: Int, moods: Map<LocalDate, Mood>, today: LocalDate, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open the year in pixels", onClick = onOpen)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier.width(120.dp).aspectRatio(Pixels.MONTHS / Pixels.DAYS.toFloat()).clearAndSetSemantics { },
        ) {
            drawPixels(year, moods, today, gapFraction = 0.2f)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("Year in pixels", style = AppType.Body, color = Palette.Ink)
            MetaText("${moods.size} day${if (moods.size == 1) "" else "s"} with a mood. Open it full size.")
        }
        Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted)
    }
}

/** Draws the grid to fill the canvas: months across, days down. Shared with the pixels page. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixels(
    year: Int,
    moods: Map<LocalDate, Mood>,
    today: LocalDate,
    gapFraction: Float,
) {
    val cellW = size.width / Pixels.MONTHS
    val cellH = size.height / Pixels.DAYS
    val gap = minOf(cellW, cellH) * gapFraction
    val w = cellW - gap
    val h = cellH - gap
    val corner = CornerRadius(minOf(w, h) * 0.2f)
    for (month in 1..Pixels.MONTHS) {
        for (day in 1..Pixels.DAYS) {
            val pixel = Pixels.at(year, month, day, moods, today)
            val topLeft = Offset((month - 1) * cellW + gap / 2, (day - 1) * cellH + gap / 2)
            when (pixel.kind) {
                PixelKind.MOOD -> drawRoundRect(Palette.mood(pixel.mood!!), topLeft, Size(w, h), corner)
                PixelKind.EMPTY -> drawRoundRect(
                    Palette.Hairline,
                    topLeft,
                    Size(w, h),
                    corner,
                    style = Stroke(1.dp.toPx().coerceAtMost(w / 4)),
                )
                PixelKind.FUTURE -> drawCircle(
                    Palette.Hairline,
                    radius = minOf(w, h) * 0.12f,
                    center = Offset(topLeft.x + w / 2, topLeft.y + h / 2),
                )
                PixelKind.ABSENT -> Unit
            }
        }
    }
}

// The chapters

@Composable
private fun Chapter(title: String, first: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        if (!first) Hairline(Modifier.padding(vertical = 20.dp))
        HeadingText(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Chapters(period: RecapPeriod, content: RecapContent, name: String) {
    val locale = Locale.getDefault()
    var first = true
    fun isFirst(): Boolean = first.also { first = false }

    if (content.whatHappened.isNotEmpty()) {
        Chapter("What happened", isFirst()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.whatHappened.forEach { line ->
                    Row {
                        // Within a week the weekday places a line; over longer stretches the date does.
                        MetaText(
                            if (period is RecapPeriod.Week) {
                                line.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            } else {
                                RecapLabels.compactDay(line.date)
                            },
                            Modifier.width(if (period is RecapPeriod.Week) 48.dp else 64.dp).padding(top = 2.dp),
                        )
                        BodyText(line.text, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.note.isNotEmpty()) {
        Chapter("A note for you", isFirst()) {
            if (name.isNotBlank()) BodyText("Dear ${name.trim()},", Modifier.padding(bottom = 8.dp))
            // The page writes the greeting itself; one the model added anyway is dropped.
            BodyText(content.note.replace(Regex("^\\s*Dear [^,\\n]{1,40},\\s*"), ""))
        }
    }

    if (content.changes.isNotEmpty()) {
        Chapter("Since last ${period.noun}", isFirst()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.changes.forEach { change ->
                    Row {
                        Icon(
                            when (change.direction) {
                                Direction.UP -> LineIcons.ArrowUp
                                Direction.DOWN -> LineIcons.ArrowDown
                                Direction.SAME -> LineIcons.ArrowSame
                            },
                            contentDescription = when (change.direction) {
                                Direction.UP -> "Up:"
                                Direction.DOWN -> "Down:"
                                Direction.SAME -> "About the same:"
                            },
                            tint = Palette.Ink,
                            modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(20.dp),
                        )
                        BodyText(change.text, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.tries.isNotEmpty()) {
        // Suggestions, not chores: plain bullets, nothing to tick off.
        Chapter("Something to try", isFirst()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.tries.forEach { suggestion ->
                    Row {
                        Text("•", style = AppType.Body, color = Palette.Muted, modifier = Modifier.width(20.dp).clearAndSetSemantics { })
                        BodyText(suggestion, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.places.isNotEmpty()) {
        Chapter("Where you were", isFirst()) {
            val most = content.places.maxOf { it.days }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.places.forEach { place ->
                    Column(Modifier.semantics(mergeDescendants = true) { }) {
                        Row {
                            BodyText(place.name, Modifier.weight(1f))
                            MetaText("${place.days} day${if (place.days == 1) "" else "s"}", Modifier.padding(top = 2.dp))
                        }
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(place.days.toFloat() / most)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Palette.Blush),
                        )
                    }
                }
            }
        }
    }
}

// The states

@Composable
private fun StateNote(text: String, modifier: Modifier = Modifier) {
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Hairline(Modifier.padding(bottom = 20.dp))
        BodyText(text, modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Grey lines where the letter will be, so the page does not jump when it arrives. */
@Composable
private fun WritingPlaceholder() {
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Hairline(Modifier.padding(bottom = 20.dp))
        Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0.5f, 1f, 0.92f, 0.97f, 0.6f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Hairline))
            }
        }
        MetaText(
            "Your letter is being written. Takes about a minute.",
            Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun NotEnough(period: RecapPeriod, daysWritten: Int, today: LocalDate, onWrite: () -> Unit) {
    val days = "$daysWritten day${if (daysWritten == 1) "" else "s"}"
    val min = RecapRules.MIN_DAYS_WITH_ENTRIES
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Hairline(Modifier.padding(bottom = 20.dp))
        if (today in period.start..period.end) {
            BodyText("You've written on $days this ${period.noun}. Write on $min or more and a letter is written for you.")
            RoseButton("Write about today", onWrite, Modifier.fillMaxWidth().padding(top = 16.dp))
        } else {
            BodyText("You wrote on $days that ${period.noun}. A letter needs $min or more days of writing, so none was written.")
        }
    }
}

@Composable
private fun NotDue(period: RecapPeriod, status: RecapStatus.NotDue) {
    val whenText = when (period) {
        is RecapPeriod.Week -> "on Sunday night"
        else -> "on ${RecapLabels.dayAndMonth(status.arrives)}"
    }
    val days = "${status.daysWritten} day${if (status.daysWritten == 1) "" else "s"}"
    StateNote("The letter for this ${period.noun} arrives $whenText. You've written on $days so far.")
}

@Composable
private fun Missing(failure: RecapFailure?, onWriteNow: () -> Unit) {
    val text = when (failure?.reason) {
        RecapFailureReason.OFFLINE -> "Your phone was offline ${failure.at.asMoment()}. Your entries are safe."
        RecapFailureReason.FAILED, RecapFailureReason.NOT_SET_UP ->
            "The letter couldn't be written ${failure.at.asMoment()}. Your entries are safe."
        null -> "This letter hasn't been written yet. Your entries are safe."
    }
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Hairline(Modifier.padding(bottom = 20.dp))
        BodyText(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        RoseButton("Write the letter now", onWriteNow, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** "on Sunday night", "this morning": when something was last tried, in plain words. */
private fun Long.asMoment(): String {
    val at = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val part = when (at.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
    val today = LocalDateTime.now().toLocalDate()
    return when {
        at.toLocalDate() == today && part == "night" -> "tonight"
        at.toLocalDate() == today -> "this $part"
        at.toLocalDate().isAfter(today.minusDays(7)) ->
            "on ${at.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))} $part"
        else -> "on ${RecapLabels.dayAndMonth(at.toLocalDate())}"
    }
}

/** Plays the period's seconds one after another, then closes. */
@UnstableApi
@Composable
private fun PlayDialog(clips: List<ClipEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(clips.map { MediaItem.fromUri(Uri.fromFile(it.file)) })
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onDismiss()
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Paper).systemBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true)
                        .clip(TileShape)
                        .background(Palette.Blush),
                ) {
                    AndroidView(
                        factory = { TextureView(it) },
                        update = { player.setVideoTextureView(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                clips.getOrNull(index)?.let { clip ->
                    MetaText(
                        RecapLabels.weekdayAndDay(clip.date) + if (clip.caption.isEmpty()) "" else ". ${clip.caption}",
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
                        maxLines = 2,
                    )
                }
            }
            IconAction(LineIcons.Close, "Stop playing", onDismiss, Modifier.padding(8.dp))
        }
    }
}

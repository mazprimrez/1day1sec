package com.onedayonesecond.ui.day

import android.media.MediaPlayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.onedayonesecond.ui.theme.BusyLine
import com.onedayonesecond.ui.theme.FabClearance
import com.onedayonesecond.ui.theme.FloatingAction
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.TextAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.ProcessingState
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.media.VideoMetadata
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.ClipPlayer
import com.onedayonesecond.ui.common.JournalImage
import com.onedayonesecond.ui.common.PHOTO_ASPECT
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.MoodFace
import com.onedayonesecond.ui.theme.MoodPlaceholder
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.onedayonesecond.data.DiaryThought
import com.onedayonesecond.ui.books.ThoughtBody
import java.time.format.TextStyle
import java.util.Locale

/** One row on the day page: an entry, or several photos that were added together. */
private sealed interface EntryRow {
    val key: Long

    /** When it happened, which is what the page is ordered by. */
    val at: Long

    data class Single(val entry: Entry) : EntryRow {
        override val key get() = entry.id
        override val at get() = entry.createdAt
    }

    data class Photos(val photos: List<Entry>) : EntryRow {
        override val key get() = photos.first().id
        override val at get() = photos.first().createdAt
    }

    /**
     * A thought logged on a book, shown here because it was logged on this day. It is
     * not an entry: it lives with its book, and this row is a way back to it.
     */
    data class Thought(val diary: DiaryThought) : EntryRow {
        // Entries and thoughts are numbered separately; the sign keeps their keys apart.
        override val key get() = -diary.thought.id
        override val at get() = diary.thought.createdAt
    }
}

/** The day's entries and its book thoughts, in the order they happened. */
private fun rowsOf(entries: List<Entry>, thoughts: List<DiaryThought>): List<EntryRow> {
    val rows = rowsOf(entries)
    if (thoughts.isEmpty()) return rows
    // A stable sort: entries made at the same moment keep the order they already had.
    return (rows + thoughts.map { EntryRow.Thought(it) }).sortedBy { it.at }
}

private fun rowsOf(entries: List<Entry>): List<EntryRow> {
    val rows = mutableListOf<EntryRow>()
    for (entry in entries) {
        val last = rows.lastOrNull()
        if (entry.type == EntryType.PHOTO && last is EntryRow.Photos &&
            last.photos.first().createdAt == entry.createdAt
        ) {
            rows[rows.lastIndex] = EntryRow.Photos(last.photos + entry)
        } else if (entry.type == EntryType.PHOTO) {
            rows += EntryRow.Photos(listOf(entry))
        } else {
            rows += EntryRow.Single(entry)
        }
    }
    return rows
}

private val ClockTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Entry.time(): String =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(ClockTime)

@UnstableApi
@Composable
fun DayScreen(
    onBack: () -> Unit,
    onRecord: (LocalDate) -> Unit,
    onPickFromGallery: (LocalDate) -> Unit,
    onWrite: (LocalDate, Long) -> Unit,
    onVoice: (LocalDate) -> Unit,
    onDraw: (LocalDate) -> Unit,
    onMoved: (LocalDate) -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movedTo by viewModel.movedTo.collectAsStateWithLifecycle()
    val date = state.date

    var adding by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var choosingMood by remember { mutableStateOf(false) }
    var editingCaption by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var entryOptions by remember { mutableStateOf<Entry?>(null) }
    var deletingEntry by remember { mutableStateOf<Entry?>(null) }
    var viewing by remember { mutableStateOf<File?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshGallery() }
    LaunchedEffect(movedTo) { movedTo?.let(onMoved) }

    val voice = rememberVoicePlayback()
    val rows = remember(state.entries, state.thoughts) { rowsOf(state.entries, state.thoughts) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackBar(
                onBack,
                backLabel = "Back to the calendar",
                title = if (state.isToday) "Today" else RecapLabels.weekdayAndDay(date),
            ) {
                IconAction(LineIcons.MoreVertical, "Options for this day", { showOptions = true })
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    DayHeader(
                        state = state,
                        onRecord = { onRecord(date) },
                        onMood = { choosingMood = true },
                        onCaption = { editingCaption = true },
                        modifier = Modifier.padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 20.dp),
                    )
                }

                if (state.loaded && state.clip == null) {
                    item(key = "record") {
                        Column(
                            Modifier.padding(horizontal = ScreenPadding).padding(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoseButton(
                                if (state.isToday) "Record today's second" else "Record this day's second",
                                { onRecord(date) },
                                Modifier.fillMaxWidth(),
                                icon = LineIcons.Camera,
                            )
                            if (state.galleryCount > 0) {
                                QuietButton(
                                    "Choose from ${state.galleryCount} video${if (state.galleryCount == 1) "" else "s"} of this day",
                                    { onPickFromGallery(date) },
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                if (state.loaded && state.isToday && state.mood == null) {
                    item(key = "mood") {
                        Column(Modifier.padding(bottom = 20.dp)) {
                            HeadingText("How was today?", Modifier.padding(horizontal = ScreenPadding))
                            MoodRow(selected = null, onPick = viewModel::setMood, Modifier.padding(top = 8.dp))
                        }
                    }
                }

                if (state.loaded) {
                    item(key = "entries") {
                        HeadingText(
                            when (rows.size) {
                                0 -> "No entries yet"
                                1 -> "1 entry"
                                else -> "${rows.size} entries"
                            },
                            Modifier.padding(horizontal = ScreenPadding).padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }

                if (state.loaded && rows.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(horizontal = ScreenPadding)) {
                            Hairline()
                            MetaText("Nothing written for this day yet.", Modifier.padding(top = 16.dp))
                            QuietButton(
                                "Write a few words",
                                { onWrite(date, -1L) },
                                Modifier.padding(top = 12.dp),
                                icon = LineIcons.Write,
                            )
                        }
                    }
                }

                items(rows, key = { it.key }) { row ->
                    Hairline(Modifier.padding(horizontal = ScreenPadding))
                    when (row) {
                        is EntryRow.Single -> EntryItem(
                            entry = row.entry,
                            canTypeOut = state.canTypeOut,
                            voice = voice,
                            onOptions = { entryOptions = row.entry },
                            onRetry = { viewModel.retry(row.entry) },
                            onView = { viewing = it },
                        )
                        is EntryRow.Photos -> PhotosItem(
                            photos = row.photos,
                            onOptions = { entryOptions = it },
                            onView = { viewing = it },
                        )
                        is EntryRow.Thought -> ThoughtItem(
                            diary = row.diary,
                            canTypeOut = state.canTypeOut,
                            voice = voice,
                            onOpenBook = { onOpenBook(row.diary.thought.bookId) },
                            onRetry = { viewModel.retry(row.diary.thought) },
                            onView = { viewing = it },
                        )
                    }
                }

                item(key = "end") { Spacer(Modifier.height(FabClearance)) }
            }
        }

        FloatingAction(
            "Add entry",
            LineIcons.Plus,
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
        )
    }

    if (adding) {
        AddSheet(
            date = date,
            place = state.place,
            hasClip = state.clip != null,
            onDismiss = { adding = false },
            onRecord = {
                adding = false
                onRecord(date)
            },
            onWrite = {
                adding = false
                onWrite(date, -1L)
            },
            onVoice = {
                adding = false
                onVoice(date)
            },
            onDraw = {
                adding = false
                onDraw(date)
            },
            onPhotosAdded = { adding = false },
        )
    }

    if (showOptions) {
        DayOptionsSheet(
            date = date,
            clip = state.clip,
            place = state.place,
            onReplace = {
                showOptions = false
                onRecord(date)
            },
            onMove = {
                showOptions = false
                moving = true
            },
            onCaption = {
                showOptions = false
                editingCaption = true
            },
            onPlace = {
                showOptions = false
                editingPlace = true
            },
            onDelete = {
                showOptions = false
                viewModel.deleteClip()
            },
            onDismiss = { showOptions = false },
        )
    }

    if (choosingMood) {
        MoodSheet(
            date = date,
            selected = state.mood,
            onPick = {
                viewModel.setMood(it)
                choosingMood = false
            },
            onDismiss = { choosingMood = false },
        )
    }

    state.clip?.let { clip ->
        if (editingCaption) {
            CaptionDialog(
                date = date,
                current = clip.caption,
                onSave = {
                    viewModel.setCaption(it)
                    editingCaption = false
                },
                onDismiss = { editingCaption = false },
            )
        }
        if (moving) {
            MoveDialog(
                clip = clip,
                loadMonth = viewModel::clipsIn,
                onMove = { target, replace ->
                    moving = false
                    viewModel.move(target, replace)
                },
                onDismiss = { moving = false },
            )
        }
    }

    if (editingPlace) {
        PlaceDialog(
            date = date,
            current = state.place.orEmpty(),
            // Only today can be "where I am now".
            findCurrent = if (state.isToday) viewModel::currentPlace else null,
            onSave = {
                viewModel.setPlace(it)
                editingPlace = false
            },
            onDismiss = { editingPlace = false },
        )
    }

    entryOptions?.let { entry ->
        EntrySheet(
            entry = entry,
            onEdit = {
                entryOptions = null
                onWrite(date, entry.id)
            },
            onDelete = {
                entryOptions = null
                deletingEntry = entry
            },
            onDismiss = { entryOptions = null },
        )
    }

    deletingEntry?.let { entry ->
        ConfirmDeleteEntryDialog(
            entry = entry,
            onDelete = {
                voice.stop()
                viewModel.deleteEntry(entry)
                deletingEntry = null
            },
            onDismiss = { deletingEntry = null },
        )
    }

    viewing?.let { file -> PhotoViewer(file, onDismiss = { viewing = null }) }
}

/**
 * The top of the page: the clip in portrait on the left with its frame number and
 * length under it; on the right the mood, the big day number, the weekday and month,
 * the caption and the place.
 */
@UnstableApi
@Composable
private fun DayHeader(
    state: DayUiState,
    onRecord: () -> Unit,
    onMood: () -> Unit,
    onCaption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = state.date
    val locale = Locale.getDefault()
    val clip = state.clip
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.width(HERO_WIDTH)) {
            if (clip != null) {
                ClipPlayer(
                    file = clip.file,
                    version = clip.createdAt,
                    label = "Play this day's second",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // The same dashed frame a missed day has on the month sheet, at full size.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CLIP_ASPECT)
                        .clip(Corners.Still)
                        .drawBehind {
                            val inset = 0.5.dp.toPx()
                            drawRoundRect(
                                Palette.FrameLine,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - inset * 2, size.height - inset * 2),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(
                                    1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                                ),
                            )
                        }
                        .clickable(enabled = state.loaded, role = Role.Button, onClick = onRecord)
                        .semantics { contentDescription = "No second yet. Record one." },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loaded) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(LineIcons.Plus, contentDescription = null, tint = Palette.Rose)
                            MetaText("No second yet", Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            // Its number on the year's reel, and how long it runs.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { }) {
                Text(date.dayOfYear.toString(), style = AppType.FrameNumber, color = Palette.Ink)
                Spacer(Modifier.weight(1f))
                if (clip != null) {
                    Text(formatTimecode(clip.durationMs), style = AppType.FrameNumber, color = Palette.Muted)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            val mood = state.mood
            // On a today without a mood the five faces are offered just below instead.
            if (mood != null || !state.isToday) {
                Row(
                    Modifier
                        .offset(x = (-8).dp)
                        .clip(Corners.Card)
                        .clickable(role = Role.Button, onClick = onMood)
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 8.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (mood == null) {
                                "No mood yet. Choose one."
                            } else {
                                "Mood: ${mood.label}. Change it."
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mood == null) MoodPlaceholder(size = 28.dp) else MoodFace(mood, size = 28.dp)
                    Text(
                        mood?.label ?: "Add mood",
                        style = AppType.TitleSmall,
                        color = Palette.Rose,
                        modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { },
                    )
                }
            }
            Text(
                date.dayOfMonth.toString(),
                style = AppType.Display,
                color = Palette.Ink,
                maxLines = 1,
                softWrap = false,
                // Read as one date rather than a bare number followed by loose words.
                modifier = Modifier.semantics { contentDescription = RecapLabels.fullDate(date) },
            )
            Text(
                "${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${date.month.getDisplayName(TextStyle.FULL, locale)}",
                style = AppType.Heading,
                color = Palette.Muted,
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (clip != null && clip.caption.isNotEmpty()) {
                Text(
                    clip.caption,
                    style = AppType.TitleLarge,
                    color = Palette.Ink,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else if (clip != null) {
                TextAction("Add a caption", onCaption, Modifier.padding(top = 4.dp).offset(x = (-12).dp), icon = LineIcons.Caption)
            }
            state.place?.let { place ->
                Row(Modifier.padding(top = 12.dp)) {
                    Icon(
                        LineIcons.Place,
                        contentDescription = null,
                        tint = Palette.Muted,
                        modifier = Modifier.padding(top = 1.dp).size(18.dp),
                    )
                    MetaText(place, Modifier.padding(start = 6.dp), maxLines = 2)
                }
            }
        }
    }
}

/** The five faces in a row, hardest day first. Each is a labelled button. */
@Composable
fun MoodRow(selected: Mood?, onPick: (Mood) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = ScreenPadding - 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Mood.entries.forEach { mood ->
            val isSelected = mood == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(Corners.Card)
                    .background(if (isSelected) Palette.Blush else Palette.Paper)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(mood) })
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MoodFace(mood, size = 30.dp)
                Text(
                    mood.label,
                    style = AppType.Tab,
                    color = if (isSelected) Palette.Ink else Palette.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The shape of every row under "entries": the time it was made in the left column, and
 * on the right a small line saying what kind of thing it is, then the thing itself.
 * [trailing] sits at the end of that small line: the row's options, or a way onward.
 */
@Composable
private fun EntryFrame(
    time: String,
    kind: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding - 12.dp, bottom = 16.dp)) {
        Box(Modifier.width(52.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(time, style = AppType.Timecode, color = Palette.Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                Text(
                    kind,
                    style = AppType.Tab,
                    color = Palette.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                trailing()
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/**
 * A thought from a book: "From Laut Bercerita, page 214", which opens the book, then
 * the thought as it is shown there. It is changed or deleted on the book's page.
 */
@Composable
private fun ThoughtItem(
    diary: DiaryThought,
    canTypeOut: Boolean,
    voice: VoicePlayback,
    onOpenBook: () -> Unit,
    onRetry: () -> Unit,
    onView: (File) -> Unit,
) {
    val thought = diary.thought
    val time = Instant.ofEpochMilli(thought.createdAt).atZone(ZoneId.systemDefault()).format(ClockTime)
    EntryFrame(
        time = time,
        kind = "From ${diary.bookTitle}, page ${thought.page}",
        icon = LineIcons.Books,
        trailing = { IconAction(LineIcons.ChevronRight, "Open the book", onOpenBook, tint = Palette.Muted) },
    ) {
        // The sign keeps this voice note's "playing" state apart from an entry with the same number.
        ThoughtBody(thought, voice, voiceId = -thought.id, canTypeOut = canTypeOut, onRetry = onRetry, onView = onView)
    }
}

@Composable
private fun EntryItem(
    entry: Entry,
    canTypeOut: Boolean,
    voice: VoicePlayback,
    onOptions: () -> Unit,
    onRetry: () -> Unit,
    onView: (File) -> Unit,
) {
    val length = if (entry.type == EntryType.VOICE) rememberLength(entry.mediaFile) else 0L
    EntryFrame(
        time = entry.time(),
        kind = when (entry.type) {
            EntryType.WRITTEN -> "Written"
            EntryType.VOICE -> if (length > 0) "Voice note · ${formatDuration(length)}" else "Voice note"
            EntryType.NOTEBOOK_PHOTO -> "From my notebook"
            EntryType.PEN -> "S Pen page"
            EntryType.PHOTO -> "Photo"
        },
        icon = when (entry.type) {
            EntryType.WRITTEN -> LineIcons.Write
            EntryType.VOICE -> LineIcons.Mic
            EntryType.PEN -> LineIcons.Pen
            else -> LineIcons.Image
        },
        trailing = { IconAction(LineIcons.More, "Options for this entry", onOptions, tint = Palette.Muted) },
    ) {
        when (entry.type) {
            EntryType.VOICE -> VoiceChip(entry.id, entry.mediaFile, voice, showLength = false)
            EntryType.NOTEBOOK_PHOTO -> entry.mediaFile?.let { file ->
                JournalImage(
                    file = file,
                    description = "Notebook page. Open it.",
                    shape = Corners.Field,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(PHOTO_ASPECT)
                        .clickable(role = Role.Button) { onView(file) },
                )
            }
            // A pen page is mostly empty paper, so it is shown whole on a tinted ground.
            EntryType.PEN -> entry.mediaFile?.let { file ->
                JournalImage(
                    file = file,
                    description = "Pen page. Open it.",
                    shape = Corners.Chip,
                    ground = Palette.BlushTint,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable(role = Role.Button) { onView(file) },
                )
            }
            else -> Unit
        }

        val text = entry.text.orEmpty()
        when {
            text.isNotEmpty() -> BodyText(text)
            entry.processing == ProcessingState.DONE && entry.needsText ->
                MetaText(if (entry.type == EntryType.VOICE) "No words could be made out." else "No writing was found on this page.")
        }
        if (entry.needsText && entry.processing != ProcessingState.DONE) {
            TypingOutNote(
                processing = entry.processing,
                thing = when (entry.type) {
                    EntryType.VOICE -> "voice note"
                    EntryType.PEN -> "page"
                    else -> "photo"
                },
                spoken = entry.type == EntryType.VOICE,
                canTypeOut = canTypeOut,
                onRetry = onRetry,
            )
        }
    }
}

/**
 * What is happening to something whose words are still to be typed out: a voice note,
 * a notebook page, a pen page. [thing] is what to call it ("voice note"). Announced
 * politely when it changes; it never takes focus.
 */
@Composable
fun TypingOutNote(
    processing: ProcessingState,
    thing: String,
    spoken: Boolean,
    canTypeOut: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            processing == ProcessingState.PENDING -> {
                BusyLine()
                MetaText(
                    if (spoken) "Typing out what you said… You can listen to it now." else "Typing out this page…",
                )
            }
            processing == ProcessingState.WAITING_FOR_NETWORK -> InlineAlert(
                title = "No internet right now",
                body = "The $thing is saved on your phone. Its text appears when you're back online.",
                actionLabel = "Try now",
                onAction = onRetry,
            )
            canTypeOut -> InlineAlert(
                title = "This couldn't be typed out",
                body = "The $thing is saved on your phone.",
                icon = LineIcons.Warning,
                actionLabel = "Try again",
                onAction = onRetry,
            )
            else -> InlineAlert(
                title = "Typing out isn't set up yet",
                body = "This build of the app can't type things out. The $thing is saved on your phone.",
                icon = LineIcons.Warning,
            )
        }
    }
}

/** How long a recording runs, in milliseconds; 0 until it has been read. */
@Composable
private fun rememberLength(file: File?): Long {
    val duration by produceState(0L, file?.absolutePath) {
        value = file?.let { VideoMetadata.durationMs(it) } ?: 0L
    }
    return duration
}

/**
 * A voice note as a small play button. [id] tells this note from the others on the
 * page, so only the one that is playing shows as playing. Its length is printed on
 * the button unless the row around it already says it.
 */
@Composable
fun VoiceChip(id: Long, file: File?, voice: VoicePlayback, showLength: Boolean = true) {
    val duration = if (showLength) rememberLength(file) else 0L
    val playing = voice.playingId == id
    Row(
        Modifier
            .clip(Corners.Button)
            .border(1.dp, Palette.FrameLine, Corners.Button)
            .clickable(
                enabled = file != null,
                onClickLabel = if (playing) "Pause the voice note" else "Play the voice note",
                role = Role.Button,
            ) { file?.let { voice.toggle(id, it) } }
            .defaultMinSize(minHeight = 40.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) LineIcons.Pause else LineIcons.Play,
            contentDescription = null,
            tint = Palette.Rose,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (playing) "Pause" else "Play",
            style = AppType.Label,
            color = Palette.Rose,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (duration > 0) {
            Text(
                formatDuration(duration),
                style = AppType.Timecode,
                color = Palette.Muted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** "00:01": a length the way a camera prints it. */
private fun formatTimecode(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun PhotosItem(photos: List<Entry>, onOptions: (Entry) -> Unit, onView: (File) -> Unit) {
    val first = photos.first()
    EntryFrame(
        time = first.time(),
        kind = if (photos.size == 1) "Photo" else "${photos.size} photos",
        icon = LineIcons.Image,
        // With one photo the options are its own; with several, each photo carries them.
        trailing = {
            if (photos.size == 1) {
                IconAction(LineIcons.More, "Options for this photo", { onOptions(first) }, tint = Palette.Muted)
            }
        },
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photos.forEachIndexed { index, photo ->
                Column(horizontalAlignment = Alignment.End) {
                    JournalImage(
                        file = photo.mediaFile,
                        description = "Photo ${index + 1} of ${photos.size}. Open it.",
                        shape = Corners.Field,
                        modifier = Modifier
                            .width(72.dp)
                            .aspectRatio(PHOTO_ASPECT)
                            .clickable(role = Role.Button) { photo.mediaFile?.let(onView) },
                    )
                    if (photos.size > 1) {
                        IconAction(
                            LineIcons.More,
                            "Options for photo ${index + 1}",
                            { onOptions(photo) },
                            tint = Palette.Muted,
                        )
                    }
                }
            }
        }
    }
}

/** The clip on a day page is this wide; its height follows from the clip's shape. */
private val HERO_WIDTH = 150.dp

/** One voice note plays at a time; starting another stops the first. */
class VoicePlayback {
    var playingId by mutableStateOf<Long?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(id: Long, file: File) {
        if (playingId == id) return stop()
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                // This class's stop(), which also clears the row's "playing" state; a
                // bare stop() here would be the media player's own.
                setOnCompletionListener { this@VoicePlayback.stop() }
                prepare()
                start()
            }
            playingId = id
        } catch (e: Exception) {
            stop() // an unreadable recording just does not play
        }
    }

    fun stop() {
        player?.runCatching { release() }
        player = null
        playingId = null
    }
}

@Composable
fun rememberVoicePlayback(): VoicePlayback {
    val playback = remember { VoicePlayback() }
    DisposableEffect(Unit) { onDispose { playback.stop() } }
    // Leaving the app should not leave a voice note talking.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.stop() }
    return playback
}

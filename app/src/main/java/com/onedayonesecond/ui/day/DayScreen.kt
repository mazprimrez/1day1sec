package com.onedayonesecond.ui.day

import android.media.MediaPlayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One row on the day page: an entry, or several photos that were added together. */
private sealed interface EntryRow {
    val key: Long

    data class Single(val entry: Entry) : EntryRow {
        override val key get() = entry.id
    }

    data class Photos(val photos: List<Entry>) : EntryRow {
        override val key get() = photos.first().id
    }
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
    val rows = remember(state.entries) { rowsOf(state.entries) }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back to the calendar") {
            IconAction(LineIcons.Plus, "Add to this day", { adding = true })
            IconAction(LineIcons.More, "Options for this day", { showOptions = true })
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                DayHeader(
                    state = state,
                    onRecord = { onRecord(date) },
                    onMood = { choosingMood = true },
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                )
            }

            if (state.loaded && state.clip == null) {
                item(key = "record") {
                    Column(
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoseButton(
                            if (state.isToday) "Record today's second" else "Record this day's second",
                            { onRecord(date) },
                            Modifier.fillMaxWidth(),
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
                    Column(Modifier.padding(top = 24.dp)) {
                        Text(
                            "How was today?",
                            style = AppType.Heading,
                            color = Palette.Ink,
                            modifier = Modifier.padding(horizontal = ScreenPadding),
                        )
                        MoodRow(selected = null, onPick = viewModel::setMood, Modifier.padding(top = 8.dp))
                    }
                }
            }

            item(key = "gap") { Spacer(Modifier.height(24.dp)) }

            if (state.loaded && rows.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.padding(horizontal = ScreenPadding)) {
                        Hairline()
                        MetaText("Nothing written for this day yet.", Modifier.padding(top = 16.dp))
                        QuietButton("Write a few words", { onWrite(date, -1L) }, Modifier.padding(top = 12.dp))
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
                }
            }

            item(key = "end") { Spacer(Modifier.height(32.dp)) }
        }
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
 * The top of the page: the clip in portrait on the left; on the right the mood face,
 * the big day number, month, weekday and caption, with the place at the bottom, level
 * with the foot of the clip.
 */
@UnstableApi
@Composable
private fun DayHeader(
    state: DayUiState,
    onRecord: () -> Unit,
    onMood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = state.date
    val locale = Locale.getDefault()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val clipWidth = minOf(210.dp, maxWidth * 0.56f)
        val clipHeight = clipWidth / CLIP_ASPECT

        Row(Modifier.fillMaxWidth().height(clipHeight)) {
            val clip = state.clip
            if (clip != null) {
                ClipPlayer(
                    file = clip.file,
                    version = clip.createdAt,
                    label = "Play this day's second",
                    modifier = Modifier.width(clipWidth),
                )
            } else {
                Box(
                    Modifier
                        .width(clipWidth)
                        .aspectRatio(CLIP_ASPECT)
                        .clip(TileShape)
                        .border(1.dp, Palette.Hairline, TileShape)
                        .clickable(enabled = state.loaded, role = Role.Button, onClick = onRecord)
                        .semantics { contentDescription = "No second yet. Record one." },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loaded) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(LineIcons.Plus, contentDescription = null, tint = Palette.Muted)
                            MetaText("No second yet", Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                val mood = state.mood
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onMood)
                        .semantics {
                            contentDescription = if (mood == null) {
                                "No mood yet. Choose one."
                            } else {
                                "Mood: ${mood.label}. Change it."
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (mood == null) MoodPlaceholder(size = 40.dp) else MoodFace(mood, size = 40.dp)
                }
                Text(
                    date.dayOfMonth.toString(),
                    style = AppType.BigDate,
                    color = Palette.Ink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        // Read as one date rather than a bare number followed by loose words.
                        .semantics { contentDescription = RecapLabels.fullDate(date) },
                )
                Text(
                    date.month.getDisplayName(TextStyle.FULL, locale),
                    style = AppType.Heading,
                    color = Palette.Ink,
                )
                MetaText(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                if (clip != null && clip.caption.isNotEmpty()) {
                    Text(
                        clip.caption,
                        style = AppType.Body,
                        color = Palette.Ink,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                state.place?.let { MetaText(it, maxLines = 2) }
            }
        }
    }
}

/** The five faces in a row, hardest day first. Each is a labelled button. */
@Composable
fun MoodRow(selected: Mood?, onPick: (Mood) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = ScreenPadding - 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Mood.entries.forEach { mood ->
            val isSelected = mood == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(TileShape)
                    .clickable(role = Role.RadioButton, onClick = { onPick(mood) })
                    .semantics(mergeDescendants = true) { this.selected = isSelected }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(if (isSelected) Modifier.border(2.dp, Palette.Rose, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    MoodFace(mood, size = 36.dp)
                }
                MetaText(mood.label, Modifier.padding(top = 4.dp), color = if (isSelected) Palette.Ink else Palette.Muted)
            }
        }
    }
}

@Composable
private fun EntryMeta(text: String, onOptions: () -> Unit, optionsLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MetaText(text, Modifier.weight(1f))
        IconAction(LineIcons.More, optionsLabel, onOptions, tint = Palette.Muted)
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
    val label = when (entry.type) {
        EntryType.WRITTEN -> "written"
        EntryType.VOICE -> "voice note"
        EntryType.NOTEBOOK_PHOTO -> "from my notebook"
        EntryType.PEN -> "S Pen"
        EntryType.PHOTO -> "photo"
    }
    Column(Modifier.padding(start = ScreenPadding, end = ScreenPadding - 12.dp, top = 4.dp, bottom = 16.dp)) {
        EntryMeta("${entry.time()}, $label", onOptions, "Options for this entry")

        Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (entry.type) {
                EntryType.VOICE -> VoiceRow(entry, voice)
                EntryType.NOTEBOOK_PHOTO, EntryType.PEN -> entry.mediaFile?.let { file ->
                    JournalImage(
                        file = file,
                        description = if (entry.type == EntryType.PEN) "Pen page. Open it." else "Notebook page. Open it.",
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(PHOTO_ASPECT)
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
                ProcessingNote(entry, canTypeOut, onRetry)
            }
        }
    }
}

/**
 * What is happening to an entry whose words are still to be typed out. Announced
 * politely when it changes; it never takes focus.
 */
@Composable
private fun ProcessingNote(entry: Entry, canTypeOut: Boolean, onRetry: () -> Unit) {
    val thing = when (entry.type) {
        EntryType.VOICE -> "voice note"
        EntryType.PEN -> "page"
        else -> "photo"
    }
    val (text, action) = when (entry.processing) {
        ProcessingState.PENDING ->
            (if (entry.type == EntryType.VOICE) "Writing out what you said…" else "Typing out this page…") to null
        ProcessingState.WAITING_FOR_NETWORK ->
            "No internet right now. The $thing is saved on your phone. Its text will be typed out " +
                "as soon as you're back online." to "Try now"
        else -> if (canTypeOut) {
            "This couldn't be typed out. The $thing is saved on your phone." to "Try again"
        } else {
            "Typing out isn't set up in this build of the app yet. The $thing is saved on your phone." to null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaText(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (action != null) QuietButton(action, onRetry)
    }
}

@Composable
private fun VoiceRow(entry: Entry, voice: VoicePlayback) {
    val file = entry.mediaFile
    val duration by produceState(0L, file?.absolutePath) {
        value = file?.let { VideoMetadata.durationMs(it) } ?: 0L
    }
    val playing = voice.playingId == entry.id
    Row(
        Modifier
            .clip(TileShape)
            .border(1.dp, Palette.Hairline, TileShape)
            .clickable(enabled = file != null, role = Role.Button) { file?.let { voice.toggle(entry.id, it) } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) LineIcons.Pause else LineIcons.Play,
            contentDescription = if (playing) "Pause the voice note" else "Play the voice note",
            tint = Palette.Ink,
        )
        Text(
            formatDuration(duration),
            style = AppType.Body,
            color = Palette.Ink,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun PhotosItem(photos: List<Entry>, onOptions: (Entry) -> Unit, onView: (File) -> Unit) {
    val first = photos.first()
    Column(Modifier.padding(top = 4.dp, bottom = 16.dp)) {
        Row(
            Modifier.padding(start = ScreenPadding, end = ScreenPadding - 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaText(
                "${first.time()}, " + if (photos.size == 1) "photo" else "${photos.size} photos",
                Modifier.weight(1f),
            )
            // With one photo the options are its own; with several, each photo is opened to reach them.
            if (photos.size == 1) {
                IconAction(LineIcons.More, "Options for this photo", { onOptions(first) }, tint = Palette.Muted)
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photos.forEachIndexed { index, photo ->
                Column(horizontalAlignment = Alignment.End) {
                    JournalImage(
                        file = photo.mediaFile,
                        description = "Photo ${index + 1} of ${photos.size}. Open it.",
                        modifier = Modifier
                            .width(120.dp)
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
                setOnCompletionListener { stop() }
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
private fun rememberVoicePlayback(): VoicePlayback {
    val playback = remember { VoicePlayback() }
    DisposableEffect(Unit) { onDispose { playback.stop() } }
    // Leaving the app should not leave a voice note talking.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.stop() }
    return playback
}

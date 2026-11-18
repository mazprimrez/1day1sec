package com.onedayonesecond.ui.books

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.onedayonesecond.ui.theme.AssistChip
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.ui.theme.SwitchRow
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.TonalButton
import com.onedayonesecond.ui.theme.BodyText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.ThoughtDraft
import com.onedayonesecond.data.ThoughtMedia
import com.onedayonesecond.data.ThoughtType
import com.onedayonesecond.ui.common.JournalImage
import com.onedayonesecond.ui.common.PHOTO_ASPECT
import com.onedayonesecond.ui.day.MAX_VOICE_MS
import com.onedayonesecond.ui.day.MIN_VOICE_MS
import com.onedayonesecond.ui.day.VoiceChip
import com.onedayonesecond.ui.day.VoiceRecorder
import com.onedayonesecond.ui.day.formatClock
import com.onedayonesecond.ui.day.rememberVoicePlayback
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppSheet
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.appTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Logging a thought on a page of a book: a few words, a voice note or a photo of the
 * page. Closing keeps what was started as a draft for that book; saving moves the
 * bookmark, but only ever forward.
 *
 * @param onSaved called with the book the thought was saved to, which the picker may have changed.
 */
@UnstableApi
@Composable
fun ThoughtScreen(
    initialBookId: Long,
    onSaved: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val scope = rememberCoroutineScope()
    val voice = rememberVoicePlayback()
    val recorder = remember { VoiceRecorder(context.applicationContext) }

    val books by remember { container.books.observeBooks() }.collectAsStateWithLifecycle(initialValue = null)
    var bookId by rememberSaveable { mutableLongStateOf(initialBookId) }
    val book = books?.firstOrNull { it.id == bookId }

    var page by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var showInDiary by rememberSaveable { mutableStateOf(true) }
    var mediaPath by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaType by rememberSaveable { mutableStateOf<ThoughtType?>(null) }
    // Which book the fields above belong to; 0 until its draft has been read.
    var loadedFor by rememberSaveable { mutableLongStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }

    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    // The fields as they are now, for the draft written when the screen goes away.
    val latest by rememberUpdatedState(
        ThoughtDraft(
            page = page.toIntOrNull() ?: 0,
            text = text,
            showInDiary = showInDiary,
            mediaPath = mediaPath,
            mediaType = mediaType,
        ),
    )
    val latestBook by rememberUpdatedState(book)
    val latestLoadedFor by rememberUpdatedState(loadedFor)
    var saved by remember { mutableStateOf(false) }

    /** Something worth keeping: words, an attachment, or a page that was changed by hand. */
    fun worthKeeping(draft: ThoughtDraft, of: Book): Boolean =
        draft.text.isNotBlank() || draft.mediaPath != null || (draft.page > 0 && draft.page != BookRules.nextPage(of))

    fun keepDraft(of: Book?, draft: ThoughtDraft) {
        if (of == null) return
        container.appScope.launch {
            container.journalPrefs.setThoughtDraft(of.id, draft.takeIf { worthKeeping(it, of) })
        }
    }

    // Reads the draft kept for this book, or starts from the page after the bookmark.
    LaunchedEffect(book?.id) {
        val current = book ?: return@LaunchedEffect
        if (loadedFor == current.id) return@LaunchedEffect
        val draft = container.journalPrefs.thoughtDraft(current.id)
        val attachment = draft?.mediaPath?.takeIf { File(it).exists() }
        page = (draft?.page?.takeIf { it > 0 } ?: BookRules.nextPage(current)).toString()
        text = draft?.text.orEmpty()
        showInDiary = draft?.showInDiary ?: true
        mediaPath = attachment
        mediaType = draft?.mediaType.takeIf { attachment != null }
        loadedFor = current.id
    }

    // Leaving without saving keeps the draft, whichever way the screen is left.
    DisposableEffect(Unit) {
        onDispose {
            recorder.discard()
            if (!saved && latestLoadedFor == latestBook?.id) keepDraft(latestBook, latest)
        }
    }

    fun attach(file: File, type: ThoughtType) {
        mediaPath?.let { File(it).delete() }
        mediaPath = file.absolutePath
        mediaType = type
    }

    fun removeAttachment() {
        voice.stop()
        mediaPath?.let { File(it).delete() }
        mediaPath = null
        mediaType = null
    }

    val photos = rememberPhotoPicker { uri ->
        scope.launch {
            val file = container.bookActions.readPhoto(uri)
            if (file == null) {
                container.messages.show("That photo could not be read. Try another one.")
            } else {
                attach(file, ThoughtType.PAGE_PHOTO)
            }
        }
    }

    fun startRecording() {
        voice.stop()
        try {
            recorder.start(container.storage.newScratchFile("m4a"))
            elapsedMs = 0
            recording = true
        } catch (e: Exception) {
            container.messages.show("Recording could not start. Check that no other app is using the microphone.")
        }
    }

    fun stopRecording() {
        recording = false
        val file = recorder.finish()
        if (file == null || elapsedMs < MIN_VOICE_MS) {
            file?.delete()
            container.messages.show("That was too short to keep. Hold on a little longer and try again.")
        } else {
            attach(file, ThoughtType.VOICE)
        }
    }

    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecording()
        } else {
            container.messages.show(
                "The microphone isn't allowed for Kilasan. Allow it in your phone's Settings, under Apps.",
            )
        }
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAt
            if (elapsedMs >= MAX_VOICE_MS) {
                stopRecording()
                break
            }
            delay(200)
        }
    }

    fun switchTo(other: Book) {
        picking = false
        if (other.id == bookId) return
        if (recording) stopRecording()
        voice.stop()
        // What was started stays with the book it was started for.
        if (loadedFor == book?.id) keepDraft(book, latest)
        mediaPath = null
        mediaType = null
        bookId = other.id
    }

    val pageNumber = page.toIntOrNull()
    val hasContent = text.isNotBlank() || mediaPath != null
    val canSave = book != null && loadedFor == book.id && pageNumber != null && pageNumber >= 1 &&
        hasContent && !saving && !recording

    fun save() {
        val current = book ?: return
        val number = pageNumber ?: return
        if (!canSave) return
        saving = true
        saved = true
        voice.stop()
        val media = mediaPath?.let(::File)?.takeIf { it.exists() }?.let { file ->
            val type = mediaType ?: ThoughtType.PAGE_PHOTO
            ThoughtMedia(type, file, if (type == ThoughtType.VOICE) "m4a" else "jpg")
        }
        val words = text
        val inDiary = showInDiary
        // On the app's scope, so the thought is saved even if this screen is left mid-save.
        container.appScope.launch {
            val clamped = BookRules.clampPage(current, number)
            container.bookActions.logThought(current, clamped, words, media, inDiary)
            container.journalPrefs.setThoughtDraft(current.id, null)
            container.messages.show(
                if (BookRules.movesBookmark(current, clamped)) {
                    "Thought saved. Your bookmark is on page $clamped."
                } else {
                    "Thought saved on page $clamped."
                },
            )
            // Only if this screen is still the one showing: otherwise "back" would leave some other screen.
            if (scope.isActive) onSaved(current.id)
        }
    }

    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(
            onClose,
            backLabel = "Close. What you've started is kept.",
            title = "Log a thought",
            backIcon = LineIcons.Close,
        ) {
            TextAction(if (saving) "Saving…" else "Save", ::save, enabled = canSave)
        }
        if (books != null && book == null) {
            // The book was removed while this was open.
            StateCard("That book is no longer on your shelf", Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)) {
                BodyText("It was removed while this page was open, so there is nowhere to save a thought.")
                TextAction("Back to your books", onClose, Modifier.offset(x = (-12).dp))
            }
            return@Column
        }
        if (book == null) return@Column
        val reading = books.orEmpty().filter { it.status == BookStatus.READING || it.id == book.id }
        val inset = Modifier.padding(horizontal = ScreenPadding)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            // Which book: its cover, its title, and where the bookmark is now.
            Row(
                inset
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable(
                        enabled = reading.size > 1,
                        onClickLabel = "Choose another book",
                        role = Role.Button,
                    ) { picking = true }
                    .semantics(mergeDescendants = true) { contentDescription = "Book: ${book.title}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book, Modifier.width(36.dp).aspectRatio(COVER_ASPECT), maxSidePx = 192)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(book.title, style = AppType.Heading, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SmallText(if (book.currentPage > 0) "Bookmark on page ${book.currentPage}" else "Not started yet")
                }
                if (reading.size > 1) {
                    Icon(LineIcons.ChevronDown, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
                }
            }

            // The page: a field for the number, with a step either side for the common case of one page on.
            val last = BookRules.lastPage(book)
            val clamped = pageNumber?.let { BookRules.clampPage(book, it) }
            Row(inset.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = page,
                    onValueChange = { typed -> page = typed.filter(Char::isDigit).take(4) },
                    label = { Text("Page") },
                    singleLine = true,
                    textStyle = AppType.NumeralMedium.copy(fontSize = 16.sp),
                    shape = Corners.Field,
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                IconAction(
                    LineIcons.Minus,
                    "One page earlier",
                    { page = ((pageNumber ?: 1) - 1).coerceIn(1, last).toString() },
                    Modifier.padding(start = 4.dp, top = 8.dp),
                    enabled = (pageNumber ?: 1) > 1,
                )
                IconAction(
                    LineIcons.Plus,
                    "One page later",
                    { page = ((pageNumber ?: 0) + 1).coerceIn(1, last).toString() },
                    Modifier.padding(top = 8.dp).offset(x = 8.dp),
                    enabled = (pageNumber ?: 0) < last,
                )
            }
            SmallText(
                when {
                    pageNumber != null && book.totalPages != null && book.totalPages > 0 && pageNumber > book.totalPages ->
                        "This edition has ${book.totalPages} pages. If yours has more, change it under Edit book."
                    clamped != null && BookRules.movesBookmark(book, clamped) ->
                        "Saving moves your bookmark to $clamped. Earlier pages don't move it."
                    book.currentPage > 0 -> "Your bookmark stays on ${book.currentPage}. Earlier pages don't move it."
                    else -> "The page you're on. Saving puts your bookmark there."
                },
                inset.padding(start = 16.dp, top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Your thought") },
                placeholder = { Text("What stayed with you on this page?", style = AppType.Body, color = Palette.Muted) },
                textStyle = AppType.Body,
                shape = Corners.Field,
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = inset.fillMaxWidth().padding(top = 24.dp).defaultMinSize(minHeight = 160.dp),
            )

            // One attachment: a photo of the page, or a voice note.
            val attached = mediaPath?.let(::File)
            when {
                recording -> Row(inset.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Rose).clearAndSetSemantics { })
                    Text(
                        formatClock(elapsedMs),
                        style = AppType.Timecode,
                        color = Palette.Ink,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp).semantics { contentDescription = "Recording" },
                    )
                    TonalButton("Stop and keep", ::stopRecording)
                    TextAction(
                        "Discard",
                        {
                            recording = false
                            recorder.discard()
                        },
                        color = Palette.Ink,
                    )
                }

                attached != null && mediaType == ThoughtType.VOICE ->
                    Column(inset.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VoiceChip(DRAFT_VOICE_ID, attached, voice)
                            TextAction("Remove", ::removeAttachment, color = Palette.Ink)
                        }
                        SmallText(
                            if (text.isBlank()) {
                                "What you said is written out after you save."
                            } else {
                                "Your typed words are kept as they are; the voice note is saved beside them."
                            },
                        )
                    }

                attached != null -> Row(inset.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    JournalImage(
                        file = attached,
                        description = "The photo of the page",
                        modifier = Modifier.width(60.dp).aspectRatio(PHOTO_ASPECT),
                    )
                    TextAction("Remove", ::removeAttachment, Modifier.padding(start = 4.dp), color = Palette.Ink)
                }

                else -> Row(inset.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip("Photo of the page", LineIcons.Camera, { photoSheet = true })
                    AssistChip(
                        "Voice note",
                        LineIcons.Mic,
                        {
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (allowed) startRecording() else microphone.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                }
            }

            SwitchRow(
                "Also show it in today's diary",
                showInDiary,
                { showInDiary = it },
                Modifier.padding(top = 16.dp),
            )
            if (!hasContent) {
                SmallText(
                    "Write a line, record a voice note or add a photo of the page, then save.",
                    inset.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (picking) {
            AppSheet(onDismiss = { picking = false }, title = "Which book?") {
                reading.forEach { other ->
                    ActionRow(
                        other.title,
                        { switchTo(other) },
                        subtitle = BookRules.pageLine(other) ?: other.author,
                        trailing = if (other.id == book.id) {
                            { Icon(LineIcons.Check, contentDescription = "Chosen", tint = Palette.Rose) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (photoSheet) {
            AppSheet(onDismiss = { photoSheet = false }, title = "Photo of the page") {
                ActionRow("Take a photo", { photoSheet = false; photos.take() }, icon = LineIcons.Camera)
                ActionRow("Choose from your photos", { photoSheet = false; photos.choose() }, icon = LineIcons.Image)
            }
        }
    }
}

/** The voice note not saved yet. Saved thoughts have positive ids, so it can never be mistaken for one. */
private const val DRAFT_VOICE_ID = Long.MIN_VALUE

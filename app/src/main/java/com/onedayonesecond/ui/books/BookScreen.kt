package com.onedayonesecond.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookDetails
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.ui.day.PhotoViewer
import com.onedayonesecond.ui.day.VoicePlayback
import com.onedayonesecond.ui.day.rememberVoicePlayback
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.AppSheet
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.ButtonShape
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MessageAction
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText
import com.onedayonesecond.ui.theme.appTextFieldColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * One book: where the bookmark is, and every thought logged on it, by page with the
 * furthest page first. The thoughts are the point of the page; the summary is a row
 * at the end of them.
 */
@UnstableApi
@Composable
fun BookScreen(
    bookId: Long,
    onBack: () -> Unit,
    onAddThought: () -> Unit,
    onSummary: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val scope = rememberCoroutineScope()
    val voice = rememberVoicePlayback()

    val book by remember(bookId) { container.books.observeBook(bookId) }.collectAsStateWithLifecycle(initialValue = null)
    // Null until the first read, so "Log your first thought" does not flash on a book that has some.
    val thoughts by remember(bookId) { container.books.observeThoughts(bookId) }
        .collectAsStateWithLifecycle(initialValue = null)

    // A book that is gone (removed here, or by Undo after adding) has no page to show.
    LaunchedEffect(bookId) {
        if (container.books.observeBook(bookId).first() == null) onBack()
    }

    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var changingCover by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<File?>(null) }
    var thoughtMenu by remember { mutableStateOf<BookThought?>(null) }
    var editingThought by remember { mutableStateOf<BookThought?>(null) }
    var deletingThought by remember { mutableStateOf<BookThought?>(null) }

    val photos = rememberPhotoPicker { uri ->
        val current = book ?: return@rememberPhotoPicker
        scope.launch {
            if (!container.bookActions.useCoverPhoto(current, uri)) {
                container.messages.show("That photo could not be read. Try another one.")
            }
        }
    }

    val current = book
    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back to your books") {
            if (current != null) IconAction(LineIcons.More, "More for this book", { showMenu = true })
        }
        if (current == null) return@Column
        val all = thoughts

        LazyColumn(Modifier.weight(1f)) {
            item(key = "header") { BookHeader(current, onCover = { changingCover = true }) }

            if (all != null && all.isEmpty()) {
                item(key = "first") {
                    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 28.dp)) {
                        Hairline(Modifier.padding(bottom = 20.dp))
                        HeadingText("Log your first thought")
                        BodyText(
                            "When a page stays with you, write a line about it, record a voice note, " +
                                "or take a photo of the page.",
                            Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else if (all != null) {
                item(key = "thoughts-heading") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 28.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        HeadingText("Your thoughts, by page", Modifier.weight(1f))
                        MetaText("Furthest page first")
                    }
                }
                val shown = if (showAll) all else all.take(BookRules.THOUGHTS_SHOWN)
                items(shown, key = { it.id }) { thought ->
                    ThoughtRow(
                        thought = thought,
                        voice = voice,
                        canTypeOut = container.thoughtProcessor.isConfigured,
                        onRetry = { container.appScope.launch { container.bookActions.retry(thought) } },
                        onView = { viewing = it },
                        onMore = { thoughtMenu = thought },
                    )
                    Hairline(Modifier.padding(horizontal = ScreenPadding))
                }
                val hidden = all.size - shown.size
                if (hidden > 0) {
                    item(key = "earlier") {
                        QuietButton(
                            "Show $hidden earlier ${if (hidden == 1) "thought" else "thoughts"}",
                            { showAll = true },
                            Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                        )
                    }
                }
                item(key = "summary") {
                    Column(Modifier.padding(top = 8.dp)) {
                        ActionRow(
                            "Summary and review ideas",
                            onSummary,
                            subtitle = BookLabels.summaryLine(BookRules.withWords(all).size),
                            trailing = {
                                Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted)
                            },
                        )
                        Hairline(Modifier.padding(horizontal = ScreenPadding))
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }

        RoseButton(
            "Add a thought",
            onAddThought,
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp),
        )
    }

    if (current != null) {
        if (showMenu) {
            BookMenu(
                book = current,
                onFinish = {
                    showMenu = false
                    scope.launch {
                        if (current.status == BookStatus.READING) {
                            container.books.finish(current)
                            container.messages.show(
                                "${current.title} marked as finished.",
                                MessageAction("Undo", run = { container.books.readAgain(current) }),
                            )
                        } else {
                            container.books.readAgain(current)
                        }
                    }
                },
                onEdit = { showMenu = false; editing = true },
                onCover = { showMenu = false; changingCover = true },
                onRemove = { showMenu = false; removing = true },
                onDismiss = { showMenu = false },
            )
        }
        if (editing) {
            EditBookDialog(
                book = current,
                onSave = { details, page ->
                    editing = false
                    scope.launch { container.books.update(current, details, page) }
                },
                onDismiss = { editing = false },
            )
        }
        if (changingCover) {
            AppSheet(onDismiss = { changingCover = false }, title = "Change cover") {
                val divider = Modifier.padding(horizontal = ScreenPadding)
                ActionRow("Take a photo of your copy", { changingCover = false; photos.take() }, icon = LineIcons.Image)
                Hairline(divider)
                ActionRow("Choose from your photos", { changingCover = false; photos.choose() }, icon = LineIcons.Image)
                if (current.coverPath != null) {
                    Hairline(divider)
                    ActionRow(
                        "Use no cover",
                        {
                            changingCover = false
                            scope.launch { container.books.setCover(current, null) }
                        },
                        subtitle = "The title is printed on a plain cover instead.",
                    )
                }
            }
        }
        if (removing) {
            val count = thoughts?.size ?: 0
            AppDialog(
                onDismiss = { removing = false },
                title = "Remove ${current.title}?",
                buttons = {
                    QuietButton(
                        "Remove",
                        {
                            removing = false
                            // On the app's scope: this page closes while the book is being removed.
                            container.appScope.launch {
                                container.books.remove(current)
                                container.messages.show("${current.title} removed.")
                            }
                            onBack()
                        },
                    )
                    RoseButton("Keep", { removing = false })
                },
            ) {
                BodyText(
                    if (count == 0) {
                        "The book is taken off your shelf. This can't be undone."
                    } else {
                        "The book and your ${BookLabels.thoughts(count)} on it are deleted from this phone, " +
                            "and from Google Drive at the next backup. This can't be undone."
                    },
                )
            }
        }
    }

    thoughtMenu?.let { thought ->
        AppSheet(
            onDismiss = { thoughtMenu = null },
            title = "Page ${thought.page}",
            meta = BookLabels.moment(thought.createdAt),
        ) {
            ActionRow(
                if (thought.text.isNullOrEmpty()) "Edit the page, or add words" else "Edit the page or the words",
                { thoughtMenu = null; editingThought = thought },
                icon = LineIcons.Write,
            )
            Hairline(Modifier.padding(horizontal = ScreenPadding))
            ActionRow("Delete this thought", { thoughtMenu = null; deletingThought = thought }, icon = LineIcons.Trash)
        }
    }
    editingThought?.let { thought ->
        EditThoughtDialog(
            book = current ?: return@let,
            thought = thought,
            otherPages = thoughts.orEmpty().filter { it.id != thought.id }.map { it.page },
            onSave = { page, text ->
                editingThought = null
                scope.launch { container.books.editThought(thought, page, text) }
            },
            onDismiss = { editingThought = null },
        )
    }
    deletingThought?.let { thought ->
        AppDialog(
            onDismiss = { deletingThought = null },
            title = "Delete this thought?",
            buttons = {
                QuietButton(
                    "Delete",
                    {
                        deletingThought = null
                        scope.launch { container.books.deleteThought(thought) }
                    },
                )
                RoseButton("Keep", { deletingThought = null })
            },
        ) {
            BodyText(
                "Your thought on page ${thought.page} is deleted. Your bookmark stays where it is. " +
                    "This can't be undone.",
            )
        }
    }
    viewing?.let { file -> PhotoViewer(file, onDismiss = { viewing = null }) }
}

/** Cover on the left; title, author and the bookmark on the right. */
@Composable
private fun BookHeader(book: Book, onCover: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 4.dp)) {
        BookCover(
            book,
            Modifier
                .width(132.dp)
                .aspectRatio(COVER_ASPECT)
                .clickable(
                    onClickLabel = if (book.coverPath == null) "Use a photo of your copy" else "Change the cover",
                    role = Role.Button,
                    onClick = onCover,
                )
                .semantics { contentDescription = "Cover of ${book.title}" },
            maxSidePx = 768,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            TitleText(book.title, Modifier.semantics { heading() })
            book.author?.let { MetaText(it, Modifier.padding(top = 4.dp)) }

            val total = book.totalPages?.takeIf { it > 0 }
            val bookmark = when {
                book.currentPage <= 0 -> "Not started yet"
                total != null -> "On page ${book.currentPage} of $total"
                else -> "On page ${book.currentPage}"
            }
            // Read aloud as one line; the big figure and its caption are one fact.
            Column(Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { contentDescription = bookmark }) {
                if (book.currentPage > 0) {
                    Text(book.currentPage.toString(), style = BigPage, color = Palette.Ink)
                    MetaText(if (total != null) "of $total pages" else "your current page")
                } else {
                    MetaText(if (total != null) "$total pages. Not started yet." else "Not started yet.")
                }
            }
            BookRules.progress(book)?.let { ProgressLine(it, Modifier.padding(top = 10.dp)) }
            MetaText(
                buildString {
                    append("Started ").append(BookLabels.day(book.startedOn))
                    book.finishedOn?.let { append("\nFinished ").append(BookLabels.day(it)) }
                },
                Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** One thought: its page in a column on the left, then when it was logged and what it holds. */
@Composable
private fun ThoughtRow(
    thought: BookThought,
    voice: VoicePlayback,
    canTypeOut: Boolean,
    onRetry: () -> Unit,
    onView: (File) -> Unit,
    onMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 4.dp, top = 16.dp, bottom = 16.dp)) {
        Text(
            thought.page.toString(),
            style = ThoughtPage,
            color = Palette.Ink,
            modifier = Modifier.width(52.dp).semantics { contentDescription = "Page ${thought.page}" },
        )
        Column(Modifier.weight(1f)) {
            MetaText(BookLabels.moment(thought.createdAt))
            ThoughtBody(
                thought = thought,
                voice = voice,
                voiceId = thought.id,
                canTypeOut = canTypeOut,
                onRetry = onRetry,
                onView = onView,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        IconAction(LineIcons.More, "More for your thought on page ${thought.page}", onMore, tint = Palette.Muted)
    }
}

@Composable
private fun BookMenu(
    book: Book,
    onFinish: () -> Unit,
    onEdit: () -> Unit,
    onCover: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val divider = Modifier.padding(horizontal = ScreenPadding)
    AppSheet(onDismiss = onDismiss, title = book.title, meta = book.author) {
        if (book.status == BookStatus.READING) {
            ActionRow(
                "Mark as finished",
                onFinish,
                subtitle = "It moves to Finished this year. Your thoughts stay.",
                icon = LineIcons.Check,
            )
        } else {
            ActionRow("Read it again", onFinish, subtitle = "It moves back to Reading now.", icon = LineIcons.Books)
        }
        Hairline(divider)
        ActionRow("Edit book", onEdit, subtitle = "Title, author, pages, where your bookmark is.", icon = LineIcons.Write)
        Hairline(divider)
        ActionRow("Change cover", onCover, icon = LineIcons.Image)
        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(8.dp))
        ActionRow("Remove book", onRemove, subtitle = "Deletes the book and your thoughts on it.", icon = LineIcons.Trash)
    }
}

/**
 * Fixing what search got wrong, or what was typed by hand. The bookmark can be set
 * here too: it is the one place it can be moved back.
 */
@Composable
private fun EditBookDialog(book: Book, onSave: (BookDetails, Int) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(book.title) }
    var author by rememberSaveable { mutableStateOf(book.author.orEmpty()) }
    var pages by rememberSaveable { mutableStateOf(book.totalPages?.toString().orEmpty()) }
    var page by rememberSaveable { mutableStateOf(book.currentPage.toString()) }

    AppDialog(
        onDismiss = onDismiss,
        title = "Edit book",
        buttons = {
            QuietButton("Cancel", onDismiss)
            RoseButton(
                "Save",
                {
                    onSave(
                        BookDetails(title, author, book.isbn, book.edition, pages.toIntOrNull()),
                        page.toIntOrNull() ?: 0,
                    )
                },
                enabled = title.isNotBlank(),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Title", title, { title = it }, capitalization = KeyboardCapitalization.Words)
            Field("Author", author, { author = it }, capitalization = KeyboardCapitalization.Words)
            Field("Pages in your edition", pages, { text -> pages = text.filter(Char::isDigit).take(4) }, KeyboardType.Number)
            Field(
                "Your current page",
                page,
                { text -> page = text.filter(Char::isDigit).take(4) },
                KeyboardType.Number,
                supporting = "Logging a thought only moves this forward. Here you can move it back.",
            )
        }
    }
}

/**
 * Putting a logged thought right: the page it was logged on, its words, or both. The
 * line under the page says what saving does to the bookmark, when it does anything.
 */
@Composable
private fun EditThoughtDialog(
    book: Book,
    thought: BookThought,
    otherPages: List<Int>,
    onSave: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(thought.page.toString()) }
    var text by rememberSaveable { mutableStateOf(thought.text.orEmpty()) }
    val typed = page.toIntOrNull()
    val lastPage = BookRules.lastPage(book)
    val valid = typed != null && typed in 1..lastPage
    val bookmark = if (valid) BookRules.bookmarkAfterPageChange(book, thought.page, typed!!, otherPages) else book.currentPage
    val pageNote = when {
        typed == null || typed < 1 -> "Type the page this thought is about."
        typed > lastPage -> "Your edition has $lastPage pages. That can be changed under Edit book."
        bookmark != book.currentPage -> "Saving moves your bookmark to page $bookmark."
        else -> null
    }
    AppDialog(
        onDismiss = onDismiss,
        title = "Edit thought",
        buttons = {
            QuietButton("Cancel", onDismiss)
            RoseButton("Save", { typed?.let { onSave(it, text) } }, enabled = valid)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                "Page",
                page,
                { typedText -> page = typedText.filter(Char::isDigit).take(4) },
                KeyboardType.Number,
                supporting = pageNote,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What stayed with you on this page?", style = AppType.Body, color = Palette.Muted) },
                textStyle = AppType.Body,
                shape = ButtonShape,
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your words for this thought" },
            )
        }
    }
}

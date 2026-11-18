package com.onedayonesecond.ui.books

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.onedayonesecond.ui.theme.FabClearance
import com.onedayonesecond.ui.theme.FloatingAction
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.SearchBar
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.ui.theme.TabHeader
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.TonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import java.time.LocalDate

/** How many finished books the shelf shows before "See all". One row of three. */
private const val FINISHED_SHOWN = 3

/**
 * The Books tab: what is being read now, each with its bookmark and a way to log a
 * thought on it, and what was finished this year.
 */
@UnstableApi
@Composable
fun BooksScreen(
    onAdd: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onLogThought: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    // Null until the first read, so the shelf does not flash "empty".
    val books by remember { container.books.observeBooks() }.collectAsStateWithLifecycle(initialValue = null)
    val year = remember { LocalDate.now().year }

    var showAllFinished by rememberSaveable { mutableStateOf(false) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    val shelf = books
    val reading = shelf.orEmpty().filter { it.status == BookStatus.READING }
    val finished = shelf.orEmpty().filter { it.status == BookStatus.FINISHED }
        .sortedWith(compareByDescending<Book> { it.finishedOn }.thenByDescending { it.id })
    val thisYear = finished.filter { it.finishedOn?.year == year }
    val earlier = finished.filter { it.finishedOn?.year != year }.groupBy { it.finishedOn?.year }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search your books",
                    onBack = {
                        searching = false
                        query = ""
                    },
                    backLabel = "Close the search",
                    focusRequester = searchFocus,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                )
            } else {
                TabHeader(
                    "Books",
                    subtitle = if (shelf.isNullOrEmpty()) null else "${reading.size} reading · ${thisYear.size} finished in $year",
                ) {
                    if (!shelf.isNullOrEmpty()) IconAction(LineIcons.Search, "Search your books", { searching = true })
                    IconAction(LineIcons.Settings, "Settings", onSettings)
                }
            }

            if (shelf == null) return@Column
            if (shelf.isEmpty()) {
                StateCard("Your shelf is empty", Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)) {
                    BodyText(
                        "Add the book you're reading now. Searching by title or scanning the barcode " +
                            "fills in the cover for you.",
                    )
                    RoseButton("Add a book", onAdd)
                }
                return@Column
            }

            if (searching) {
                val words = query.trim()
                val found = if (words.isEmpty()) {
                    emptyList()
                } else {
                    shelf.filter { it.title.contains(words, ignoreCase = true) || it.author?.contains(words, ignoreCase = true) == true }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (words.isNotEmpty()) {
                        item(key = "found-count") {
                            MetaText(
                                if (found.size == 1) "1 book on your shelf" else "${found.size} books on your shelf",
                                Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    items(found, key = { "found-${it.id}" }) { book -> FoundRow(book) { onOpenBook(book.id) } }
                    if (words.isNotEmpty() && found.isEmpty()) {
                        item(key = "found-none") {
                            StateCard("No books match “$words”", Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)) {
                                BodyText("Check the spelling or search by the author. To look for a new book, add one.")
                                TextAction("Add a book", onAdd, Modifier.offset(x = (-12).dp))
                            }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(FabClearance)) }
                }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "reading-heading") {
                    HeadingText("Reading now", Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp))
                }
                if (reading.isEmpty()) {
                    item(key = "reading-none") {
                        MetaText(
                            "Nothing on the go right now. Add the book you pick up next.",
                            Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                itemsIndexed(reading, key = { _, book -> "reading-${book.id}" }) { index, book ->
                    if (index > 0) Hairline(Modifier.padding(horizontal = ScreenPadding))
                    ReadingRow(book, onOpen = { onOpenBook(book.id) }, onLog = { onLogThought(book.id) })
                }

                if (thisYear.isNotEmpty()) {
                    item(key = "finished-heading") {
                        Row(
                            Modifier.padding(start = ScreenPadding, end = 12.dp).padding(top = 16.dp, bottom = 12.dp).defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeadingText("Finished this year", Modifier.weight(1f))
                            if (thisYear.size > FINISHED_SHOWN) {
                                TextAction(
                                    if (showAllFinished) "Show fewer" else "See all ${thisYear.size}",
                                    { showAllFinished = !showAllFinished },
                                )
                            }
                        }
                    }
                    val shown = if (showAllFinished) thisYear else thisYear.take(FINISHED_SHOWN)
                    itemsIndexed(shown.chunked(3), key = { _, row -> "finished-${row.first().id}" }) { index, row ->
                        CoverRow(row, firstNumber = index * 3 + 1, onOpen = onOpenBook)
                    }
                }

                if (earlier.isNotEmpty()) {
                    item(key = "earlier-toggle") {
                        TextAction(
                            if (showEarlier) "Hide earlier years" else "Earlier years (${earlier.values.sumOf { it.size }})",
                            { showEarlier = !showEarlier },
                            Modifier.padding(start = 12.dp, top = 8.dp),
                            icon = if (showEarlier) LineIcons.ChevronUp else LineIcons.ChevronDown,
                        )
                    }
                    if (showEarlier) {
                        earlier.forEach { (finishedIn, inYear) ->
                            item(key = "earlier-heading-$finishedIn") {
                                HeadingText(
                                    if (finishedIn == null) "Finished" else "Finished in $finishedIn",
                                    Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp, bottom = 12.dp),
                                )
                            }
                            itemsIndexed(inYear.chunked(3), key = { _, row -> "earlier-${row.first().id}" }) { index, row ->
                                CoverRow(row, firstNumber = index * 3 + 1, onOpen = onOpenBook)
                            }
                        }
                    }
                }

                item(key = "end") { Spacer(Modifier.height(FabClearance)) }
            }
        }

        // The empty shelf has its own rose button; one is enough.
        if (!shelf.isNullOrEmpty()) {
            FloatingAction(
                "Add a book",
                LineIcons.Plus,
                onAdd,
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

/** A book found by searching the shelf: small cover, title, author, where the bookmark is. */
@Composable
private fun FoundRow(book: Book, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open this book", role = Role.Button, onClick = onOpen)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.width(48.dp).aspectRatio(COVER_ASPECT), maxSidePx = 256)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(book.title, style = AppType.Heading, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.author?.let { MetaText(it, maxLines = 1) }
            SmallText(if (book.status == BookStatus.FINISHED) "Finished" else "Reading now", Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * A book being read. The way to log a thought sits on the book it belongs to, and
 * says which book when read aloud.
 */
@Composable
private fun ReadingRow(book: Book, onOpen: () -> Unit, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 16.dp)) {
        // Tapping the cover opens the book too; read aloud, the title beside it is the button.
        BookCover(
            book,
            Modifier.width(72.dp).aspectRatio(COVER_ASPECT).clearAndSetSemantics { }.clickable(onClick = onOpen),
            maxSidePx = 256,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open this book", role = Role.Button, onClick = onOpen),
            ) {
                Text(book.title, style = AppType.Heading, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { MetaText(it, maxLines = 1) }
            }
            if (book.currentPage > 0) {
                PageCount(book, Modifier.padding(top = 12.dp))
            } else {
                Text(
                    book.totalPages?.takeIf { it > 0 }?.let { "Not started · $it pages" } ?: "Not started",
                    style = AppType.Small,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            BookRules.progress(book)?.let { ProgressLine(it, Modifier.padding(top = 6.dp)) }

            TonalButton(
                "Log a thought",
                onLog,
                Modifier.padding(top = 12.dp).semantics { contentDescription = "Log a thought in ${book.title}" },
                icon = LineIcons.Write,
            )
        }
    }
}

/** Up to three finished books side by side: the cover, numbered under it like a frame. */
@Composable
private fun CoverRow(books: List<Book>, firstNumber: Int, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        books.forEachIndexed { index, book ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open this book", role = Role.Button) { onOpen(book.id) }
                    .semantics(mergeDescendants = true) { contentDescription = book.title },
            ) {
                BookCover(book, Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT), maxSidePx = 384)
                Text(
                    "%02d".format(firstNumber + index),
                    style = AppType.FrameNumber,
                    color = Palette.Ink,
                    modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { },
                )
            }
        }
        // Keeps a last row of one or two the same width as a full row.
        repeat(3 - books.size) { Spacer(Modifier.weight(1f)) }
    }
}

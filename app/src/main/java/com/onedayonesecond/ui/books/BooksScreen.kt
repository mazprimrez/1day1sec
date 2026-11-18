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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText
import java.time.LocalDate

/** How many finished books the shelf shows before "See all". Two rows of three. */
private const val FINISHED_SHOWN = 6

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
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    // Null until the first read, so the shelf does not flash "empty".
    val books by remember { container.books.observeBooks() }.collectAsStateWithLifecycle(initialValue = null)
    val year = remember { LocalDate.now().year }

    var showAllFinished by rememberSaveable { mutableStateOf(false) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleText("Books", Modifier.weight(1f))
            IconAction(LineIcons.Plus, "Add a book", onAdd)
        }

        val shelf = books ?: return@Column
        if (shelf.isEmpty()) {
            EmptyShelf(onAdd)
            return@Column
        }

        val reading = shelf.filter { it.status == BookStatus.READING }
        val finished = shelf.filter { it.status == BookStatus.FINISHED }
            .sortedWith(compareByDescending<Book> { it.finishedOn }.thenByDescending { it.id })
        val thisYear = finished.filter { it.finishedOn?.year == year }
        val earlier = finished.filter { it.finishedOn?.year != year }.groupBy { it.finishedOn?.year }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "reading-heading") {
                HeadingText("Reading now", Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp))
            }
            if (reading.isEmpty()) {
                item(key = "reading-none") {
                    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)) {
                        MetaText("Nothing on the go right now.")
                        QuietButton("Add a book", onAdd, Modifier.padding(top = 12.dp))
                    }
                }
            }
            items(reading, key = { "reading-${it.id}" }) { book ->
                ReadingRow(book, onOpen = { onOpenBook(book.id) }, onLog = { onLogThought(book.id) })
                Hairline(Modifier.padding(horizontal = ScreenPadding))
            }

            if (thisYear.isNotEmpty()) {
                item(key = "finished-heading") {
                    HeadingText(
                        "Finished this year",
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp, bottom = 12.dp),
                    )
                }
                val shown = if (showAllFinished) thisYear else thisYear.take(FINISHED_SHOWN)
                items(shown.chunked(3), key = { row -> "finished-${row.first().id}" }) { row ->
                    CoverRow(row, onOpenBook)
                }
                if (thisYear.size > FINISHED_SHOWN) {
                    item(key = "finished-all") {
                        QuietButton(
                            if (showAllFinished) "Show fewer" else "See all ${thisYear.size}",
                            { showAllFinished = !showAllFinished },
                            Modifier.padding(horizontal = ScreenPadding),
                        )
                    }
                }
            }

            if (earlier.isNotEmpty()) {
                item(key = "earlier-toggle") {
                    QuietButton(
                        if (showEarlier) "Hide earlier years" else "Earlier years (${earlier.values.sumOf { it.size }})",
                        { showEarlier = !showEarlier },
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp),
                    )
                }
                if (showEarlier) {
                    earlier.forEach { (finishedIn, inYear) ->
                        item(key = "earlier-heading-$finishedIn") {
                            HeadingText(
                                if (finishedIn == null) "Finished" else "Finished in $finishedIn",
                                Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp, bottom = 12.dp),
                            )
                        }
                        items(inYear.chunked(3), key = { row -> "earlier-${row.first().id}" }) { row ->
                            CoverRow(row, onOpenBook)
                        }
                    }
                }
            }

            item(key = "end") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        HeadingText("Your shelf is empty")
        BodyText(
            "Add the book you're reading. Then log a thought whenever a page stays with you.",
            Modifier.padding(top = 8.dp),
        )
        RoseButton("Add a book", onAdd, Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/**
 * A book being read. The way to log a thought sits on the book it belongs to, and
 * says which book when read aloud.
 */
@Composable
private fun ReadingRow(book: Book, onOpen: () -> Unit, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 8.dp)) {
        // Tapping the cover opens the book too; read aloud, the title beside it is the button.
        BookCover(
            book,
            Modifier.width(64.dp).aspectRatio(COVER_ASPECT).clearAndSetSemantics { }.clickable(onClick = onOpen),
            maxSidePx = 256,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open this book", role = Role.Button, onClick = onOpen),
            ) {
                Text(book.title, style = AppType.BodyStrong, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { MetaText(it, maxLines = 1) }
            }
            BookRules.progress(book)?.let { ProgressLine(it, Modifier.padding(top = 10.dp)) }
            BookRules.pageLine(book)?.let { MetaText(it, Modifier.padding(top = 6.dp)) }

            Row(
                Modifier
                    .clickable(role = Role.Button, onClick = onLog)
                    .semantics(mergeDescendants = true) { contentDescription = "Log a thought in ${book.title}" }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LineIcons.Plus, contentDescription = null, tint = Palette.Rose, modifier = Modifier.size(20.dp))
                Text(
                    "Log a thought",
                    style = AppType.BodyStrong,
                    color = Palette.Rose,
                    modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/** Up to three finished books side by side: cover, then title. */
@Composable
private fun CoverRow(books: List<Book>, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        books.forEach { book ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open this book", role = Role.Button) { onOpen(book.id) },
            ) {
                BookCover(book, Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT), maxSidePx = 384)
                Text(
                    book.title,
                    style = AppType.Meta,
                    color = Palette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // Keeps a last row of one or two the same width as a full row.
        repeat(3 - books.size) { Spacer(Modifier.weight(1f)) }
    }
}

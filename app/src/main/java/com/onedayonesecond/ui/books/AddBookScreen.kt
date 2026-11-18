package com.onedayonesecond.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.onedayonesecond.ui.theme.AssistChip
import com.onedayonesecond.ui.theme.BusyLine
import com.onedayonesecond.ui.theme.SearchBar
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.ui.theme.TextAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.books.BookResult
import com.onedayonesecond.books.Isbn
import com.onedayonesecond.books.SearchOutcome
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookDetails
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MessageAction
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.appTextFieldColors
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Where a search is. [Done.query] is what was searched for, which may no longer be what is typed. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Done(val query: String, val outcome: SearchOutcome) : SearchState
}

/**
 * "X added to Reading now." with Undo and a way straight into the first thought. Sent
 * to the app's message bar, so it is still there on the shelf the reader lands on.
 */
@UnstableApi
private fun announceAdded(container: AppContainer, book: Book) {
    container.messages.show(
        "${book.title} added to Reading now.",
        MessageAction("Undo", run = { container.books.book(book.id)?.let { container.books.remove(it) } }),
        MessageAction("Log first thought", route = Routes.thought(book.id)),
    )
}

/**
 * Finds a book by title, author or ISBN, or by the barcode on its back cover, and
 * puts the chosen edition on the shelf. When search cannot help (no results, no
 * connection, an edition that is not listed), the book can be added by hand.
 */
@UnstableApi
@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onOpenBook: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var search by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var byHand by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    fun runSearch(text: String = query) {
        val wanted = text.trim()
        if (wanted.isEmpty()) return
        keyboard?.hide()
        search = SearchState.Searching
        scope.launch { search = SearchState.Done(wanted, container.bookLookup.search(wanted)) }
    }

    fun add(result: BookResult) {
        if (adding) return
        adding = true
        // On the app's scope, so the book is added even if this screen is left meanwhile.
        container.appScope.launch {
            val onShelf = container.books.bookByIsbn(result.isbn)
            if (onShelf != null) {
                adding = false
                container.messages.show("${onShelf.title} is already on your shelf.")
                if (scope.isActive) onOpenBook(onShelf.id)
                return@launch
            }
            announceAdded(container, container.bookActions.add(result))
            // Only if this screen is still the one showing: otherwise "back" would leave some other screen.
            if (scope.isActive) onAdded()
        }
    }

    if (scanning) {
        BarcodeScanner(
            onIsbn = { isbn ->
                scanning = false
                query = isbn
                runSearch(isbn)
            },
            onClose = { scanning = false },
        )
        return
    }

    if (byHand) {
        BackHandler { byHand = false }
        AddByHand(
            // Something typed or scanned that is an ISBN is carried over, so it is not typed twice.
            isbn = Isbn.normalize(query).orEmpty(),
            title = if (Isbn.normalize(query) == null) query.trim() else "",
            onBack = { byHand = false },
            onAdded = onAdded,
        )
        return
    }

    val searchFocus = remember { FocusRequester() }
    // Nothing has been searched for yet, so the keyboard comes up ready for the title.
    LaunchedEffect(Unit) { if (query.isEmpty()) searchFocus.requestFocus() }

    Column(Modifier.fillMaxSize().imePadding()) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Title, author or ISBN",
            onBack = onBack,
            backLabel = "Back to your books",
            onSearch = { runSearch() },
            focusRequester = searchFocus,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        ) {
            IconAction(LineIcons.Barcode, "Scan the barcode on the back cover", { scanning = true })
        }

        val card = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)
        LazyColumn(Modifier.weight(1f)) {
            when (val state = search) {
                SearchState.Idle -> item(key = "idle") {
                    MetaText(
                        "Search by title, author or ISBN, or scan the barcode on the back cover. " +
                            "Then tap your edition to add it.",
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                    )
                }

                SearchState.Searching -> item(key = "searching") {
                    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)) {
                        MetaText("Searching Open Library…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        BusyLine(Modifier.padding(top = 12.dp))
                    }
                }

                is SearchState.Done -> when (val outcome = state.outcome) {
                    is SearchOutcome.Found -> {
                        item(key = "count") {
                            val count = outcome.results.size
                            MetaText(
                                "$count result${if (count == 1) "" else "s"} · from Open Library",
                                Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        // Keyed by position as well: Open Library can list one book twice, and two rows may not share a key.
                        itemsIndexed(outcome.results, key = { index, result -> "$index-${result.volumeId}" }) { _, result ->
                            ResultRow(result, enabled = !adding, onAdd = { add(result) })
                        }
                        item(key = "footer") {
                            Column(Modifier.padding(top = 8.dp)) {
                                Hairline(Modifier.padding(horizontal = ScreenPadding))
                                BodyText("Can't find your edition?", Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp))
                                SmallText(
                                    "Page counts differ between editions, and your progress depends on the right one.",
                                    Modifier.padding(horizontal = ScreenPadding).padding(top = 2.dp),
                                )
                                TextAction(
                                    "Add it yourself with a cover photo",
                                    { byHand = true },
                                    Modifier.padding(start = 12.dp, top = 4.dp),
                                    icon = LineIcons.Camera,
                                )
                            }
                        }
                    }

                    SearchOutcome.NoResults -> item(key = "none") {
                        StateCard("No books match “${state.query}”", card) {
                            BodyText("Check the spelling, search by the author, or scan the barcode on the back.")
                            TextAction(
                                "Add it yourself with a photo of the cover",
                                { byHand = true },
                                Modifier.offset(x = (-12).dp),
                            )
                        }
                    }

                    SearchOutcome.Offline -> item(key = "offline") {
                        StateCard("No internet right now", card) {
                            BodyText(
                                "Book search needs a connection. You can add the book yourself now and the cover " +
                                    "is filled in once you're back online.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoseButton("Try again", { runSearch(state.query) })
                                TextAction("Add it yourself", { byHand = true }, color = Palette.Ink)
                            }
                        }
                    }

                    SearchOutcome.Unavailable -> item(key = "unavailable") {
                        StateCard("Book search isn't answering right now", card) {
                            BodyText(
                                "It relies on a free service that is sometimes busy, so it may work again later. " +
                                    "You can add the book yourself meanwhile.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoseButton("Try again", { runSearch(state.query) })
                                TextAction("Add it yourself", { byHand = true }, color = Palette.Ink)
                            }
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One edition: its cover, title, author, and the publisher, year and page count that set it apart. */
@Composable
private fun ResultRow(result: BookResult, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = "Add this book", role = Role.Button, onClick = onAdd)
            .defaultMinSize(minHeight = 96.dp)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCover(result.title, result.coverUrl, result.isbn, Modifier.width(48.dp).aspectRatio(COVER_ASPECT))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(result.title, style = AppType.Heading, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            result.author?.let { MetaText(it, maxLines = 1) }
            result.editionLine?.let { SmallText(it) }
        }
        Icon(
            LineIcons.Plus,
            contentDescription = null,
            tint = if (enabled) Palette.Rose else Palette.Muted,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Adding a book that search does not have: its title and, if known, its author, page
 * count and ISBN, with a photo of the cover of the reader's own copy.
 */
@UnstableApi
@Composable
private fun AddByHand(isbn: String, title: String, onBack: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val scope = rememberCoroutineScope()

    var bookTitle by rememberSaveable { mutableStateOf(title) }
    var author by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    var bookIsbn by rememberSaveable { mutableStateOf(isbn) }
    var coverPath by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val photos = rememberPhotoPicker { uri ->
        scope.launch {
            val file = container.bookActions.readPhoto(uri)
            if (file == null) {
                container.messages.show("That photo could not be read. Try another one.")
            } else {
                coverPath?.let { File(it).delete() }
                coverPath = file.absolutePath
            }
        }
    }

    fun save() {
        if (saving) return
        saving = true
        // On the app's scope, so the book is added even if this screen is left mid-save.
        container.appScope.launch {
            val book = container.bookActions.addByHand(
                BookDetails(
                    title = bookTitle,
                    author = author,
                    isbn = bookIsbn,
                    totalPages = pages.toIntOrNull(),
                ),
                cover = coverPath?.let(::File)?.takeIf { it.exists() },
            )
            announceAdded(container, book)
            if (scope.isActive) onAdded()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(onBack, backLabel = "Back to search", title = "Add it yourself")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetaText("Only the title is needed. The page count is what lets your progress be shown.")

            // The cover of the reader's own copy, or the title on a blush cover until there is one.
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(
                    bookTitle.ifBlank { "Your book" },
                    coverPath?.let(::File),
                    Modifier.width(64.dp).aspectRatio(COVER_ASPECT),
                    maxSidePx = 256,
                )
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip("Take a photo of the cover", LineIcons.Camera, photos.take)
                    AssistChip("Choose from your photos", LineIcons.Image, photos.choose)
                }
            }

            Field("Title", bookTitle, { bookTitle = it }, capitalization = KeyboardCapitalization.Words)
            Field("Author", author, { author = it }, capitalization = KeyboardCapitalization.Words)
            Field("Pages in your edition", pages, { text -> pages = text.filter(Char::isDigit).take(4) }, KeyboardType.Number)
            Field(
                "ISBN",
                bookIsbn,
                { text -> bookIsbn = text.filter { it.isDigit() || it == '-' || it == 'X' || it == 'x' }.take(17) },
                KeyboardType.Ascii,
                supporting = "On the back cover, by the barcode. With it, the cover can be looked up.",
            )
            Spacer(Modifier.height(8.dp))
        }
        RoseButton(
            if (saving) "Adding…" else "Add to Reading now",
            ::save,
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            enabled = bookTitle.isNotBlank() && !saving,
        )
    }
}

/** A labelled, one-line text field in the app's style. */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = AppType.Body,
        shape = Corners.Field,
        colors = appTextFieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = ImeAction.Next,
        ),
        supportingText = supporting?.let { { Text(it, style = AppType.Small) } },
        modifier = modifier.fillMaxWidth(),
    )
}

package com.onedayonesecond.ui.books

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import com.onedayonesecond.ui.theme.HeadlineText
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.ui.theme.TintCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.books.BookSummaryContent
import com.onedayonesecond.books.BookSummaryGenerator.Outcome
import com.onedayonesecond.books.ReviewPoint
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.data.BookSummary
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.work.BookWork
import kotlinx.coroutines.launch

/**
 * "Summary and review ideas": how the reader's own thoughts on a book read together,
 * and points they could build a review from. It is written from their notes only; it
 * never quotes the book, and it stops at points, so the review itself stays theirs.
 *
 * The summary is kept until the book has a different number of thoughts with words,
 * and is then written again the next time this page is opened.
 */
@UnstableApi
@Composable
fun BookSummaryScreen(
    bookId: Long,
    onBack: () -> Unit,
    onAddThought: () -> Unit,
    onStartReview: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val clipboard = LocalClipboardManager.current
    val generator = container.bookSummaries

    val book by remember(bookId) { container.books.observeBook(bookId) }.collectAsStateWithLifecycle(initialValue = null)
    val thoughts by remember(bookId) { container.books.observeThoughts(bookId) }
        .collectAsStateWithLifecycle(initialValue = null)
    // Wrapped, so "not read yet" can be told from "there is none".
    val stored by remember(bookId) { container.books.observeSummary(bookId) }
        .collectAsStateWithLifecycle(initialValue = NOT_READ)
    val writing by generator.writing.collectAsStateWithLifecycle()
    val problems by generator.problems.collectAsStateWithLifecycle()

    val current = book
    val all = thoughts
    val summary = stored.takeIf { it !== NOT_READ }
    val loaded = current != null && all != null && stored !== NOT_READ
    val content = remember(summary?.json) { summary?.json?.let(BookSummaryContent::decode) }
    val isWriting = bookId in writing
    val problem = problems[bookId]
    val withWords = all?.let { BookRules.withWords(it).size } ?: 0
    val enough = all != null && BookRules.canSummarise(all)
    val fresh = all != null && content != null && BookRules.isFresh(summary, all)

    fun write() {
        val of = current ?: return
        // On the app's scope: the writing goes on if this page is left, and is there on return.
        container.appScope.launch {
            if (generator.generate(of) == Outcome.Offline) BookWork.writeSummaryWhenOnline(context, of.id)
        }
    }

    // Opening the page writes the summary if there is none, or if thoughts were added or removed since.
    LaunchedEffect(loaded, enough, fresh, withWords) {
        if (loaded && enough && !fresh && generator.isConfigured && bookId !in generator.writing.value) write()
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back to the book", title = "Summary and review ideas")
        if (!loaded || current == null) return@Column

        when {
            content != null -> SummaryPage(
                book = current,
                summary = summary,
                content = content,
                note = when {
                    fresh -> null
                    isWriting -> "Being written again with your newest thoughts…" to null
                    !enough -> "Written before some thoughts were removed. A new one needs " +
                        "${BookRules.MIN_THOUGHTS_FOR_SUMMARY} thoughts with words." to null
                    problem == Outcome.Offline ->
                        "Your thoughts have changed since this was written. It will be written again when " +
                            "you're back online." to "Try now"
                    problem != null -> "Your thoughts have changed since this was written, and writing it again " +
                        "didn't work." to "Try again"
                    else -> null
                },
                onRetry = ::write,
                onTick = { index, ticked ->
                    val kept = summary ?: return@SummaryPage
                    container.appScope.launch {
                        container.books.saveSummary(kept.copy(json = content.withTicked(index, ticked).encode()))
                    }
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(content.pointsAsText()))
                    // From Android 13 the phone shows its own "copied" notice; two would be noise.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) container.messages.show("Points copied.")
                },
                onStartReview = {
                    container.writeSeed = content.reviewDraft(current.title)
                    onStartReview()
                },
            )

            !enough -> Waiting(current) {
                val needed = BookRules.MIN_THOUGHTS_FOR_SUMMARY - withWords
                val min = BookRules.MIN_THOUGHTS_FOR_SUMMARY
                StateCard(if (needed == 1) "Your summary needs one more thought" else "Your summary needs $needed more thoughts") {
                    BodyText(
                        "You've logged ${BookLabels.thoughts(withWords)} with words on ${current.title}. " +
                            "Log $min or more and your summary and review ideas appear here.",
                    )
                    // One pip per thought needed; the filled ones are logged.
                    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(min) { index ->
                            val pip = RoundedCornerShape(3.dp)
                            Box(
                                Modifier
                                    .size(width = 28.dp, height = 6.dp)
                                    .clip(pip)
                                    .then(
                                        if (index < withWords) {
                                            Modifier.background(Palette.Rose)
                                        } else {
                                            Modifier.border(1.dp, Palette.FrameLine, pip)
                                        },
                                    ),
                            )
                        }
                    }
                    SmallText("A photo of a page on its own doesn't count; add a line to it and it does.")
                    RoseButton("Log a thought", onAddThought, icon = LineIcons.Write)
                }
            }

            isWriting || (problem == null && generator.isConfigured) -> Waiting(current) {
                StateCard("Reading your ${BookLabels.thoughts(withWords)}…") {
                    PlaceholderLines()
                    MetaText("Takes a few seconds. If you're offline, it's written as soon as you reconnect.")
                }
            }

            else -> Waiting(current) {
                val (title, text, action) = when (problem) {
                    Outcome.Offline -> Triple(
                        "No internet right now",
                        "Your summary is written as soon as you're back online. Your thoughts are saved on your phone.",
                        "Try now",
                    )
                    is Outcome.Failed -> Triple(
                        "Your summary wasn't written",
                        "${problem.message} Your thoughts are safe and nothing was lost.".trim(),
                        "Try again",
                    )
                    else -> Triple(
                        "Summaries can't be written yet",
                        "The AI service isn't set up in this build of the app. Your thoughts are safe.",
                        null,
                    )
                }
                StateCard(title) {
                    BodyText(text)
                    if (action != null) RoseButton(action, ::write)
                }
            }
        }
    }
}

/** Stands for "the summary has not been read from the phone yet". Never shown or saved. */
private val NOT_READ: BookSummary? = BookSummary(bookId = Long.MIN_VALUE, json = "", thoughtCount = -1, createdAt = 0)

/** The page before there is a summary: which book this is, and a card saying where things stand. */
@Composable
private fun Waiting(book: Book, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookLine(book, book.author.orEmpty())
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** A small cover and a line beside it: which book the page is about. */
@Composable
private fun BookLine(book: Book, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BookCover(book, Modifier.width(36.dp).aspectRatio(COVER_ASPECT), maxSidePx = 192)
        SmallText(
            if (detail.isEmpty()) book.title else "${book.title} · $detail",
            Modifier.padding(start = 12.dp),
        )
    }
}

/** Tinted lines where the summary will be, so the page does not jump when it arrives. */
@Composable
private fun PlaceholderLines() {
    Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(0.92f, 1f, 0.7f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Blush.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun SummaryPage(
    book: Book,
    summary: BookSummary?,
    content: BookSummaryContent,
    /** Said when the summary shown is older than the thoughts, with what can be done about it. */
    note: Pair<String, String?>?,
    onRetry: () -> Unit,
    onTick: (Int, Boolean) -> Unit,
    onCopy: () -> Unit,
    onStartReview: () -> Unit,
) {
    val ticked = content.tickedPoints.size
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp),
        ) {
            val count = summary?.thoughtCount ?: 0
            BookLine(
                book,
                "written ${BookLabels.writtenOn(summary?.createdAt ?: 0)} from your ${BookLabels.thoughts(count)}",
            )
            if (note != null) {
                InlineAlert(
                    note.first,
                    Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    icon = LineIcons.Sparkle,
                    actionLabel = note.second,
                    onAction = note.second?.let { onRetry },
                )
            }
            HeadlineText(content.title, Modifier.padding(top = 16.dp))

            if (content.quote.isNotEmpty()) {
                TintCard(Modifier.padding(top = 20.dp)) {
                    Text("“${content.quote}”", style = AppType.Quote, color = Palette.Ink)
                    content.quotePage?.let { page ->
                        Text(
                            "YOU · PAGE $page",
                            style = AppType.FrameNumber,
                            color = Palette.Rose,
                            modifier = Modifier.padding(top = 10.dp).semantics { contentDescription = "You, on page $page" },
                        )
                    }
                }
            }

            HeadingText("Your reading, in short", Modifier.padding(top = 24.dp))
            BodyText(content.summary, Modifier.padding(top = 8.dp))

            HeadingText("Points for your review", Modifier.padding(top = 24.dp))
            MetaText("Pick the ones you want to write about.", Modifier.padding(top = 2.dp, bottom = 8.dp))
            content.points.forEachIndexed { index, point ->
                Hairline()
                PointRow(point, onTick = { onTick(index, it) })
            }

            if (content.gap.isNotEmpty()) {
                Hairline()
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Something your notes don't cover yet", style = AppType.TitleSmall, color = Palette.Ink)
                    MetaText(content.gap, color = Palette.Ink)
                }
            }

            SmallText(
                "Written only from your own notes. The points are suggestions; the review is yours.",
                Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }

        Hairline()
        Row(
            Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuietButton("Copy", onCopy, icon = LineIcons.Copy)
            RoseButton(
                when (ticked) {
                    0 -> "Start my review"
                    1 -> "Start with 1 point"
                    else -> "Start with $ticked points"
                },
                onStartReview,
                Modifier.weight(1f),
                icon = LineIcons.Write,
            )
        }
    }
}

/** A point for the review: a tick box, its title, one line, and where in the notes it comes from. */
@Composable
private fun PointRow(point: ReviewPoint, onTick: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = point.ticked, role = Role.Checkbox, onValueChange = onTick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckMark(point.ticked, Modifier.padding(top = 3.dp))
        Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(point.title, style = AppType.Heading, color = Palette.Ink)
            if (point.line.isNotEmpty()) MetaText(point.line, color = Palette.Ink)
            SmallText(point.source)
        }
    }
}

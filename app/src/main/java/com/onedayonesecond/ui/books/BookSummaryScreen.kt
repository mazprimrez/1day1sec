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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.onedayonesecond.ui.theme.TitleText
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
        BackBar(onBack, backLabel = "Back to the book")
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

            !enough -> Waiting(current, "Summary and review ideas") {
                val needed = BookRules.MIN_THOUGHTS_FOR_SUMMARY - withWords
                BodyText(
                    if (needed == 1) {
                        "This needs one more thought."
                    } else {
                        "This needs $needed more thoughts."
                    },
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                MetaText(
                    "It is written from what you noted while reading, once there are " +
                        "${BookRules.MIN_THOUGHTS_FOR_SUMMARY} thoughts with words. A photo of a page on its own " +
                        "doesn't count; add a line to it and it does.",
                )
                RoseButton("Add a thought", onAddThought, Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            isWriting || (problem == null && generator.isConfigured) -> Waiting(current, "Summary and review ideas") {
                PlaceholderLines()
                MetaText(
                    "Being written from your ${BookLabels.thoughts(withWords)}. Takes about a minute.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            else -> Waiting(current, "Summary and review ideas") {
                val (text, action) = when (problem) {
                    Outcome.Offline ->
                        "No internet right now. It will be written as soon as you're back online; your thoughts " +
                            "are saved on your phone." to "Try now"
                    is Outcome.Failed -> "This couldn't be written. ${problem.message}".trim() to "Try again"
                    else -> "Summaries aren't set up in this build of the app yet." to null
                }
                if (problem == Outcome.Offline) PlaceholderLines()
                BodyText(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (action != null) QuietButton(action, ::write)
            }
        }
    }
}

/** Stands for "the summary has not been read from the phone yet". Never shown or saved. */
private val NOT_READ: BookSummary? = BookSummary(bookId = Long.MIN_VALUE, json = "", thoughtCount = -1, createdAt = 0)

/** The page before there is a summary: the book's name, a title, and whatever the state has to say. */
@Composable
private fun Waiting(book: Book, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetaText(book.title)
        TitleText(title)
        Hairline(Modifier.padding(vertical = 8.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** Grey lines where the summary will be, so the page does not jump when it arrives. */
@Composable
private fun PlaceholderLines() {
    Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(0.5f, 1f, 0.92f, 0.97f, 0.6f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Hairline))
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
            val count = summary?.thoughtCount ?: 0
            MetaText(
                "${book.title}. Written ${BookLabels.writtenOn(summary?.createdAt ?: 0)} from " +
                    if (count == 1) "your 1 thought" else "all $count of your thoughts",
            )
            if (note != null) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaText(note.first, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    note.second?.let { QuietButton(it, onRetry) }
                }
            }
            TitleText(content.title, Modifier.padding(top = 8.dp))

            if (content.quote.isNotEmpty()) {
                Text(
                    "“${content.quote}”",
                    style = AppType.Quote,
                    color = Palette.Rose,
                    modifier = Modifier.padding(top = 16.dp),
                )
                content.quotePage?.let { MetaText("You, on page $it", Modifier.padding(top = 6.dp)) }
            }

            Hairline(Modifier.padding(top = 20.dp))
            HeadingText("Your reading, in short", Modifier.padding(top = 20.dp))
            BodyText(content.summary, Modifier.padding(top = 8.dp))

            Hairline(Modifier.padding(top = 20.dp))
            HeadingText("Points for your review", Modifier.padding(top = 20.dp))
            MetaText("Tick the ones you'd use. Nothing is written for you.", Modifier.padding(top = 4.dp))
            content.points.forEachIndexed { index, point ->
                PointRow(point, onTick = { onTick(index, it) })
                if (index < content.points.lastIndex) Hairline()
            }

            if (content.gap.isNotEmpty()) {
                val line = Palette.Muted
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = line,
                                cornerRadius = CornerRadius(14.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                                ),
                            )
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaText("Something your notes don't cover yet")
                    BodyText(content.gap)
                }
            }

            MetaText(
                "Written from your own notes. The points are suggestions; the review is yours.",
                Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }

        Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp)) {
            RoseButton(
                when (ticked) {
                    0 -> "Start my review"
                    1 -> "Start my review with 1 point"
                    else -> "Start my review with $ticked points"
                },
                onStartReview,
                Modifier.fillMaxWidth(),
            )
            QuietButton("Copy the points", onCopy, Modifier.fillMaxWidth().padding(top = 8.dp))
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
        CheckMark(point.ticked)
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(point.title, style = AppType.BodyStrong, color = Palette.Ink)
            if (point.line.isNotEmpty()) BodyText(point.line)
            MetaText(point.source)
        }
    }
}

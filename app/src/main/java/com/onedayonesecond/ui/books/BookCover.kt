package com.onedayonesecond.ui.books

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ThoughtType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.books.OpenLibrary
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.ui.common.rememberJournalImage
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Covers are drawn 2:3, the shape of most paperbacks. */
const val COVER_ASPECT = 2f / 3f

/**
 * Where the bookmark is, as a measurement: "p.214" in ink, then "/ 379" in muted when
 * the page count is known. Read aloud it is "Page 214 of 379".
 */
@Composable
fun PageCount(book: Book, modifier: Modifier = Modifier, style: TextStyle = AppType.Timecode) {
    val total = book.totalPages?.takeIf { it > 0 }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = if (total != null) "Page ${book.currentPage} of $total" else "Page ${book.currentPage}"
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("p.${book.currentPage}", style = style, color = Palette.Ink)
        if (total != null) {
            Text(" / $total", style = AppType.Timecode, color = Palette.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
        }
    }
}

/**
 * A book's cover, or its title printed on a blush cover when it has none. The cover
 * is decoration beside the title, so screen readers skip it; where it is the only
 * thing naming the book, the caller labels the row.
 */
@Composable
fun BookCover(title: String, file: File?, modifier: Modifier = Modifier, maxSidePx: Int = 512) {
    val image = rememberJournalImage(file, maxSidePx)
    CoverBox(title, image, modifier)
}

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, maxSidePx: Int = 512) =
    BookCover(book.title, book.coverFile, modifier, maxSidePx)

/**
 * A cover in the search results, fetched from Open Library: the one the result names,
 * or one looked up by ISBN. Until one arrives, and if none does, the title is printed.
 */
@Composable
fun RemoteCover(title: String, url: String?, isbn: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lookup = remember { (context.applicationContext as OneDayApp).container.bookLookup }
    val image by produceState<ImageBitmap?>(initialValue = null, url, isbn) {
        value = withContext(Dispatchers.IO) {
            val bytes = url?.let { lookup.image(it) } ?: isbn?.let { lookup.image(OpenLibrary.coverUrl(it)) }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        }
    }
    CoverBox(title, image, modifier)
}

@Composable
private fun CoverBox(title: String, image: ImageBitmap?, modifier: Modifier) {
    // A cover is one of the few things in the app that casts a shadow: it is an object on the paper.
    BoxWithConstraints(
        modifier
            .shadow(1.dp, Corners.Frame, ambientColor = Palette.Ink, spotColor = Palette.Ink)
            .clip(Corners.Frame)
            .background(Palette.Blush)
            .clearAndSetSemantics { },
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val small = maxWidth < 90.dp
            Text(
                title,
                style = if (small) AppType.LabelSmall else AppType.TitleSmall,
                color = Palette.Ink,
                maxLines = if (small) 5 else 7,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(if (small) 6.dp else 10.dp),
            )
        }
    }
}

/**
 * A tick box, drawn in the app's colours. It is only the picture: the row around it
 * is what is toggled and what is read aloud.
 */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(18.dp)
            .clip(shape)
            .background(if (checked) Palette.Rose else Palette.Paper)
            .border(2.dp, if (checked) Palette.Rose else Palette.Muted, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(LineIcons.Check, contentDescription = null, tint = Palette.Paper, modifier = Modifier.size(14.dp))
    }
}

/** How dates read on the book screens. */
object BookLabels {
    private val locale = Locale.ENGLISH
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val clock = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    private val weekdayDayMonthYear = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)

    /** "Thursday, 12 November · voice note": the day a thought was logged, and what kind it is when not written. */
    fun logged(thought: BookThought, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(thought.createdAt).atZone(zone).toLocalDate()
        val day = date.format(if (date.year == LocalDate.now().year) weekdayDayMonth else weekdayDayMonthYear)
        return when (thought.type) {
            ThoughtType.VOICE -> "$day \u00B7 voice note"
            ThoughtType.PAGE_PHOTO -> "$day \u00B7 with a photo"
            ThoughtType.WRITTEN -> day
        }
    }

    /** "2 November", with the year once it is not this year. */
    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        date.format(if (date.year == today.year) dayMonth else dayMonthYear)

    /** "14 November, 21:10": when a thought was logged. */
    fun moment(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return "${day(time.toLocalDate())}, ${time.format(clock)}"
    }

    /** "today" or "on 14 November": when a summary was written. */
    fun writtenOn(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return if (date == LocalDate.now()) "today" else "on ${day(date)}"
    }

    fun thoughts(count: Int): String = if (count == 1) "1 thought" else "$count thoughts"

    /** What the summary row says under its title. */
    fun summaryLine(thoughtsWithWords: Int): String {
        val needed = BookRules.MIN_THOUGHTS_FOR_SUMMARY - thoughtsWithWords
        return when {
            needed == 1 -> "Needs one more thought"
            needed > 1 -> "Needs $needed more thoughts"
            else -> "From your ${thoughts(thoughtsWithWords)} so far"
        }
    }
}

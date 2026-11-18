package com.onedayonesecond.ui.books

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.books.OpenLibrary
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.ui.common.rememberJournalImage
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.TileShape
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

/** The current page on the book page: 44 sp, as the day number is the day page's one big figure. */
val BigPage: TextStyle = AppType.BigDate.copy(fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp)

/** The page number beside each thought. */
val ThoughtPage: TextStyle = AppType.Heading.copy(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp)

/** The page field on "Log a thought". */
val PageField: TextStyle = AppType.BigDate.copy(fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.2).sp)

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
    BoxWithConstraints(
        modifier.clip(TileShape).background(Palette.Blush).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val small = maxWidth < 90.dp
            Text(
                title,
                style = if (small) AppType.Tab else AppType.Heading,
                color = Palette.Ink,
                textAlign = TextAlign.Center,
                maxLines = if (small) 4 else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(if (small) 5.dp else 12.dp),
            )
        }
    }
}

/** A 4 dp bar showing how far the bookmark is through the book. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(Palette.Hairline).clearAndSetSemantics { }) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(shape).background(Palette.Rose))
    }
}

/**
 * A tick box, drawn in the app's colours. It is only the picture: the row around it
 * is what is toggled and what is read aloud.
 */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) Palette.Rose else Palette.Paper)
            .border(1.5.dp, if (checked) Palette.Rose else Palette.Muted, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(LineIcons.Check, contentDescription = null, tint = Palette.Paper, modifier = Modifier.size(18.dp))
    }
}

/** How dates read on the book screens. */
object BookLabels {
    private val locale = Locale.ENGLISH
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val clock = DateTimeFormatter.ofPattern("HH:mm", locale)

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
            else -> "Written from your ${thoughts(thoughtsWithWords)} so far"
        }
    }
}

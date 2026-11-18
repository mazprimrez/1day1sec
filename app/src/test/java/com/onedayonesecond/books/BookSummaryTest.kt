package com.onedayonesecond.books

import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ThoughtType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The summary page shows the reader's own words as theirs, so what the phone sends
 * and what it accepts back are both pinned down here: only thoughts with words go
 * out, and nothing comes back as a quote unless the reader wrote it themselves.
 */
class BookSummaryTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")
    private val book = Book(
        id = 1,
        backupKey = "2026-11-02-laut-bercerita-7k2q",
        title = "Laut Bercerita",
        author = "Leila S. Chudori",
        totalPages = 379,
        currentPage = 215,
        startedOn = LocalDate.of(2026, 11, 2),
    )

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(jakarta).toInstant().toEpochMilli()

    private fun thought(page: Int, text: String?, type: ThoughtType = ThoughtType.WRITTEN, createdAt: Long = at(10, 21, 0)) =
        BookThought(id = page.toLong(), bookId = 1, page = page, createdAt = createdAt, type = type, text = text)

    private val slowStart = thought(12, "A slow start. I kept losing the thread of who was speaking.")
    private val picksUp = thought(58, "It picks up once Biru starts telling it himself.")
    private val heart = thought(
        142,
        "The line “rumah itu tidak pernah benar-benar kosong” stayed with me. " +
            "I think the book is about absence more than loss.",
    )
    private val ending = thought(214, "Aku nggak nyangka the ending would be this quiet.")
    private val pagePhoto = thought(215, null, ThoughtType.PAGE_PHOTO)
    private val thoughts = listOf(slowStart, picksUp, heart, ending, pagePhoto)

    private fun point(title: String, pages: List<Int>, fromNotes: Boolean, line: String = "One line.") = JSONObject()
        .put("title", title)
        .put("line", line)
        .put("pages", JSONArray(pages))
        .put("fromNotes", fromNotes)

    private fun reply(
        quote: String = "I think the book is about absence more than loss.",
        quotePage: Int = 142,
        points: List<JSONObject> = listOf(point("Absence, not loss", listOf(142, 214), true)),
    ) = JSONObject()
        .put("title", "You read it slowly, then all at once")
        .put("quote", quote)
        .put("quotePage", quotePage)
        .put("summary", "You started unsure and ended quiet.")
        .put("points", JSONArray(points))
        .put("gap", "Who would you hand this book to?")

    // What is sent

    @Test
    fun `only thoughts with words are sent, lowest page first`() {
        val request = BookSummaryRequest.build(book, thoughts.reversed(), emptyList(), jakarta, "id-ID")
        val sent = request.getJSONArray("thoughts")
        assertEquals(listOf(12, 58, 142, 214), (0 until sent.length()).map { sent.getJSONObject(it).getInt("page") })
        assertEquals(slowStart.text, sent.getJSONObject(0).getString("text"))
    }

    @Test
    fun `a photo of a page is never sent, so the book's own text stays on the phone`() {
        val request = BookSummaryRequest.build(book, listOf(pagePhoto, slowStart), emptyList(), jakarta, "id-ID")
        assertEquals(1, request.getJSONArray("thoughts").length())
        assertFalse(request.toString().contains("mediaPath"))
    }

    @Test
    fun `each thought carries the day it was logged, as the reader's clock had it`() {
        // Half past midnight in Jakarta is still the day before in UTC.
        val late = thought(300, "Finished it in one sitting.", createdAt = at(15, 0, 30))
        val request = BookSummaryRequest.build(book, listOf(late), emptyList(), jakarta, "id-ID")
        assertEquals("2026-11-15", request.getJSONArray("thoughts").getJSONObject(0).getString("date"))
    }

    @Test
    fun `the book, the language and the other books finished this year go with the thoughts`() {
        val other = book.copy(id = 2, title = "Pulang", author = null, status = BookStatus.FINISHED)
        val request = BookSummaryRequest.build(book, thoughts, listOf(other), jakarta, "id-ID")

        assertEquals("id-ID", request.getString("language"))
        val sentBook = request.getJSONObject("book")
        assertEquals("Laut Bercerita", sentBook.getString("title"))
        assertEquals("Leila S. Chudori", sentBook.getString("author"))
        assertEquals(379, sentBook.getInt("totalPages"))
        assertEquals(215, sentBook.getInt("currentPage"))
        assertEquals("READING", sentBook.getString("status"))

        val others = request.getJSONArray("otherBooks")
        assertEquals(1, others.length())
        assertEquals("Pulang", others.getJSONObject(0).getString("title"))
        assertTrue(others.getJSONObject(0).isNull("author"))
    }

    @Test
    fun `a very long thought is cut before it is sent`() {
        val long = thought(20, "a".repeat(BookSummaryRequest.MAX_THOUGHT_CHARS + 500))
        val request = BookSummaryRequest.build(book, listOf(long), emptyList(), jakarta, "id-ID")
        val sent = request.getJSONArray("thoughts").getJSONObject(0).getString("text")
        assertEquals(BookSummaryRequest.MAX_THOUGHT_CHARS, sent.length)
    }

    // The quote is the reader's own, or there is none

    @Test
    fun `a quote found in a thought is shown as the reader wrote it, with the page it is really from`() {
        // The model changed the capital and named the wrong page.
        val content = BookSummaryRequest.parse(
            reply(quote = "i think the book is about absence more than loss.", quotePage = 58),
            thoughts,
        )!!
        assertEquals("I think the book is about absence more than loss.", content.quote)
        assertEquals(142, content.quotePage)
    }

    @Test
    fun `a line the reader copied out of the book is never shown as their quote`() {
        assertNull(BookSummaryRequest.verifyQuote("rumah itu tidak pernah benar-benar kosong", 142, thoughts))
        // Not even a quote that only touches the copied line.
        assertNull(BookSummaryRequest.verifyQuote("kosong” stayed with me", 142, thoughts))
    }

    @Test
    fun `when the model quotes the book, a sentence of the reader's own stands in`() {
        val content = BookSummaryRequest.parse(reply(quote = "“rumah itu tidak pernah benar-benar kosong”"), thoughts)!!
        assertEquals("I think the book is about absence more than loss.", content.quote)
        assertEquals(142, content.quotePage)
    }

    @Test
    fun `a quote that is in none of the thoughts is not shown`() {
        assertNull(BookSummaryRequest.verifyQuote("A book about memory and silence.", 142, thoughts))
        // A translation of what the reader wrote is not what they wrote either.
        assertNull(BookSummaryRequest.verifyQuote("I did not expect the ending to be this quiet.", 214, thoughts))
    }

    @Test
    fun `a name or a word or two in quotation marks is still the reader's sentence`() {
        val nickname = thought(70, "Everyone calls him “si Biru” and it suits him.")
        assertEquals(
            "calls him “si Biru” and it suits him" to 70,
            BookSummaryRequest.verifyQuote("calls him “si Biru” and it suits him", 70, listOf(nickname)),
        )
    }

    @Test
    fun `a line copied in plain quotation marks is treated the same as one in curly ones`() {
        val copied = thought(88, "He says \"the sea keeps every name\" and I had to stop reading for a bit.")
        assertNull(BookSummaryRequest.verifyQuote("the sea keeps every name", 88, listOf(copied)))
        assertEquals(
            "I had to stop reading for a bit." to 88,
            BookSummaryRequest.verifyQuote("I had to stop reading for a bit.", 88, listOf(copied)),
        )
    }

    @Test
    fun `a quote of a few letters is not a quote`() {
        assertNull(BookSummaryRequest.verifyQuote("slow start", 12, thoughts))
    }

    @Test
    fun `when nothing the reader wrote can be quoted, the page has no quote`() {
        val terse = listOf(thought(1, "Slow."), thought(2, "Better."), thought(3, "“semua yang hilang akan kembali”"))
        assertNull(BookSummaryRequest.fallbackQuote(terse))
        val content = BookSummaryRequest.parse(reply(quote = "semua yang hilang akan kembali", quotePage = 3), terse)!!
        assertEquals("", content.quote)
        assertNull(content.quotePage)
    }

    // The points

    @Test
    fun `a point only names pages the reader has notes on`() {
        val content = BookSummaryRequest.parse(
            reply(points = listOf(point("Absence, not loss", listOf(214, 999, 142, 142), true))),
            thoughts,
        )!!
        val only = content.points.single()
        assertEquals(listOf(142, 214), only.pages)
        assertTrue(only.fromNotes)
        assertTrue(only.ticked)
        assertEquals("From your notes on pages 142 and 214", only.source)
    }

    @Test
    fun `a page with only a photo on it is not a page the reader has notes on`() {
        val content = BookSummaryRequest.parse(reply(points = listOf(point("The letter", listOf(215), true))), thoughts)!!
        val only = content.points.single()
        assertFalse(only.fromNotes)
        assertTrue(only.pages.isEmpty())
    }

    @Test
    fun `a point that is not drawn from the notes starts unticked and names no pages`() {
        val content = BookSummaryRequest.parse(
            reply(
                points = listOf(
                    point("Absence, not loss", listOf(142), true),
                    point("Next to Pulang", listOf(12), false),
                    point("Made-up pages", listOf(998, 999), true),
                ),
            ),
            thoughts,
        )!!
        assertEquals(listOf(true, false, false), content.points.map { it.ticked })
        assertEquals(listOf(true, false, false), content.points.map { it.fromNotes })
        assertTrue(content.points[1].pages.isEmpty())
        assertEquals("A suggestion, not from your notes", content.points[1].source)
        assertEquals("A suggestion, not from your notes", content.points[2].source)
    }

    @Test
    fun `there are never more than five points`() {
        val many = (1..8).map { point("Point $it", listOf(142), true) }
        assertEquals(5, BookSummaryRequest.parse(reply(points = many), thoughts)!!.points.size)
    }

    @Test
    fun `an answer without a title, a summary or any points is not a summary`() {
        assertNull(BookSummaryRequest.parse(reply().put("title", " "), thoughts))
        assertNull(BookSummaryRequest.parse(reply().put("summary", ""), thoughts))
        assertNull(BookSummaryRequest.parse(reply(points = emptyList()), thoughts))
        assertNull(BookSummaryRequest.parse(reply(points = listOf(point("", listOf(142), true))), thoughts))
    }

    @Test
    fun `where a point comes from is said in plain words`() {
        fun source(vararg pages: Int) = ReviewPoint("T", "L", pages.toList(), true, true).source
        assertEquals("From your notes on page 142", source(142))
        assertEquals("From your notes on pages 142 and 214", source(142, 214))
        assertEquals("From your notes on pages 12, 142 and 214", source(12, 142, 214))
    }

    // The kept summary, and what leaves the page

    private val kept = BookSummaryContent(
        title = "You read it slowly, then all at once",
        quote = "I think the book is about absence more than loss.",
        quotePage = 142,
        summary = "You started unsure and ended quiet.",
        points = listOf(
            ReviewPoint("Absence, not loss", "What you kept coming back to.", listOf(142, 214), true, true),
            ReviewPoint("Next to Pulang", "Another book on your shelf.", emptyList(), false, false),
            ReviewPoint("The quiet ending", "", listOf(214), true, true),
        ),
        gap = "Who would you hand this book to?",
    )

    @Test
    fun `a summary survives being kept and read back, ticks included`() {
        assertEquals(kept, BookSummaryContent.decode(kept.encode()))
        val changed = kept.withTicked(0, false).withTicked(1, true)
        assertEquals(listOf(false, true, true), BookSummaryContent.decode(changed.encode())!!.points.map { it.ticked })
    }

    @Test
    fun `a summary with no quote is kept as one with no quote`() {
        val quiet = kept.copy(quote = "", quotePage = null)
        assertEquals(quiet, BookSummaryContent.decode(quiet.encode()))
    }

    @Test
    fun `something that is not a summary reads back as nothing`() {
        assertNull(BookSummaryContent.decode("not json"))
        assertNull(BookSummaryContent.decode("{}"))
    }

    @Test
    fun `the review draft starts with the ticked points' titles and nothing written for the reader`() {
        val draft = kept.reviewDraft("Laut Bercerita")
        assertEquals("Review: Laut Bercerita\n\nAbsence, not loss\n\nThe quiet ending\n\n", draft)
        // Neither the lines under the points nor the summary: those are not the reader's sentences.
        assertFalse(draft.contains("What you kept coming back to."))
        assertFalse(draft.contains("You started unsure"))
        assertFalse(draft.contains("Next to Pulang"))
    }

    @Test
    fun `with nothing ticked the draft is just the book's name`() {
        val none = kept.withTicked(0, false).withTicked(2, false)
        assertEquals("Review: Laut Bercerita\n\n", none.reviewDraft("Laut Bercerita"))
    }

    @Test
    fun `copying the points copies the ticked ones, or all of them when none is ticked`() {
        assertEquals(
            "• Absence, not loss: What you kept coming back to.\n• The quiet ending",
            kept.pointsAsText(),
        )
        val none = kept.withTicked(0, false).withTicked(2, false)
        assertEquals(3, none.pointsAsText().lines().size)
    }
}

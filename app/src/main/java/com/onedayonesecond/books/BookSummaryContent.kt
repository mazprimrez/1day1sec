package com.onedayonesecond.books

import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookThought
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** One thing the reader could write about in a review. */
data class ReviewPoint(
    val title: String,
    /** One line on what the reader noticed. A pointer to their notes, not a sentence of the review. */
    val line: String,
    /** The pages of the thoughts it draws on. Empty for a suggestion that is not from the notes. */
    val pages: List<Int>,
    val fromNotes: Boolean,
    /** Whether it goes into the review draft. Suggestions from outside the notes start unticked. */
    val ticked: Boolean,
) {
    /** "From your notes on pages 142 and 214", or what to say for a suggestion. */
    val source: String
        get() = when {
            !fromNotes || pages.isEmpty() -> "A suggestion, not from your notes"
            pages.size == 1 -> "From your notes on page ${pages[0]}"
            else -> "From your notes on pages ${pages.dropLast(1).joinToString(", ")} and ${pages.last()}"
        }
}

/** What the "Summary and review ideas" page shows. Stored as JSON in `book_summaries`. */
data class BookSummaryContent(
    /** One line on how the reader read the book. */
    val title: String,
    /** The reader's strongest line, word for word from their own thoughts. Empty if there is none. */
    val quote: String,
    val quotePage: Int?,
    val summary: String,
    val points: List<ReviewPoint>,
    /** Something a review usually has that the notes do not cover yet. May be empty. */
    val gap: String,
) {
    val tickedPoints: List<ReviewPoint> get() = points.filter { it.ticked }

    fun withTicked(index: Int, ticked: Boolean): BookSummaryContent =
        copy(points = points.mapIndexed { i, point -> if (i == index) point.copy(ticked = ticked) else point })

    /**
     * What "Start my review" opens with: the book's name and the ticked points as
     * starting lines, each with room under it. Only the points' titles: the sentences
     * of the review are the reader's to write.
     */
    fun reviewDraft(bookTitle: String): String = buildString {
        append("Review: ").append(bookTitle).append("\n\n")
        tickedPoints.forEach { append(it.title).append("\n\n") }
    }

    /** What "Copy the points" puts on the clipboard: the ticked points, or all of them if none is ticked. */
    fun pointsAsText(): String = (tickedPoints.ifEmpty { points }).joinToString("\n") { point ->
        "• ${point.title}: ${point.line}".trimEnd(' ', ':')
    }

    fun encode(): String = JSONObject()
        .put("title", title)
        .put("quote", quote)
        .put("quotePage", quotePage ?: JSONObject.NULL)
        .put("summary", summary)
        .put(
            "points",
            JSONArray(
                points.map { point ->
                    JSONObject()
                        .put("title", point.title)
                        .put("line", point.line)
                        .put("pages", JSONArray(point.pages))
                        .put("fromNotes", point.fromNotes)
                        .put("ticked", point.ticked)
                },
            ),
        )
        .put("gap", gap)
        .toString()

    companion object {
        const val MAX_POINTS = 5

        /**
         * Reads a stored summary or the backend's answer. Null for one that cannot be
         * read or has no title, summary or points. A point without a `ticked` value
         * (the backend sends none) is ticked exactly when it is from the notes.
         */
        fun decode(json: String): BookSummaryContent? {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return null
            }
            val title = root.optString("title").trim()
            val summary = root.optString("summary").trim()
            val array = root.optJSONArray("points")
            val points = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val point = array?.optJSONObject(index) ?: return@mapNotNull null
                val pointTitle = point.optString("title").trim().ifEmpty { return@mapNotNull null }
                val pagesArray = point.optJSONArray("pages")
                val pages = (0 until (pagesArray?.length() ?: 0))
                    .map { pagesArray!!.optInt(it, 0) }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                val fromNotes = point.optBoolean("fromNotes", false) && pages.isNotEmpty()
                ReviewPoint(
                    title = pointTitle,
                    line = point.optString("line").trim(),
                    pages = if (fromNotes) pages else emptyList(),
                    fromNotes = fromNotes,
                    ticked = if (point.has("ticked")) point.optBoolean("ticked") else fromNotes,
                )
            }.take(MAX_POINTS)
            if (title.isEmpty() || summary.isEmpty() || points.isEmpty()) return null
            return BookSummaryContent(
                title = title,
                quote = root.optString("quote").trim(),
                quotePage = root.optInt("quotePage", 0).takeIf { it > 0 },
                summary = summary,
                points = points,
                gap = root.optString("gap").trim(),
            )
        }
    }
}

/**
 * What is sent to the backend to have a book's summary written, and how its answer is
 * checked. Pure JSON in and out, so both directions can be unit-tested on a plain JVM.
 * The shape is documented in docs/JOURNAL_BACKEND.md.
 */
object BookSummaryRequest {

    /** A thought is cut to this many characters before it is sent. */
    const val MAX_THOUGHT_CHARS = 2000

    private const val MIN_QUOTE_LENGTH = 12
    private const val MAX_QUOTE_LENGTH = 220

    /** Three or more words in quotation marks are taken to be a line copied out of the book. */
    private const val COPIED_WORDS = 3
    private val QUOTED = Regex("“[^“”]*”|\"[^\"]*\"|«[^«»]*»")

    /**
     * @param thoughts the book's thoughts; only those with words are sent, lowest page
     * first. Photos of pages are never sent, so the book's own text never leaves the phone.
     * @param otherBooks the other books finished this year, for a comparison point.
     */
    fun build(
        book: Book,
        thoughts: List<BookThought>,
        otherBooks: List<Book>,
        zone: ZoneId = ZoneId.systemDefault(),
        language: String = Locale.getDefault().toLanguageTag(),
    ): JSONObject = JSONObject()
        .put("language", language)
        .put(
            "book",
            JSONObject()
                .put("title", book.title)
                .put("author", book.author ?: JSONObject.NULL)
                .put("totalPages", book.totalPages ?: JSONObject.NULL)
                .put("currentPage", book.currentPage)
                .put("status", book.status.name),
        )
        .put(
            "thoughts",
            JSONArray(
                thoughts.filter { it.hasWords }
                    .sortedWith(compareBy({ it.page }, { it.createdAt }, { it.id }))
                    .map { thought ->
                        JSONObject()
                            .put("page", thought.page)
                            .put("date", Instant.ofEpochMilli(thought.createdAt).atZone(zone).toLocalDate().toString())
                            .put("text", thought.text.orEmpty().trim().take(MAX_THOUGHT_CHARS))
                    },
            ),
        )
        .put(
            "otherBooks",
            JSONArray(
                otherBooks.map {
                    JSONObject().put("title", it.title).put("author", it.author ?: JSONObject.NULL)
                },
            ),
        )

    /**
     * The backend's answer, checked once more on the phone. Null when it has no title,
     * summary or points: that is not a summary worth showing.
     *
     * The backend already checks the quote; the phone does not take its word for it. A
     * quote that is not in the reader's thoughts, or that touches a line they copied
     * out of the book, is replaced by a sentence taken from the thoughts directly, or
     * by nothing. The page never shows words the reader did not write as theirs.
     */
    fun parse(reply: JSONObject, thoughts: List<BookThought>): BookSummaryContent? {
        val draft = BookSummaryContent.decode(reply.toString()) ?: return null
        val noted = thoughts.filter { it.hasWords }.mapTo(HashSet()) { it.page }
        val quote = verifyQuote(draft.quote, draft.quotePage, thoughts) ?: fallbackQuote(thoughts)
        return draft.copy(
            quote = quote?.first.orEmpty(),
            quotePage = quote?.second,
            points = draft.points.map { point ->
                val pages = point.pages.filter { it in noted }
                val fromNotes = point.fromNotes && pages.isNotEmpty()
                point.copy(pages = if (fromNotes) pages else emptyList(), fromNotes = fromNotes, ticked = fromNotes)
            },
        )
    }

    /** The quote as the reader spelled it and the page it is from, or null if it is not theirs. */
    fun verifyQuote(quote: String, claimedPage: Int?, thoughts: List<BookThought>): Pair<String, Int>? {
        val wanted = squash(quote.trim().trim('"', '“', '”', '\'', '‘', '’'))
        if (wanted.length < MIN_QUOTE_LENGTH) return null
        val ordered = thoughts.filter { it.hasWords }.sortedBy { if (it.page == claimedPage) 0 else 1 }
        for (thought in ordered) {
            val text = squash(thought.text.orEmpty())
            val copied = copiedSpans(text)
            var at = text.indexOf(wanted, ignoreCase = true)
            while (at >= 0) {
                val end = at + wanted.length
                if (copied.none { at < it.last + 1 && it.first < end }) return text.substring(at, end) to thought.page
                at = text.indexOf(wanted, at + 1, ignoreCase = true)
            }
        }
        return null
    }

    /** A sentence of the reader's own to stand in when the model's quote could not be used. */
    fun fallbackQuote(thoughts: List<BookThought>): Pair<String, Int>? {
        val candidates = thoughts.filter { it.hasWords }
            .map { squash(it.text.orEmpty()) to it.page }
            .sortedByDescending { it.first.length }
        for ((text, page) in candidates) {
            val copied = copiedSpans(text)
            var from = 0
            for (sentence in text.split(Regex("(?<=[.!?…])\\s+"))) {
                val at = text.indexOf(sentence, from).coerceAtLeast(0)
                val end = at + sentence.length
                from = end
                val own = copied.none { at < it.last + 1 && it.first < end }
                if (own && sentence.trim().length in MIN_QUOTE_LENGTH..MAX_QUOTE_LENGTH) return sentence.trim() to page
            }
        }
        return null
    }

    private fun copiedSpans(text: String): List<IntRange> =
        QUOTED.findAll(text)
            .filter { it.value.drop(1).dropLast(1).trim().split(Regex("\\s+")).size >= COPIED_WORDS }
            .map { it.range }
            .toList()

    private fun squash(text: String): String = text.trim().replace(Regex("\\s+"), " ")
}

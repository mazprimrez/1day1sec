package com.onedayonesecond.books

import com.onedayonesecond.data.BookDetails
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

/** One edition found by a search. Editions of one book differ in page count, so each is its own result. */
data class BookResult(
    val volumeId: String,
    val title: String,
    val author: String?,
    val isbn: String?,
    /** Publisher and year: "Gramedia, 2017". */
    val edition: String?,
    val pageCount: Int?,
    /** A small cover for the results list, or null if Open Library has none. */
    val coverUrl: String?,
    /**
     * True when [pageCount] is not this edition's own: Open Library gives one figure
     * for all editions of a book, the middle one. It is said as "about 322 pages", and
     * can be put right under "Edit book".
     */
    val pagesApproximate: Boolean = false,
) {
    /** "Gramedia, 2017 · 379 pages": what tells one edition from another. */
    val editionLine: String?
        get() = listOfNotNull(
            edition,
            pageCount?.let { if (pagesApproximate) "about $it pages" else "$it pages" },
        ).joinToString(" · ").ifEmpty { null }

    fun details() = BookDetails(title, author, isbn, edition, pageCount)
}

/**
 * Open Library: where books are searched for, by title, author or ISBN, and where
 * their covers come from. It is free and needs no key. Building the address and
 * reading the answer are here, free of Android types, so both can be unit-tested on
 * a plain JVM; the request itself is in [BookLookup].
 */
object OpenLibrary {

    private const val SEARCH = "https://openlibrary.org/search.json"
    private const val COVERS = "https://covers.openlibrary.org/b"
    const val MAX_RESULTS = 20

    /**
     * Only what the results list shows is asked for. Open Library lists a book once
     * for all its editions; asking for `editions` adds the one edition that matches
     * the search best, which is where the publisher, year and ISBN come from.
     */
    private const val FIELDS = "key,title,author_name,first_publish_year,number_of_pages_median,cover_i," +
        "edition_count,editions,editions.key,editions.title,editions.isbn,editions.publisher," +
        "editions.publish_year,editions.cover_i"

    /** The address to ask. Something that is an ISBN is looked up as one; anything else is searched as words. */
    fun searchUrl(query: String): String {
        val isbn = Isbn.normalize(query)
        return buildString {
            append(SEARCH)
            if (isbn != null) {
                append("?isbn=").append(isbn)
            } else {
                append("?q=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            }
            append("&limit=").append(MAX_RESULTS)
            append("&fields=").append(FIELDS)
        }
    }

    /** The books in Open Library's answer. Empty for no results or an answer that cannot be read. */
    fun parse(json: String): List<BookResult> {
        val docs = try {
            JSONObject(json).optJSONArray("docs")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        return (0 until docs.length()).mapNotNull { index ->
            val work = docs.optJSONObject(index) ?: return@mapNotNull null
            val edition = work.optJSONObject("editions")?.optJSONArray("docs")?.optJSONObject(0)
            // The edition's title is the one printed on the copy in hand: a translation
            // keeps its translated title. Subtitles are left out; here they are mostly
            // labels like "sebuah novel".
            val title = edition?.text("title") ?: work.text("title") ?: return@mapNotNull null
            val authors = work.strings("author_name")
            val year = edition?.optJSONArray("publish_year")?.optInt(0, 0)?.takeIf { it > 0 }
                ?: work.optInt("first_publish_year", 0).takeIf { it > 0 }
            val coverId = edition?.optLong("cover_i", 0)?.takeIf { it > 0 } ?: work.optLong("cover_i", 0).takeIf { it > 0 }
            val pages = work.optInt("number_of_pages_median", 0).takeIf { it > 0 }
            BookResult(
                volumeId = "openlibrary:" + (edition?.text("key") ?: work.optString("key")),
                title = title,
                author = authors.joinToString(", ").ifEmpty { null },
                isbn = edition?.strings("isbn")?.firstNotNullOfOrNull(Isbn::normalize),
                edition = listOfNotNull(edition?.strings("publisher")?.firstOrNull(), year?.toString())
                    .joinToString(", ").ifEmpty { null },
                pageCount = pages,
                coverUrl = coverId?.let { coverUrlById(it, large = false) },
                // One figure stands for every edition; with a single edition it is that edition's own.
                pagesApproximate = pages != null && work.optInt("edition_count", 1) > 1,
            )
        }
    }

    /** A cover by ISBN. `default=false` makes a missing cover a 404 instead of a blank image. */
    fun coverUrl(isbn: String): String = "$COVERS/isbn/$isbn-L.jpg?default=false"

    /** A cover Open Library has already told us it has, by its number: medium for lists, large to keep. */
    fun coverUrlById(id: Long, large: Boolean): String = "$COVERS/id/$id-${if (large) "L" else "M"}.jpg"

    /** The large version of a medium cover address from [coverUrlById]; any other address is returned as it is. */
    fun largeCover(url: String): String =
        if (url.startsWith("$COVERS/id/") && url.endsWith("-M.jpg")) url.removeSuffix("-M.jpg") + "-L.jpg" else url

    fun isCoverUrl(url: String): Boolean = url.startsWith("https://covers.openlibrary.org/")

    /** Lookups by ISBN are the ones Open Library limits (see [LookupLimiter]); covers by number are not. */
    fun isIsbnLookup(url: String): Boolean = url.startsWith("$COVERS/isbn/")

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).trim().ifEmpty { null }

    private fun JSONObject.strings(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).trim().ifEmpty { null } }
    }
}

/**
 * Puts search results in the order they are shown. Pure, so it can be unit-tested on
 * a plain JVM.
 *
 * Open Library matches loosely: a search for a title also brings books that merely
 * share a word with it, and now and then it lists one edition twice. So the title
 * that was typed comes first, and an edition listed twice, by ISBN, is shown once.
 */
object BookSearchOrder {

    fun arrange(query: String, results: List<BookResult>): List<BookResult> {
        // For an ISBN there are no words to match; Open Library's own order stands.
        val wanted = if (Isbn.normalize(query) != null) "" else plain(query)
        // sortedByDescending is stable: books that match equally well keep Open Library's order.
        val ranked = if (wanted.isEmpty()) results else results.sortedByDescending { score(it, wanted) }

        val shown = LinkedHashMap<String, BookResult>()
        var unnamed = 0
        for (result in ranked) {
            val key = result.isbn ?: "no-isbn-${unnamed++}"
            val first = shown[key]
            shown[key] = if (first == null) result else combine(first, result)
        }
        return shown.values.toList()
    }

    /** 3: the title is what was typed. 2: it starts with it. 1: every word typed is in the title or author. */
    private fun score(result: BookResult, wanted: String): Int {
        val title = plain(result.title)
        val mainTitle = plain(result.title.substringBefore(':'))
        return when {
            title == wanted || mainTitle == wanted -> 3
            title.startsWith("$wanted ") -> 2
            else -> {
                val words = (title + " " + plain(result.author.orEmpty())).split(' ').toSet()
                if (wanted.split(' ').all { it in words }) 1 else 0
            }
        }
    }

    /** [first] with the gaps in it filled from [second], the same edition listed again. */
    private fun combine(first: BookResult, second: BookResult): BookResult {
        // A page count that is this edition's own beats one that stands for all editions.
        val takeSecondPages = second.pageCount != null && (first.pageCount == null || (first.pagesApproximate && !second.pagesApproximate))
        return first.copy(
            author = first.author ?: second.author,
            edition = first.edition ?: second.edition,
            pageCount = if (takeSecondPages) second.pageCount else first.pageCount,
            pagesApproximate = if (takeSecondPages) second.pagesApproximate else first.pagesApproximate,
            coverUrl = first.coverUrl ?: second.coverUrl,
        )
    }

    /** Lowercase letters and digits with single spaces: "Café Müller!" and "cafe muller" are the same. */
    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

/**
 * Open Library allows 100 cover lookups by ISBN in five minutes. (Covers asked for by
 * their number, as in search results, are not limited.) This keeps the app
 * well under that: a lookup over the limit is simply not made, and the book shows its
 * title on a blush cover until one is.
 */
class LookupLimiter(
    private val limit: Int = 80,
    private val windowMs: Long = 5 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val made = ArrayDeque<Long>()

    /** True if a lookup may be made now, and counts it. */
    @Synchronized
    fun tryAcquire(): Boolean {
        val time = now()
        while (made.isNotEmpty() && time - made.first() >= windowMs) made.removeFirst()
        if (made.size >= limit) return false
        made.addLast(time)
        return true
    }
}

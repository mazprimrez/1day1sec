package com.onedayonesecond.books

import com.onedayonesecond.data.BookDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {

    @Test
    fun `an edition found by search carries its details onto the shelf`() {
        assertEquals(
            BookDetails("Gadis kretek", "Ratih Kumala", "9789792281415", "Gramedia Pustaka Utama, 2012", 275),
            OpenLibrary.parse(openLibraryAnswer).first().details(),
        )
    }

    @Test
    fun `an edition with only a page count still says how long it is`() {
        val result = BookResult("v", "Title", null, null, null, 120, null)
        assertEquals("120 pages", result.editionLine)
        assertNull(result.copy(pageCount = null).editionLine)
    }

    @Test
    fun `Open Library is asked for a large cover, and for a 404 when it has none`() {
        val url = OpenLibrary.coverUrl("9780306406157")
        assertEquals("https://covers.openlibrary.org/b/isbn/9780306406157-L.jpg?default=false", url)
        assertTrue(OpenLibrary.isCoverUrl(url))
        assertFalse(OpenLibrary.isCoverUrl("https://example.com/cover.jpg"))
    }

    @Test
    fun `cover lookups stop at the limit and start again once the window has passed`() {
        var time = 0L
        val limiter = LookupLimiter(limit = 2, windowMs = 1_000, now = { time })
        assertTrue(limiter.tryAcquire())
        time = 400
        assertTrue(limiter.tryAcquire())
        time = 900
        assertFalse(limiter.tryAcquire())
        // The first lookup is now a full window old, so one more may be made.
        time = 1_000
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `a lookup that was refused is not counted against the limit`() {
        var time = 0L
        val limiter = LookupLimiter(limit = 1, windowMs = 1_000, now = { time })
        assertTrue(limiter.tryAcquire())
        repeat(5) { assertFalse(limiter.tryAcquire()) }
        time = 1_000
        assertTrue(limiter.tryAcquire())
    }

    // Open Library search

    // As Open Library answers: one entry per book, with the edition that matches best
    // inside it. A book with one edition and no cover, a translation among many
    // editions, a book with no edition listed, and an entry with no title.
    private val openLibraryAnswer = """
        {
          "numFound": 4,
          "docs": [
            {
              "key": "/works/OL16682472W", "title": "Gadis kretek", "author_name": ["Ratih Kumala"],
              "edition_count": 1, "first_publish_year": 2012, "number_of_pages_median": 275,
              "editions": {"numFound": 1, "docs": [{
                "key": "/books/OL25356526M", "title": "Gadis kretek", "subtitle": "sebuah novel",
                "publisher": ["Gramedia Pustaka Utama"], "publish_year": [2012],
                "isbn": ["979228141X", "9789792281415"]
              }]}
            },
            {
              "key": "/works/OL17930368W", "title": "Atomic Habits", "author_name": ["James Clear"],
              "cover_i": 12539702, "edition_count": 42, "first_publish_year": 2016, "number_of_pages_median": 322,
              "editions": {"numFound": 1, "docs": [{
                "key": "/books/OL45208572M", "title": "Atomic Habits: Perubahan Kecil", "cover_i": 14578682,
                "publisher": ["Gramedia Pustaka Utama"], "publish_year": [2019],
                "isbn": ["9786020633176", "6020633179"]
              }]}
            },
            {
              "key": "/works/OL5881662W", "title": "Cantik itu luka", "author_name": ["Eka Kurniawan", "Someone Else"],
              "cover_i": 14846592, "edition_count": 3, "first_publish_year": 2002
            },
            {"key": "/works/OL1W", "author_name": ["Nobody"]}
          ]
        }
    """.trimIndent()

    @Test
    fun `Open Library is searched by words, for one matching edition of each book`() {
        val url = OpenLibrary.searchUrl("  gadis kretek ")
        assertTrue(url.startsWith("https://openlibrary.org/search.json?q=gadis+kretek&"))
        assertTrue("limit=20" in url)
        assertTrue("editions.isbn" in url)
        assertTrue("number_of_pages_median" in url)
    }

    @Test
    fun `Open Library is asked for an ISBN as an ISBN, however it was typed`() {
        assertTrue(OpenLibrary.searchUrl("978-602-06-3317-6").startsWith("https://openlibrary.org/search.json?isbn=9786020633176&"))
        assertTrue(OpenLibrary.searchUrl("0306406152").startsWith("https://openlibrary.org/search.json?isbn=9780306406157&"))
    }

    @Test
    fun `an Open Library book is read with its edition's publisher, year and ISBN`() {
        val gadis = OpenLibrary.parse(openLibraryAnswer)[0]
        assertEquals("Gadis kretek", gadis.title)
        assertEquals("Ratih Kumala", gadis.author)
        assertEquals("9789792281415", gadis.isbn)
        assertEquals("Gramedia Pustaka Utama, 2012", gadis.edition)
        assertNull(gadis.coverUrl)
        assertEquals("openlibrary:/books/OL25356526M", gadis.volumeId)
    }

    @Test
    fun `a book with one edition has that edition's page count, said plainly`() {
        val gadis = OpenLibrary.parse(openLibraryAnswer)[0]
        assertEquals(275, gadis.pageCount)
        assertFalse(gadis.pagesApproximate)
        assertEquals("Gramedia Pustaka Utama, 2012 · 275 pages", gadis.editionLine)
    }

    @Test
    fun `a book with many editions has an approximate page count, and its own edition's title and cover`() {
        val habits = OpenLibrary.parse(openLibraryAnswer)[1]
        assertEquals("Atomic Habits: Perubahan Kecil", habits.title)
        assertEquals("9786020633176", habits.isbn)
        assertTrue(habits.pagesApproximate)
        assertEquals("Gramedia Pustaka Utama, 2019 · about 322 pages", habits.editionLine)
        assertEquals("https://covers.openlibrary.org/b/id/14578682-M.jpg", habits.coverUrl)
    }

    @Test
    fun `a book with no edition listed falls back to what is known of the book itself`() {
        val cantik = OpenLibrary.parse(openLibraryAnswer)[2]
        assertEquals("Cantik itu luka", cantik.title)
        assertEquals("Eka Kurniawan, Someone Else", cantik.author)
        assertNull(cantik.isbn)
        assertNull(cantik.pageCount)
        assertFalse(cantik.pagesApproximate)
        assertEquals("2002", cantik.edition)
        assertEquals("https://covers.openlibrary.org/b/id/14846592-M.jpg", cantik.coverUrl)
        assertEquals("openlibrary:/works/OL5881662W", cantik.volumeId)
    }

    @Test
    fun `an Open Library entry without a title is left out, and an unreadable answer is no results`() {
        assertEquals(3, OpenLibrary.parse(openLibraryAnswer).size)
        assertTrue(OpenLibrary.parse("""{"numFound": 0, "docs": []}""").isEmpty())
        assertTrue(OpenLibrary.parse("<html>busy</html>").isEmpty())
    }

    @Test
    fun `a cover from a search result is kept in its large size, and other addresses are left alone`() {
        val medium = OpenLibrary.coverUrlById(14578682, large = false)
        assertEquals("https://covers.openlibrary.org/b/id/14578682-L.jpg", OpenLibrary.largeCover(medium))
        assertEquals(OpenLibrary.coverUrlById(14578682, large = true), OpenLibrary.largeCover(medium))
        val byIsbn = OpenLibrary.coverUrl("9780306406157")
        assertEquals(byIsbn, OpenLibrary.largeCover(byIsbn))
    }

    @Test
    fun `only cover lookups by ISBN count against Open Library's limit`() {
        assertTrue(OpenLibrary.isIsbnLookup(OpenLibrary.coverUrl("9780306406157")))
        assertFalse(OpenLibrary.isIsbnLookup(OpenLibrary.coverUrlById(14578682, large = false)))
        assertTrue(OpenLibrary.isCoverUrl(OpenLibrary.coverUrlById(14578682, large = false)))
    }

    // The order results are shown in

    private fun result(
        id: String,
        title: String,
        author: String? = null,
        isbn: String? = null,
        edition: String? = null,
        pages: Int? = null,
        cover: String? = null,
        approximate: Boolean = false,
    ) = BookResult(id, title, author, isbn, edition, pages, cover, approximate)

    private fun ids(results: List<BookResult>) = results.map { it.volumeId }

    @Test
    fun `the book that was asked for comes before books that only mention it`() {
        val results = listOf(
            result("a", "Membaca Cinta, Mimpi, dan Realitas dalam Novel Gadis Kretek"),
            result("b", "Kajian Sastra Indonesia"),
            result("c", "Gadis kretek", author = "Ratih Kumala"),
        )
        assertEquals(listOf("c", "a", "b"), ids(BookSearchOrder.arrange("Gadis Kretek", results)))
    }

    @Test
    fun `a title counts as asked for whatever its case, accents or subtitle`() {
        val results = listOf(
            result("a", "Notes on a Café"),
            result("b", "Café Müller: A Memoir"),
            result("c", "Cafe Muller and Other Stories"),
        )
        assertEquals(listOf("b", "c", "a"), ids(BookSearchOrder.arrange("cafe muller", results)))
    }

    @Test
    fun `typing the author with the title still finds the book`() {
        val results = listOf(
            result("a", "Bedah Sastra dengan Teori Fiksi"),
            result("b", "Pulang", author = "Leila S. Chudori"),
        )
        assertEquals(listOf("b", "a"), ids(BookSearchOrder.arrange("Pulang Leila Chudori", results)))
    }

    @Test
    fun `equally good matches keep the order Open Library gave them`() {
        val results = listOf(result("a", "Atomic Habits"), result("b", "Atomic Habits"), result("c", "Atomic Habits"))
        assertEquals(listOf("a", "b", "c"), ids(BookSearchOrder.arrange("atomic habits", results)))
    }

    @Test
    fun `an edition listed twice is shown once, with what either entry knows about it`() {
        val results = listOf(
            result("a", "Filosofi teras", isbn = "9786233463034"),
            result(
                "b", "Filosofi teras", author = "Henry Manampiring", isbn = "9786233463034",
                edition = "Buku Kompas, 2020", pages = 297, cover = "https://covers.openlibrary.org/b/id/1-M.jpg",
            ),
        )
        val shown = BookSearchOrder.arrange("filosofi teras", results).single()
        assertEquals("a", shown.volumeId)
        assertEquals("Henry Manampiring", shown.author)
        assertEquals("Buku Kompas, 2020", shown.edition)
        assertEquals(297, shown.pageCount)
        assertEquals("https://covers.openlibrary.org/b/id/1-M.jpg", shown.coverUrl)
    }

    @Test
    fun `an edition's own page count replaces an approximate one`() {
        val results = listOf(
            result("a", "Gadis Kretek", isbn = "9789792281415", pages = 280, approximate = true),
            result("b", "Gadis Kretek", isbn = "9789792281415", pages = 275),
        )
        val shown = BookSearchOrder.arrange("gadis kretek", results).single()
        assertEquals(275, shown.pageCount)
        assertFalse(shown.pagesApproximate)
    }

    @Test
    fun `an approximate page count is kept when it is all there is`() {
        val results = listOf(
            result("a", "Atomic Habits", isbn = "9786020633176"),
            result("b", "Atomic Habits", isbn = "9786020633176", pages = 322, approximate = true),
        )
        val shown = BookSearchOrder.arrange("atomic habits", results).single()
        assertEquals(322, shown.pageCount)
        assertTrue(shown.pagesApproximate)
    }

    @Test
    fun `books without an ISBN are never taken for one another`() {
        val results = listOf(result("a", "Laut Bercerita"), result("b", "Laut Bercerita"), result("c", "Laut Bercerita"))
        assertEquals(3, BookSearchOrder.arrange("laut bercerita", results).size)
    }

    @Test
    fun `for a scanned ISBN the results stay in Open Library's order`() {
        val results = listOf(result("a", "Sebuah Buku Lain"), result("b", "Laut Bercerita", isbn = "9786024246945"))
        assertEquals(listOf("a", "b"), ids(BookSearchOrder.arrange("978-602-424-694-5", results)))
    }

    @Test
    fun `no results stay no results`() {
        assertTrue(BookSearchOrder.arrange("anything", emptyList()).isEmpty())
    }
}

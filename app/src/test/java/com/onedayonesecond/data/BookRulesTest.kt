package com.onedayonesecond.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BookRulesTest {

    private val started = LocalDate.of(2026, 11, 2)
    private val book = Book(
        id = 1,
        backupKey = "2026-11-02-laut-bercerita-7k2q",
        title = "Laut Bercerita",
        totalPages = 379,
        currentPage = 214,
        startedOn = started,
    )

    private fun thought(page: Int, text: String?, type: ThoughtType = ThoughtType.WRITTEN, id: Long = page.toLong()) =
        BookThought(id = id, bookId = 1, page = page, createdAt = page * 1000L, type = type, text = text)

    private fun summary(thoughtCount: Int) = BookSummary(bookId = 1, json = "{}", thoughtCount = thoughtCount, createdAt = 0)

    @Test
    fun `a book's name in the backup is the day it was started, its title and a short suffix`() {
        assertEquals("2026-11-02-laut-bercerita-7k2q", BookRules.backupKey(started, "Laut Bercerita", "7k2q"))
    }

    @Test
    fun `a title is reduced to plain letters, digits and hyphens`() {
        assertEquals("cafe-muller", BookRules.slug("Café  Müller!"))
        assertEquals("1984", BookRules.slug("1984"))
        assertEquals("the-sea-a-novel", BookRules.slug("The Sea: A Novel"))
    }

    @Test
    fun `a title with no plain letters still gets a name, and a long one is cut cleanly`() {
        assertEquals("book", BookRules.slug("《》"))
        assertEquals("book", BookRules.slug(""))
        val long = BookRules.slug("The Hundred-Year-Old Man Who Climbed Out of the Window and Disappeared")
        assertTrue(long, long.length <= 40)
        assertFalse(long, long.endsWith("-"))
    }

    @Test
    fun `log a thought starts on the page after the bookmark`() {
        assertEquals(215, BookRules.nextPage(book))
        assertEquals(1, BookRules.nextPage(book.copy(currentPage = 0)))
    }

    @Test
    fun `the page never goes past the last page of the edition`() {
        assertEquals(379, BookRules.nextPage(book.copy(currentPage = 379)))
        assertEquals(379, BookRules.clampPage(book, 500))
        assertEquals(1, BookRules.clampPage(book, 0))
    }

    @Test
    fun `without a page count, pages run up to a generous limit`() {
        val unknown = book.copy(totalPages = null)
        assertEquals(215, BookRules.nextPage(unknown))
        assertEquals(BookRules.MAX_PAGE, BookRules.clampPage(unknown, 20_000))
    }

    @Test
    fun `the bookmark only moves forward`() {
        assertTrue(BookRules.movesBookmark(book, 215))
        assertFalse(BookRules.movesBookmark(book, 214))
        assertFalse(BookRules.movesBookmark(book, 12))
    }

    // The book is at page 214, where its furthest thought put it.

    @Test
    fun `a thought corrected to a later page takes the bookmark forward`() {
        assertEquals(250, BookRules.bookmarkAfterPageChange(book, oldPage = 142, newPage = 250, otherPages = listOf(214)))
    }

    @Test
    fun `a thought corrected between earlier pages leaves the bookmark alone`() {
        assertEquals(214, BookRules.bookmarkAfterPageChange(book, oldPage = 142, newPage = 124, otherPages = listOf(214)))
        assertEquals(214, BookRules.bookmarkAfterPageChange(book, oldPage = 142, newPage = 214, otherPages = listOf(214)))
    }

    @Test
    fun `a mistyped page that put the bookmark too far brings it back to the furthest thought`() {
        // Logged as 214, meant 21: the furthest page thought about is now 142.
        assertEquals(142, BookRules.bookmarkAfterPageChange(book, oldPage = 214, newPage = 21, otherPages = listOf(12, 142)))
        // The corrected thought is itself still the furthest.
        assertEquals(204, BookRules.bookmarkAfterPageChange(book, oldPage = 214, newPage = 204, otherPages = listOf(12, 142)))
        // It was the only thought.
        assertEquals(21, BookRules.bookmarkAfterPageChange(book, oldPage = 214, newPage = 21, otherPages = emptyList()))
    }

    @Test
    fun `a bookmark set further on by hand is not pulled back by a corrected thought`() {
        val ahead = book.copy(currentPage = 300)
        assertEquals(300, BookRules.bookmarkAfterPageChange(ahead, oldPage = 214, newPage = 21, otherPages = listOf(142)))
    }

    @Test
    fun `a finished book stays on its last page when a thought there is corrected`() {
        val finished = book.copy(currentPage = 379, status = BookStatus.FINISHED)
        assertEquals(379, BookRules.bookmarkAfterPageChange(finished, oldPage = 379, newPage = 370, otherPages = listOf(142)))
    }

    @Test
    fun `progress is the bookmark over the page count, and unknown without one`() {
        assertEquals(214f / 379f, BookRules.progress(book)!!, 0.0001f)
        assertEquals(1f, BookRules.progress(book.copy(currentPage = 400))!!, 0.0001f)
        assertNull(BookRules.progress(book.copy(totalPages = null)))
    }

    @Test
    fun `the page line says where the bookmark is in each state`() {
        assertEquals("Page 214 of 379", BookRules.pageLine(book))
        assertEquals("Page 214", BookRules.pageLine(book.copy(totalPages = null)))
        assertEquals("Not started. 379 pages", BookRules.pageLine(book.copy(currentPage = 0)))
        assertNull(BookRules.pageLine(book.copy(currentPage = 0, totalPages = null)))
        assertEquals("379 pages", BookRules.pageLine(book.copy(status = BookStatus.FINISHED, currentPage = 379)))
    }

    @Test
    fun `a summary needs three thoughts with words`() {
        val two = listOf(thought(12, "A slow start."), thought(58, "It picks up."))
        assertFalse(BookRules.canSummarise(two))
        assertEquals(1, BookRules.thoughtsStillNeeded(two))

        val three = two + thought(142, "This is the heart of it.")
        assertTrue(BookRules.canSummarise(three))
        assertEquals(0, BookRules.thoughtsStillNeeded(three))
    }

    @Test
    fun `a photo of a page or a voice note not yet written out does not count towards a summary`() {
        val thoughts = listOf(
            thought(12, "A slow start."),
            thought(58, "It picks up."),
            thought(90, null, ThoughtType.PAGE_PHOTO),
            thought(120, null, ThoughtType.VOICE),
            thought(130, "   "),
        )
        assertFalse(BookRules.canSummarise(thoughts))
        assertEquals(2, BookRules.withWords(thoughts).size)
        assertEquals(1, BookRules.thoughtsStillNeeded(thoughts))
    }

    @Test
    fun `a summary is kept until the book has a different number of thoughts with words`() {
        val thoughts = listOf(thought(12, "One."), thought(58, "Two."), thought(142, "Three."))
        assertTrue(BookRules.isFresh(summary(3), thoughts))
        // A new thought makes it stale.
        assertFalse(BookRules.isFresh(summary(3), thoughts + thought(214, "Four.")))
        // So does a deleted one: the summary may still be quoting it.
        assertFalse(BookRules.isFresh(summary(3), thoughts.dropLast(1)))
        assertFalse(BookRules.isFresh(null, thoughts))
    }

    @Test
    fun `a photo added without words does not make the summary stale`() {
        val thoughts = listOf(thought(12, "One."), thought(58, "Two."), thought(142, "Three."))
        assertTrue(BookRules.isFresh(summary(3), thoughts + thought(200, null, ThoughtType.PAGE_PHOTO)))
    }
}

/**
 * The migration from version 3 is written by hand. Room writes down what it expects
 * each version of the database to look like in `app/schemas`, so the hand-written
 * statements are checked against that: a column added to a book table without the
 * migration being updated fails here, not on a phone with a diary in it.
 */
class BookSchemaTest {

    private class Table(val name: String, val statements: List<String>)

    private fun tables(version: Int): List<Table> {
        // Unit tests run from the app module's folder.
        val file = File("schemas/com.onedayonesecond.data.AppDatabase/$version.json")
        val entities = JSONObject(file.readText()).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { index ->
            val entity = entities.getJSONObject(index)
            val name = entity.getString("tableName")
            val indices = entity.optJSONArray("indices")
            val statements = listOf(entity.getString("createSql")) +
                (0 until (indices?.length() ?: 0)).map { indices!!.getJSONObject(it).getString("createSql") }
            Table(name, statements.map { it.replace("\${TABLE_NAME}", name) })
        }
    }

    @Test
    fun `the migration creates exactly the book tables Room expects in version 4`() {
        val bookTables = setOf("books", "book_thoughts", "book_summaries")
        val expected = tables(4).filter { it.name in bookTables }
        assertEquals(bookTables, expected.mapTo(HashSet()) { it.name })
        assertEquals(expected.flatMap { it.statements }.toSet(), BookSchema.MIGRATION_3_4_SQL.toSet())
    }

    @Test
    fun `the tables that were already there are left exactly as they were`() {
        val before = tables(3).associate { it.name to it.statements }
        val after = tables(4).associate { it.name to it.statements }
        assertTrue(before.isNotEmpty())
        for ((name, statements) in before) assertEquals(name, statements, after[name])
        assertEquals(setOf("books", "book_thoughts", "book_summaries"), after.keys - before.keys)
    }
}

package com.onedayonesecond.backup

import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.ThoughtType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookFileTest {

    private val key = "2026-11-02-laut-bercerita-7k2q"
    private val written = ThoughtBackup(142, 1000, ThoughtType.WRITTEN, "This is the heart of it.", null, true)
    private val voice = ThoughtBackup(214, 2000, ThoughtType.VOICE, null, "$key-2000.m4a", false)
    private val photo = ThoughtBackup(215, 3000, ThoughtType.PAGE_PHOTO, "The letter.", "$key-3000.jpg", true)
    private val book = BookBackup(
        key = key,
        title = "Laut Bercerita",
        author = "Leila S. Chudori",
        isbn = "9780306406157",
        edition = "Gramedia, 2017",
        totalPages = 379,
        currentPage = 215,
        status = BookStatus.READING,
        startedOn = LocalDate.of(2026, 11, 2),
        finishedOn = null,
        cover = "$key.jpg",
        thoughts = listOf(written, voice, photo),
    )

    @Test
    fun `a book survives being written to its file and read back`() {
        assertEquals(book, BookFile.decode(BookFile.encode(book)))
    }

    @Test
    fun `a finished book added by hand, with nothing but a title, reads back the same`() {
        val bare = BookBackup(
            key = "2026-01-05-untitled-ab23",
            title = "Untitled",
            author = null,
            isbn = null,
            edition = null,
            totalPages = null,
            currentPage = 0,
            status = BookStatus.FINISHED,
            startedOn = LocalDate.of(2026, 1, 5),
            finishedOn = LocalDate.of(2026, 2, 1),
            cover = null,
            thoughts = emptyList(),
        )
        assertEquals(bare, BookFile.decode(BookFile.encode(bare)))
    }

    @Test
    fun `the same book always produces the same bytes, whatever order the thoughts came in`() {
        assertArrayEquals(
            BookFile.encode(book),
            BookFile.encode(book.copy(thoughts = listOf(photo, written, voice))),
        )
    }

    @Test
    fun `a changed bookmark changes the bytes, so the backup sees it`() {
        assertFalse(BookFile.encode(book).contentEquals(BookFile.encode(book.copy(currentPage = 300))))
    }

    @Test
    fun `a damaged file, or one without a key, title or start date, is not a book`() {
        assertNull(BookFile.decode("not json".toByteArray()))
        assertNull(BookFile.decode("""{"title": "Laut Bercerita", "startedOn": "2026-11-02"}""".toByteArray()))
        assertNull(BookFile.decode("""{"key": "k", "startedOn": "2026-11-02"}""".toByteArray()))
        assertNull(BookFile.decode("""{"key": "k", "title": "Laut Bercerita", "startedOn": "soon"}""".toByteArray()))
    }

    @Test
    fun `a thought that cannot be understood is skipped, and the rest of the book is kept`() {
        val file = """
            {
              "key": "k", "title": "Laut Bercerita", "startedOn": "2026-11-02",
              "thoughts": [
                {"page": 12, "createdAt": 1000, "type": "WRITTEN", "text": "A slow start."},
                {"page": 58, "createdAt": 2000, "type": "SKETCH", "text": "From a later version."},
                {"page": 0, "createdAt": 3000, "type": "WRITTEN", "text": "No page."},
                {"page": 90, "type": "WRITTEN", "text": "No time."},
                "nonsense"
              ]
            }
        """.trimIndent()
        val read = BookFile.decode(file.toByteArray())!!
        assertEquals(listOf(12), read.thoughts.map { it.page })
        // What the file leaves out falls back to a book that is being read, from the first page.
        assertEquals(BookStatus.READING, read.status)
        assertEquals(0, read.currentPage)
        assertTrue(read.thoughts.single().showInDiary)
    }

    @Test
    fun `with nothing in Drive yet, the phone's book is what is written`() {
        assertEquals(book, BookSync.merge(null, book))
    }

    @Test
    fun `thoughts that are only in Drive are kept`() {
        val onPhone = book.copy(thoughts = listOf(photo))
        val merged = BookSync.merge(book, onPhone)
        assertEquals(setOf(written, voice, photo), merged.thoughts.toSet())
    }

    @Test
    fun `a thought on both sides is the phone's version`() {
        val edited = written.copy(text = "This is the heart of it, really.")
        val merged = BookSync.merge(book, book.copy(thoughts = listOf(edited, voice, photo)))
        assertEquals(3, merged.thoughts.size)
        assertTrue(edited in merged.thoughts)
        assertFalse(written in merged.thoughts)
    }

    @Test
    fun `a thought moved to another page on the phone replaces its copy in Drive`() {
        val moved = written.copy(page = 124)
        val merged = BookSync.merge(book, book.copy(thoughts = listOf(moved, voice, photo)))
        assertEquals(3, merged.thoughts.size)
        assertEquals(124, merged.thoughts.single { it.createdAt == written.createdAt }.page)
    }

    @Test
    fun `a voice note written out on the phone replaces its wordless copy in Drive`() {
        val typedOut = voice.copy(text = "Aku nggak nyangka endingnya begitu.")
        val merged = BookSync.merge(book, book.copy(thoughts = listOf(written, typedOut, photo)))
        assertEquals(typedOut, merged.thoughts.single { it.createdAt == voice.createdAt })
    }

    @Test
    fun `the book's details are the phone's`() {
        val onPhone = book.copy(title = "Laut Bercerita (2017)", currentPage = 379, status = BookStatus.FINISHED)
        val merged = BookSync.merge(book, onPhone)
        assertEquals("Laut Bercerita (2017)", merged.title)
        assertEquals(379, merged.currentPage)
        assertEquals(BookStatus.FINISHED, merged.status)
    }

    @Test
    fun `a cover that is only in Drive is kept, and the phone's cover wins when it has one`() {
        assertEquals("$key.jpg", BookSync.merge(book, book.copy(cover = null)).cover)
        assertEquals("$key.jpg", BookSync.merge(book.copy(cover = null), book).cover)
    }

    @Test
    fun `merging what is already in Drive changes nothing, so nothing is uploaded again`() {
        assertArrayEquals(BookFile.encode(book), BookFile.encode(BookSync.merge(book, book)))
    }

    // Names in the books folder

    @Test
    fun `a book's files are named by its key`() {
        assertEquals("books", DriveLayout.BOOKS_FOLDER)
        assertEquals("$key.json", DriveLayout.bookFileName(key))
        assertEquals("$key.jpg", DriveLayout.bookCoverName(key))
        assertEquals("$key-1793500000000.m4a", DriveLayout.thoughtMediaName(key, 1793500000000, "m4a"))
    }

    @Test
    fun `a book file's name reads back to its key, and other files are not book files`() {
        assertEquals(key, DriveLayout.bookKeyOf("$key.json"))
        assertNull(DriveLayout.bookKeyOf("$key.jpg"))
        assertNull(DriveLayout.bookKeyOf("$key-1793500000000.m4a"))
        assertNull(DriveLayout.bookKeyOf(".json"))
    }

    @Test
    fun `removing a book takes its file, its cover and its thoughts' media`() {
        assertTrue(DriveLayout.isBookFile("$key.json", key))
        assertTrue(DriveLayout.isBookFile("$key.jpg", key))
        assertTrue(DriveLayout.isBookFile("$key-1793500000000.m4a", key))
        assertTrue(DriveLayout.isBookFile("$key-1793500000000.jpg", key))
    }

    @Test
    fun `removing a book never touches another book whose name starts the same`() {
        // The same title started on the same day, with another suffix.
        assertFalse(DriveLayout.isBookFile("2026-11-02-laut-bercerita-9x4m.json", key))
        // A key that merely continues this one.
        assertFalse(DriveLayout.isBookFile("$key-again-ab23.json", key))
        assertFalse(DriveLayout.isBookFile("$key-again-ab23-1793500000000.m4a", key))
        assertFalse(DriveLayout.isBookFile("${key}2.json", key))
        assertFalse(DriveLayout.isBookFile("anything.json", ""))
    }

    @Test
    fun `book files are told to Drive as what they are`() {
        assertEquals("application/json", DriveLayout.mimeOf(DriveLayout.bookFileName(key)))
        assertEquals("image/jpeg", DriveLayout.mimeOf(DriveLayout.bookCoverName(key)))
        assertEquals("audio/mp4", DriveLayout.mimeOf(DriveLayout.thoughtMediaName(key, 1, "m4a")))
    }
}

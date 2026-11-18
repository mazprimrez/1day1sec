package com.onedayonesecond.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** What is known about a book when it is added or edited. */
data class BookDetails(
    val title: String,
    val author: String? = null,
    val isbn: String? = null,
    val edition: String? = null,
    val totalPages: Int? = null,
)

/** The voice note or page photo that goes with a thought. [scratch] is moved into the app's files. */
class ThoughtMedia(val type: ThoughtType, val scratch: File, val extension: String)

/** The reading log: the shelf, each book's thoughts, and the summaries written from them. */
class BookRepository(
    private val dao: BookDao,
    val storage: ClipStorage,
    private val removals: BookRemovalLog,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val suffix: () -> String = ::randomSuffix,
) {

    fun observeBooks(): Flow<List<Book>> = dao.observeBooks()

    fun observeBook(id: Long): Flow<Book?> = dao.observeBook(id)

    suspend fun book(id: Long): Book? = dao.book(id)

    suspend fun allBooks(): List<Book> = dao.allBooks()

    suspend fun bookByKey(key: String): Book? = dao.bookByKey(key)

    /** A book already on the shelf with this ISBN, so the same edition is not added twice. */
    suspend fun bookByIsbn(isbn: String?): Book? = isbn?.takeIf { it.isNotBlank() }?.let { dao.bookByIsbn(it) }

    suspend fun booksMissingCover(): List<Book> = dao.booksMissingCover()

    /**
     * Puts a book on the shelf under "Reading now", started today.
     *
     * @param coverScratch its cover if one was found or photographed; moved into the app's files.
     */
    suspend fun add(details: BookDetails, coverScratch: File? = null): Book = withContext(Dispatchers.IO) {
        val tidy = details.tidy()
        val startedOn = today()
        var key = BookRules.backupKey(startedOn, tidy.title, suffix())
        while (dao.bookByKey(key) != null) key = BookRules.backupKey(startedOn, tidy.title, suffix())
        val book = Book(
            backupKey = key,
            title = tidy.title,
            author = tidy.author,
            isbn = tidy.isbn,
            edition = tidy.edition,
            totalPages = tidy.totalPages,
            coverPath = coverScratch?.let { moveCoverIn(key, it) },
            startedOn = startedOn,
        )
        book.copy(id = dao.insertBook(book))
    }

    /** Changes what is known about a book. The bookmark can be corrected here, in either direction. */
    suspend fun update(book: Book, details: BookDetails, currentPage: Int) {
        val tidy = details.tidy()
        val last = tidy.totalPages?.takeIf { it > 0 } ?: BookRules.MAX_PAGE
        dao.setDetails(
            id = book.id,
            title = tidy.title,
            author = tidy.author,
            isbn = tidy.isbn,
            edition = tidy.edition,
            totalPages = tidy.totalPages,
            currentPage = currentPage.coerceIn(0, last),
        )
    }

    /** Gives the book a new cover, or with null goes back to the title on a blush cover. */
    suspend fun setCover(book: Book, scratch: File?) = withContext(Dispatchers.IO) {
        val path = scratch?.let { moveCoverIn(book.backupKey, it) }
        dao.setCover(book.id, path)
        book.coverFile?.takeIf { it.absolutePath != path }?.delete()
    }

    /**
     * A cover that was looked up in the background. Kept only if the book still has
     * none: one the reader chose meanwhile is never replaced.
     */
    suspend fun fillInCover(book: Book, scratch: File) = withContext(Dispatchers.IO) {
        val path = moveCoverIn(book.backupKey, scratch)
        if (dao.setCoverIfMissing(book.id, path) == 0) File(path).delete()
    }

    /** Finished today. The bookmark goes to the last page when the page count is known. */
    suspend fun finish(book: Book) {
        dao.setFinished(book.id, today(), book.totalPages?.takeIf { it > 0 } ?: book.currentPage)
    }

    suspend fun readAgain(book: Book) = dao.setReading(book.id)

    /**
     * Takes a book off the shelf with its thoughts, their voice notes and photos, its
     * cover and its summary. Its copy in Drive goes with the next backup.
     */
    suspend fun remove(book: Book) = withContext(Dispatchers.IO) {
        val thoughts = dao.thoughtsOf(book.id)
        // Noted first: if the app dies half-way, the worst outcome is a Drive clean-up
        // for a book that is still here, which the backup checks for.
        removals.add(book.backupKey)
        dao.deleteThoughtsOf(book.id)
        dao.deleteSummary(book.id)
        dao.deleteBook(book.id)
        thoughts.forEach { it.mediaFile?.delete() }
        book.coverFile?.delete()
    }

    fun observeThoughts(bookId: Long): Flow<List<BookThought>> = dao.observeThoughts(bookId)

    /** A book's thoughts, lowest page first. */
    suspend fun thoughtsOf(bookId: Long): List<BookThought> = dao.thoughtsOf(bookId)

    suspend fun thought(id: Long): BookThought? = dao.thought(id)

    suspend fun unprocessedThoughts(): List<BookThought> = dao.unprocessedThoughts()

    /** The thoughts logged on [date] that asked to be shown in the diary. */
    fun observeDiaryThoughts(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DiaryThought>> =
        dao.observeDiaryThoughts(
            from = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    /**
     * Records a thought and moves the bookmark to its page if that is further on.
     *
     * Words the reader typed stand as they are. A voice note without typed words starts
     * out [ProcessingState.PENDING] and is typed out afterwards.
     */
    suspend fun logThought(
        book: Book,
        page: Int,
        text: String,
        media: ThoughtMedia?,
        showInDiary: Boolean,
    ): BookThought = withContext(Dispatchers.IO) {
        val createdAt = now()
        val words = text.trim().ifEmpty { null }
        val path = media?.let { attachment ->
            val destination = storage.newThoughtFile(book.backupKey, createdAt, attachment.extension)
            moveInto(attachment.scratch, destination)
            destination.absolutePath
        }
        val type = media?.type ?: ThoughtType.WRITTEN
        val thought = BookThought(
            bookId = book.id,
            page = BookRules.clampPage(book, page),
            createdAt = createdAt,
            type = type,
            text = words,
            mediaPath = path,
            showInDiary = showInDiary,
            processing = if (type == ThoughtType.VOICE && words == null) ProcessingState.PENDING else ProcessingState.DONE,
        )
        val saved = thought.copy(id = dao.insertThought(thought))
        dao.moveBookmark(book.id, saved.page)
        saved
    }

    /**
     * Corrects a logged thought: its page, its words (also how a voice note's
     * transcript is put right), or both. The bookmark follows a corrected page (see
     * [BookRules.bookmarkAfterPageChange]). The summary may be quoting the old words or
     * naming the old page, so it is written afresh next time.
     *
     * Words left as they were are not touched, so a voice note still waiting to be
     * typed out goes on waiting when only its page is corrected.
     */
    suspend fun editThought(thought: BookThought, page: Int, text: String) = withContext(Dispatchers.IO) {
        val book = dao.book(thought.bookId) ?: return@withContext
        val newPage = BookRules.clampPage(book, page)
        val words = text.trim()
        val pageChanged = newPage != thought.page
        val wordsChanged = words != thought.text.orEmpty()
        if (!pageChanged && !wordsChanged) return@withContext
        if (pageChanged) {
            val otherPages = dao.thoughtsOf(book.id).filter { it.id != thought.id }.map { it.page }
            dao.setThoughtPage(thought.id, newPage)
            val bookmark = BookRules.bookmarkAfterPageChange(book, thought.page, newPage, otherPages)
            if (bookmark != book.currentPage) dao.setBookmark(book.id, bookmark)
        }
        if (wordsChanged) dao.setThoughtTextByHand(thought.id, words)
        dao.deleteSummary(book.id)
    }

    suspend fun deleteThought(thought: BookThought) = withContext(Dispatchers.IO) {
        dao.deleteThought(thought.id)
        thought.mediaFile?.delete()
    }

    suspend fun setProcessing(id: Long, state: ProcessingState) = dao.setThoughtProcessing(id, state)

    /** "Try again" on a voice note that could not be typed out. */
    suspend fun retryThought(id: Long) = dao.retryThought(id)

    /** False if the reader typed the words while the voice note was being typed out. Theirs are kept. */
    suspend fun finishTypingOut(id: Long, text: String): Boolean = dao.finishTypingOut(id, text.trim()) > 0

    fun observeSummary(bookId: Long): Flow<BookSummary?> = dao.observeSummary(bookId)

    suspend fun summary(bookId: Long): BookSummary? = dao.summary(bookId)

    suspend fun saveSummary(summary: BookSummary) = dao.upsertSummary(summary)

    /** Used by restore: the book is recorded as it was, cover already in place. */
    suspend fun putBook(book: Book): Book = book.copy(id = dao.insertBook(book.copy(id = 0)))

    /** Used by restore: the thought is recorded as it was, media already in place. */
    suspend fun putThought(thought: BookThought): BookThought =
        thought.copy(id = dao.insertThought(thought.copy(id = 0)))

    private fun moveCoverIn(key: String, scratch: File): String {
        val destination = storage.newCoverFile(key, now())
        moveInto(scratch, destination)
        return destination.absolutePath
    }

    private fun moveInto(scratch: File, destination: File) {
        if (!scratch.renameTo(destination)) {
            scratch.copyTo(destination, overwrite = true)
            scratch.delete()
        }
    }

    private fun BookDetails.tidy(): BookDetails = BookDetails(
        title = title.oneLine(MAX_TITLE_LENGTH).orEmpty().ifEmpty { "Untitled" },
        author = author?.oneLine(MAX_AUTHOR_LENGTH),
        isbn = isbn?.filter { it.isDigit() || it == 'X' || it == 'x' }?.uppercase()?.ifEmpty { null },
        edition = edition?.oneLine(MAX_EDITION_LENGTH),
        totalPages = totalPages?.takeIf { it in 1..BookRules.MAX_PAGE },
    )

    private fun String.oneLine(max: Int): String? = trim().replace(Regex("\\s+"), " ").take(max).ifEmpty { null }

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_AUTHOR_LENGTH = 120
        const val MAX_EDITION_LENGTH = 80

        private const val SUFFIX_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789"

        /** Four letters and digits, without the ones that look alike. */
        fun randomSuffix(): String = (1..4).map { SUFFIX_LETTERS.random() }.joinToString("")
    }
}

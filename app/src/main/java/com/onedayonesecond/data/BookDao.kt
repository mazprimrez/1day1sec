package com.onedayonesecond.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BookDao {

    // Books

    @Query("SELECT * FROM books ORDER BY id DESC")
    fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun allBooks(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: Long): Book?

    @Query("SELECT * FROM books WHERE backupKey = :key")
    suspend fun bookByKey(key: String): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn ORDER BY id DESC LIMIT 1")
    suspend fun bookByIsbn(isbn: String): Book?

    /** Books whose cover could still be looked up by ISBN: added by hand or while offline. */
    @Query("SELECT * FROM books WHERE coverPath IS NULL AND isbn IS NOT NULL")
    suspend fun booksMissingCover(): List<Book>

    @Insert
    suspend fun insertBook(book: Book): Long

    // Books are changed a few columns at a time, never by writing a whole row back, for
    // the same reason as entries: a cover arriving in the background must not undo a
    // bookmark moved meanwhile.

    @Query(
        "UPDATE books SET title = :title, author = :author, isbn = :isbn, edition = :edition, " +
            "totalPages = :totalPages, currentPage = :currentPage WHERE id = :id",
    )
    suspend fun setDetails(
        id: Long,
        title: String,
        author: String?,
        isbn: String?,
        edition: String?,
        totalPages: Int?,
        currentPage: Int,
    )

    @Query("UPDATE books SET coverPath = :path WHERE id = :id")
    suspend fun setCover(id: Long, path: String?)

    /** A cover found in the background. Never replaces one the reader has chosen meanwhile. */
    @Query("UPDATE books SET coverPath = :path WHERE id = :id AND coverPath IS NULL")
    suspend fun setCoverIfMissing(id: Long, path: String): Int

    /** The bookmark only moves forward: a thought about an earlier page leaves it where it is. */
    @Query("UPDATE books SET currentPage = :page WHERE id = :id AND currentPage < :page")
    suspend fun moveBookmark(id: Long, page: Int): Int

    @Query("UPDATE books SET status = 'FINISHED', finishedOn = :finishedOn, currentPage = :page WHERE id = :id")
    suspend fun setFinished(id: Long, finishedOn: LocalDate, page: Int)

    @Query("UPDATE books SET status = 'READING', finishedOn = NULL WHERE id = :id")
    suspend fun setReading(id: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    // Thoughts

    @Query("SELECT * FROM book_thoughts WHERE bookId = :bookId ORDER BY page DESC, createdAt DESC, id DESC")
    fun observeThoughts(bookId: Long): Flow<List<BookThought>>

    @Query("SELECT * FROM book_thoughts WHERE bookId = :bookId ORDER BY page ASC, createdAt ASC, id ASC")
    suspend fun thoughtsOf(bookId: Long): List<BookThought>

    @Query("SELECT * FROM book_thoughts WHERE id = :id")
    suspend fun thought(id: Long): BookThought?

    @Query("SELECT * FROM book_thoughts WHERE processing IN ('PENDING', 'WAITING_FOR_NETWORK')")
    suspend fun unprocessedThoughts(): List<BookThought>

    /** Thoughts logged in a stretch of time that asked to be shown in the diary, with their book's title. */
    @Query(
        "SELECT t.*, b.title AS bookTitle FROM book_thoughts t JOIN books b ON b.id = t.bookId " +
            "WHERE t.showInDiary = 1 AND t.createdAt >= :from AND t.createdAt < :until " +
            "ORDER BY t.createdAt ASC, t.id ASC",
    )
    fun observeDiaryThoughts(from: Long, until: Long): Flow<List<DiaryThought>>

    /** Puts the bookmark where a corrected thought leaves it, which may be further back. */
    @Query("UPDATE books SET currentPage = :page WHERE id = :id")
    suspend fun setBookmark(id: Long, page: Int)

    @Insert
    suspend fun insertThought(thought: BookThought): Long

    @Query("UPDATE book_thoughts SET page = :page WHERE id = :id")
    suspend fun setThoughtPage(id: Long, page: Int)

    /** The reader's own words. They settle the matter: nothing is typed out over them. */
    @Query("UPDATE book_thoughts SET text = :text, processing = 'DONE' WHERE id = :id")
    suspend fun setThoughtTextByHand(id: Long, text: String)

    /** Typed-out words. Ignored if the thought is already done, because then the reader typed first. */
    @Query("UPDATE book_thoughts SET text = :text, processing = 'DONE' WHERE id = :id AND processing != 'DONE'")
    suspend fun finishTypingOut(id: Long, text: String): Int

    /** Never moves a finished thought back to waiting or failed. */
    @Query("UPDATE book_thoughts SET processing = :state WHERE id = :id AND processing != 'DONE'")
    suspend fun setThoughtProcessing(id: Long, state: ProcessingState)

    /** "Try again" on a voice note that could not be typed out. Words already there are left alone. */
    @Query("UPDATE book_thoughts SET processing = 'PENDING' WHERE id = :id AND (text IS NULL OR text = '')")
    suspend fun retryThought(id: Long)

    @Query("DELETE FROM book_thoughts WHERE id = :id")
    suspend fun deleteThought(id: Long)

    @Query("DELETE FROM book_thoughts WHERE bookId = :bookId")
    suspend fun deleteThoughtsOf(bookId: Long)

    // Summaries

    @Query("SELECT * FROM book_summaries WHERE bookId = :bookId")
    fun observeSummary(bookId: Long): Flow<BookSummary?>

    @Query("SELECT * FROM book_summaries WHERE bookId = :bookId")
    suspend fun summary(bookId: Long): BookSummary?

    @Upsert
    suspend fun upsertSummary(summary: BookSummary)

    @Query("DELETE FROM book_summaries WHERE bookId = :bookId")
    suspend fun deleteSummary(bookId: Long)
}

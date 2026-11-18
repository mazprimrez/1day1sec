package com.onedayonesecond.books

import android.content.Context
import android.net.Uri
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookDetails
import com.onedayonesecond.data.BookRepository
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ProcessingState
import com.onedayonesecond.data.ThoughtMedia
import com.onedayonesecond.work.BookWork
import com.onedayonesecond.work.JournalWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Adding books and logging thoughts. As with the diary, each thing is saved first;
 * only then is anything slow or networked tried (the cover, the typing out), so a
 * book or a thought is safe on the phone whatever the connection does.
 */
class BookActions(
    context: Context,
    private val books: BookRepository,
    private val lookup: BookLookup,
    private val processor: ThoughtProcessor,
    /** Reads a picked or freshly taken photo into an upright, shrunk scratch JPEG. */
    private val importPhoto: suspend (Uri) -> File?,
    private val appScope: CoroutineScope,
) {

    private val appContext = context.applicationContext

    /** Adds an edition found by search or by its barcode. Its cover follows in the background. */
    suspend fun add(result: BookResult): Book {
        val book = books.add(result.details())
        appScope.launch {
            val cover = lookup.downloadCover(result.coverUrl, result.isbn)
            if (cover != null) books.fillInCover(book, cover) else coverLater(book)
        }
        return book
    }

    /**
     * Adds a book typed in by hand.
     *
     * @param cover a photo of its cover already read with [readPhoto], if one was taken.
     */
    suspend fun addByHand(details: BookDetails, cover: File?): Book {
        val book = books.add(details, cover)
        if (cover == null) coverLater(book)
        return book
    }

    /** Uses a photo of the reader's own copy as the cover. False if the photo could not be read. */
    suspend fun useCoverPhoto(book: Book, photo: Uri): Boolean {
        val cover = importPhoto(photo) ?: return false
        books.setCover(book, cover)
        return true
    }

    /** A book with an ISBN and no cover gets one as soon as the phone is online. */
    private fun coverLater(book: Book) {
        if (book.isbn != null) BookWork.fetchCoversWhenOnline(appContext)
    }

    /** Saves a thought, and has a voice note without typed words typed out. */
    suspend fun logThought(
        book: Book,
        page: Int,
        text: String,
        media: ThoughtMedia?,
        showInDiary: Boolean,
    ): BookThought = books.logThought(book, page, text, media, showInDiary).also { thought ->
        if (thought.processing == ProcessingState.PENDING) typeOut(thought)
    }

    /** A photo for a thought or a cover, read into a scratch file. Null if it cannot be read. */
    suspend fun readPhoto(photo: Uri): File? = importPhoto(photo)

    /** Starts typing out now, and leaves a job behind that retries when the phone is online. */
    private fun typeOut(thought: BookThought) {
        JournalWork.typeOutWhenOnline(appContext)
        appScope.launch { processor.process(thought.id) }
    }

    /** "Try now" on a voice thought that is waiting or failed. */
    suspend fun retry(thought: BookThought) {
        books.retryThought(thought.id)
        JournalWork.typeOutWhenOnline(appContext)
        processor.process(thought.id)
    }
}

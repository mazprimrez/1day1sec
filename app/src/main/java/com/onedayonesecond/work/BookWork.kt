package com.onedayonesecond.work

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.books.BookSummaryGenerator
import java.util.concurrent.TimeUnit

/** Scheduling for the reading log's background jobs. */
object BookWork {

    private const val COVERS = "book_covers"
    private const val SUMMARY_PREFIX = "book_summary_"
    const val KEY_BOOK_ID = "bookId"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Leaves a job that looks up covers for books that have an ISBN and no cover: ones
     * added by hand, or added while the phone was offline. This is the "cover filled
     * in later" behind the offline search message.
     */
    fun fetchCoversWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<CoverWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(COVERS, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** After a summary could not be written offline: write it as soon as there is a connection. */
    fun writeSummaryWhenOnline(context: Context, bookId: Long) {
        val request = OneTimeWorkRequestBuilder<BookSummaryWorker>()
            .setConstraints(online)
            .setInputData(workDataOf(KEY_BOOK_ID to bookId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SUMMARY_PREFIX + bookId, ExistingWorkPolicy.KEEP, request)
    }
}

@UnstableApi
class CoverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        for (book in container.books.booksMissingCover()) {
            val isbn = book.isbn ?: continue
            // No cover anywhere is an answer too: the book keeps its title on a blush cover.
            container.bookLookup.coverByIsbn(isbn)?.let { container.books.fillInCover(book, it) }
        }
        return Result.success()
    }
}

@UnstableApi
class BookSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        val bookId = inputData.getLong(BookWork.KEY_BOOK_ID, -1)
        val book = container.books.book(bookId) ?: return Result.success()
        return when (container.bookSummaries.generate(book)) {
            BookSummaryGenerator.Outcome.Offline ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}

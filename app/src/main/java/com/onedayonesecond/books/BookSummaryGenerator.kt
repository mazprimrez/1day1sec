package com.onedayonesecond.books

import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.AiSignInNeededException
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRepository
import com.onedayonesecond.data.BookRules
import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.BookSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.LocalDate

/**
 * Gathers a book's thoughts, has its summary and review ideas written, checks them
 * and stores them. A stored summary is kept until the book's thoughts change, so the
 * model is asked once per change, not once per visit.
 */
class BookSummaryGenerator(
    private val books: BookRepository,
    private val ai: JournalAi,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {

    sealed interface Outcome {
        data object Written : Outcome

        /** Fewer than [BookRules.MIN_THOUGHTS_FOR_SUMMARY] thoughts have words. */
        data class NotEnough(val thoughtsWithWords: Int) : Outcome
        data object Offline : Outcome
        data object NotSetUp : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val _writing = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of the books whose summary is being written right now, for the "being written" state. */
    val writing: StateFlow<Set<Long>> = _writing.asStateFlow()

    private val _problems = MutableStateFlow<Map<Long, Outcome>>(emptyMap())

    /** Why a book's summary could not be written the last time it was tried, by book id. */
    val problems: StateFlow<Map<Long, Outcome>> = _problems.asStateFlow()

    val isConfigured: Boolean get() = ai.isConfigured

    /**
     * Writes [book]'s summary from the thoughts it has now. Safe to call again: a
     * second call while one is running for the same book returns straight away.
     */
    suspend fun generate(book: Book): Outcome {
        var claimed = false
        _writing.update { if (book.id in it) it else (it + book.id).also { claimed = true } }
        if (!claimed) return Outcome.Written

        return try {
            write(book).also { outcome ->
                _problems.update { if (outcome == Outcome.Written) it - book.id else it + (book.id to outcome) }
            }
        } finally {
            _writing.update { it - book.id }
        }
    }

    private suspend fun write(book: Book): Outcome {
        val thoughts = books.thoughtsOf(book.id)
        val withWords = BookRules.withWords(thoughts)
        if (withWords.size < BookRules.MIN_THOUGHTS_FOR_SUMMARY) return Outcome.NotEnough(withWords.size)
        if (!ai.isConfigured) return Outcome.NotSetUp

        // The freshest details: the bookmark may have moved since the page was opened.
        val current = books.book(book.id) ?: book
        val year = today().year
        val others = books.allBooks().filter {
            it.id != current.id && it.status == BookStatus.FINISHED && it.finishedOn?.year == year
        }

        val content = try {
            BookSummaryRequest.parse(ai.writeBookRecap(BookSummaryRequest.build(current, thoughts, others)), thoughts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            return Outcome.NotSetUp
        } catch (e: AiSignInNeededException) {
            return Outcome.Failed(e.message ?: "Confirm your Google account first.")
        } catch (e: IOException) {
            return Outcome.Offline
        } catch (e: AiException) {
            // A busy server is the same to the reader as no connection: it is tried again.
            return if (e.retryable) Outcome.Offline else Outcome.Failed(e.message ?: "The summary could not be written.")
        } ?: return Outcome.Failed("The summary came back empty.")

        books.saveSummary(
            BookSummary(
                bookId = current.id,
                json = content.encode(),
                // What it was written from. A thought added while the model was writing
                // makes it stale at once, which is right: it does not know that thought.
                thoughtCount = withWords.size,
                createdAt = now(),
            ),
        )
        return Outcome.Written
    }
}

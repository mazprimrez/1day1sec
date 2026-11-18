package com.onedayonesecond.books

import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.AiSignInNeededException
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.BookRepository
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Has the words of voice thoughts typed out, the same way as the diary's voice notes.
 * The recording is already saved before this runs, so the worst outcome is a thought
 * without its words yet. A photo of a page is never sent: its words are the book's.
 */
class ThoughtProcessor(
    private val books: BookRepository,
    private val ai: JournalAi,
) {

    // One at a time, so the same voice note cannot be sent twice at once.
    private val lock = Mutex()

    val isConfigured: Boolean get() = ai.isConfigured

    /** Tries one thought now. Returns the state it ended in, or null if it is gone. */
    suspend fun process(thoughtId: Long): ProcessingState? = lock.withLock {
        val thought = books.thought(thoughtId) ?: return null
        if (!thought.needsText || thought.processing == ProcessingState.DONE) return thought.processing
        val state = attempt(thought)
        if (state != ProcessingState.DONE) books.setProcessing(thoughtId, state)
        state
    }

    /** Tries every thought still waiting for its words. True if any are still waiting afterwards. */
    suspend fun processWaiting(): Boolean {
        var left = false
        for (thought in books.unprocessedThoughts()) {
            val state = process(thought.id)
            if (state == ProcessingState.PENDING || state == ProcessingState.WAITING_FOR_NETWORK) left = true
        }
        return left
    }

    private suspend fun attempt(thought: BookThought): ProcessingState {
        val file = thought.mediaFile?.takeIf { it.exists() } ?: return ProcessingState.FAILED
        return try {
            // If the reader typed the words meanwhile, this changes nothing and theirs stand.
            books.finishTypingOut(thought.id, ai.transcribe(file))
            ProcessingState.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            ProcessingState.FAILED
        } catch (e: AiSignInNeededException) {
            ProcessingState.FAILED
        } catch (e: IOException) {
            ProcessingState.WAITING_FOR_NETWORK
        } catch (e: AiException) {
            if (e.retryable) ProcessingState.WAITING_FOR_NETWORK else ProcessingState.FAILED
        }
    }
}

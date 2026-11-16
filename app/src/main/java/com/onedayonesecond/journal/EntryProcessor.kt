package com.onedayonesecond.journal

import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Has the words of voice notes, notebook photos and pen pages typed out. The media
 * is already saved before this runs, so nothing here can lose an entry: the worst
 * outcome is an entry without its text yet.
 */
class EntryProcessor(
    private val journal: JournalRepository,
    private val ai: JournalAi,
) {

    // One at a time. It keeps a slow connection from being split between uploads, and
    // makes it impossible for the same entry to be sent twice at once.
    private val lock = Mutex()

    val isConfigured: Boolean get() = ai.isConfigured

    /** Tries one entry now. Returns the state it ended in, or null if it is gone. */
    suspend fun process(entryId: Long): ProcessingState? = lock.withLock {
        val entry = journal.entry(entryId) ?: return null
        if (!entry.needsText || entry.processing == ProcessingState.DONE) return entry.processing
        val state = attempt(entry)
        if (state != ProcessingState.DONE) journal.setProcessing(entryId, state)
        state
    }

    /**
     * Tries every entry still waiting for its text.
     *
     * @return true if any are still waiting afterwards, so the caller knows to come back.
     */
    suspend fun processWaiting(): Boolean {
        var left = false
        for (entry in journal.unprocessedEntries()) {
            val state = process(entry.id)
            if (state == ProcessingState.PENDING || state == ProcessingState.WAITING_FOR_NETWORK) left = true
        }
        return left
    }

    private suspend fun attempt(entry: Entry): ProcessingState {
        val file = entry.mediaFile?.takeIf { it.exists() } ?: return ProcessingState.FAILED
        return try {
            val text = when (entry.type) {
                EntryType.VOICE -> ai.transcribe(file)
                else -> ai.extractText(file)
            }
            // An empty answer is a valid one: a page with nothing written on it. If the
            // user typed the words meanwhile, this changes nothing and theirs stand.
            journal.finishTypingOut(entry.id, text)
            ProcessingState.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            ProcessingState.FAILED
        } catch (e: IOException) {
            ProcessingState.WAITING_FOR_NETWORK
        } catch (e: AiException) {
            // A busy server is the same to the user as no connection: it will be tried again.
            if (e.retryable) ProcessingState.WAITING_FOR_NETWORK else ProcessingState.FAILED
        }
    }
}

package com.onedayonesecond.journal

import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.data.RecapFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.LocalDate

/** Gathers a period's days, has the recap written, checks it and stores it. */
class RecapGenerator(
    private val clips: ClipRepository,
    private val journal: JournalRepository,
    private val ai: JournalAi,
    private val prefs: JournalPrefs,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface Outcome {
        data object Written : Outcome

        /** Fewer than [RecapRules.MIN_DAYS_WITH_ENTRIES] days were written on. */
        data class NotEnough(val daysWithEntries: Int) : Outcome
        data object Offline : Outcome
        data object NotSetUp : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val _writing = MutableStateFlow<Set<String>>(emptySet())

    /** Keys of the recaps being written right now, for the "still writing" state. */
    val writing: StateFlow<Set<String>> = _writing.asStateFlow()

    val isConfigured: Boolean get() = ai.isConfigured

    /** Every day from [start] to [end] that has a clip, a mood, a place or an entry. */
    suspend fun summaries(start: LocalDate, end: LocalDate): List<DaySummary> {
        val clipsByDay = clips.clipsBetween(start, end).associateBy { it.date }
        val metaByDay = journal.metaBetween(start, end).associateBy { it.date }
        val entriesByDay = journal.entriesBetween(start, end).groupBy { it.date }
        return (clipsByDay.keys + metaByDay.keys + entriesByDay.keys).sorted().map { date ->
            val entries = entriesByDay[date].orEmpty()
            DaySummary(
                date = date,
                hasClip = date in clipsByDay,
                caption = clipsByDay[date]?.caption.orEmpty(),
                mood = metaByDay[date]?.mood,
                place = metaByDay[date]?.placeName,
                texts = entries.mapNotNull { it.text?.trim()?.ifEmpty { null } },
                entryCount = entries.size,
            )
        }
    }

    /**
     * Writes [period]'s recap. Safe to call again: a second call while one is running
     * for the same period returns straight away, and a recap that already exists is
     * written afresh only because the user asked for it.
     */
    suspend fun generate(period: RecapPeriod): Outcome {
        val key = period.key
        var claimed = false
        _writing.update { if (key in it) it else (it + key).also { claimed = true } }
        if (!claimed) return Outcome.Written

        return try {
            val outcome = write(period)
            prefs.setRecapFailure(
                key,
                when (outcome) {
                    Outcome.Offline -> RecapFailure(RecapFailureReason.OFFLINE, now())
                    Outcome.NotSetUp -> RecapFailure(RecapFailureReason.NOT_SET_UP, now())
                    is Outcome.Failed -> RecapFailure(RecapFailureReason.FAILED, now())
                    else -> null
                },
            )
            outcome
        } finally {
            _writing.update { it - key }
        }
    }

    private suspend fun write(period: RecapPeriod): Outcome {
        val days = summaries(period.start, period.end)
        val written = days.count { it.entryCount > 0 }
        if (!RecapRules.hasEnough(written)) return Outcome.NotEnough(written)
        if (!ai.isConfigured) return Outcome.NotSetUp

        val before = period.previous()
        val previousDays = summaries(before.start, before.end)
        val request = RecapRequest.build(
            period = period,
            days = days,
            previous = previousDays.takeIf { it.isNotEmpty() }?.let(PeriodStats::of),
            name = prefs.name.first(),
        )

        val draft = try {
            RecapRequest.parse(ai.writeRecap(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            return Outcome.NotSetUp
        } catch (e: IOException) {
            return Outcome.Offline
        } catch (e: AiException) {
            return Outcome.Failed(e.message ?: "The letter could not be written.")
        } ?: return Outcome.Failed("The letter came back empty.")

        // The quote must be the user's own words. If the model's is not in the entries,
        // one is taken from them directly.
        val quote = JournalMath.verifyQuote(draft.quote, days, draft.quoteDate)
            ?: JournalMath.fallbackQuote(days)
        val content = draft.content.copy(
            places = if (period is RecapPeriod.Year) JournalMath.daysPerCity(days.map { it.place }) else emptyList(),
        )
        journal.saveRecap(
            Recap(
                key = period.key,
                title = draft.title,
                quote = quote?.first.orEmpty(),
                quoteDate = quote?.second ?: period.end,
                json = content.encode(),
                createdAt = now(),
            ),
        )
        return Outcome.Written
    }
}

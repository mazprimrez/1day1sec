package com.onedayonesecond.ui.recaps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.journal.RecapContent
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.nav.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Where a period's letter stands. */
sealed interface RecapStatus {
    data object Loading : RecapStatus

    data class Ready(val recap: Recap, val content: RecapContent) : RecapStatus

    /** Being written right now. */
    data object Writing : RecapStatus

    /** Too few days were written on for a letter. */
    data class NotEnough(val daysWritten: Int) : RecapStatus

    /** The period is not over yet; the letter comes on [arrives]. */
    data class NotDue(val arrives: LocalDate, val daysWritten: Int) : RecapStatus

    /** Due, enough to write from, and still not there. [failure] says why, if it was tried. */
    data class Missing(val failure: RecapFailure?) : RecapStatus

    /** This build has no AI service to write letters with. */
    data object NotSetUp : RecapStatus
}

data class RecapUiState(
    val period: RecapPeriod,
    val status: RecapStatus = RecapStatus.Loading,
    /** The period's seconds by day, for the tiles and for "Play this week". */
    val clips: Map<LocalDate, ClipEntity> = emptyMap(),
    val moods: Map<LocalDate, Mood> = emptyMap(),
    val name: String = "",
    val today: LocalDate = LocalDate.now(),
)

@UnstableApi
class RecapViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // A key that cannot be read falls back to this week rather than crashing the page.
    val period: RecapPeriod = RecapPeriod.parse(savedStateHandle.get<String>(Routes.ARG_KEY).orEmpty())
        ?: RecapPeriod.weekOf(LocalDate.now())

    private data class Days(
        val clips: Map<LocalDate, ClipEntity>,
        val moods: Map<LocalDate, Mood>,
        val daysWritten: Int,
    )

    private val days = combine(
        container.repository.observeBetween(period.start, period.end),
        container.journal.observeMetaBetween(period.start, period.end),
        container.journal.observeEntriesBetween(period.start, period.end),
    ) { clips, metas, entries ->
        Days(
            clips = clips.filter { it.file.exists() }.associateBy { it.date },
            moods = metas.mapNotNull { meta -> meta.mood?.let { meta.date to it } }.toMap(),
            daysWritten = entries.map { it.date }.distinct().size,
        )
    }

    val state: StateFlow<RecapUiState> = combine(
        days,
        container.journal.observeRecap(period.key),
        container.recapGenerator.writing,
        container.journalPrefs.recapFailures,
        container.journalPrefs.name,
    ) { days, recap, writing, failures, name ->
        val status = when {
            recap != null -> RecapStatus.Ready(recap, RecapContent.decode(recap.json))
            period.key in writing -> RecapStatus.Writing
            // Said first, even for a period still under way: it is the one thing the
            // user can do something about.
            !RecapRules.hasEnough(days.daysWritten) -> RecapStatus.NotEnough(days.daysWritten)
            !RecapRules.isDue(period, LocalDateTime.now()) ->
                RecapStatus.NotDue(RecapRules.arrives(period), days.daysWritten)
            !container.recapGenerator.isConfigured -> RecapStatus.NotSetUp
            else -> RecapStatus.Missing(failures[period.key])
        }
        RecapUiState(
            period = period,
            status = status,
            clips = days.clips,
            moods = days.moods,
            name = name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState(period))

    /** "Write the letter now". Runs on the app's scope, so leaving the page does not stop it. */
    fun writeNow() {
        container.appScope.launch { container.recapGenerator.generate(period) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                RecapViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

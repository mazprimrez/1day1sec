package com.onedayonesecond.ui.day

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
import com.onedayonesecond.data.ClipMoveRules
import com.onedayonesecond.data.DayMeta
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.theme.MessageAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayUiState(
    val date: LocalDate,
    val clip: ClipEntity? = null,
    val meta: DayMeta? = null,
    val entries: List<Entry> = emptyList(),
    /** Videos in the phone's gallery made on this day, offered when the day has no second. */
    val galleryCount: Int = 0,
    /** False when this build has no AI service, so nothing can be typed out. */
    val canTypeOut: Boolean = true,
    /** False until the first read from the database, so the page does not flash "empty". */
    val loaded: Boolean = false,
) {
    val isToday: Boolean get() = date == LocalDate.now()
    val mood: Mood? get() = meta?.mood
    val place: String? get() = meta?.placeName
}

@UnstableApi
class DayViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle.get<Long>(Routes.ARG_DATE)!!)

    private val repository = container.repository
    private val journal = container.journal
    private val messages = container.messages
    private val galleryCount = MutableStateFlow(0)

    val state: StateFlow<DayUiState> = combine(
        repository.observeBetween(date, date).map { it.firstOrNull() },
        journal.observeMeta(date),
        journal.observeEntries(date),
        galleryCount,
    ) { clip, meta, entries, gallery ->
        DayUiState(
            date = date,
            clip = clip,
            meta = meta,
            entries = entries,
            galleryCount = gallery,
            canTypeOut = container.entryProcessor.isConfigured,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(date))

    private val _movedTo = MutableStateFlow<LocalDate?>(null)

    /** Set once the day's second has been moved, so the screen can follow it there. */
    val movedTo: StateFlow<LocalDate?> = _movedTo.asStateFlow()

    /** Quietly finds nothing unless gallery access was granted on the Days tab. */
    fun refreshGallery() {
        viewModelScope.launch { galleryCount.value = container.galleryScanner.scan()[date]?.size ?: 0 }
    }

    fun setMood(mood: Mood?) {
        viewModelScope.launch { journal.setMood(date, mood) }
    }

    fun setPlace(place: String) {
        viewModelScope.launch { journal.setPlace(date, place) }
    }

    /** Where the phone is now, to offer in the place dialog. Null if not allowed or not known. */
    suspend fun currentPlace(): String? = container.places.current()

    /** Sets, changes or (with blank text) removes the caption drawn above the date on the reel. */
    fun setCaption(text: String) {
        viewModelScope.launch {
            try {
                repository.setCaption(date, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.show(e.message ?: "Could not save the caption.")
            }
        }
    }

    /**
     * Deletes the day's second. There is no "are you sure": the second goes to
     * Recently deleted and the message that follows can undo it on the spot.
     */
    fun deleteClip() {
        val clip = state.value.clip ?: return
        // On the app's scope, so leaving the page straight away cannot stop it half-way.
        container.appScope.launch {
            try {
                val deleted = repository.deleteClip(clip)
                messages.show(
                    "Second for ${RecapLabels.dayAndMonth(date)} deleted.",
                    MessageAction("Undo", run = { undoDelete(deleted.id) }),
                    MessageAction("Record a new one", route = Routes.camera(date)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.show(e.message ?: "Could not delete this second.")
            }
        }
    }

    private suspend fun undoDelete(deletedId: Long) {
        try {
            repository.restoreDeleted(deletedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.show(
                "That second could not be put back: ${e.message ?: "something went wrong"} " +
                    "It is still in Recently deleted.",
            )
        }
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch { journal.deleteEntry(entry) }
    }

    /** "Try now" on a voice note or page that is waiting for the internet or failed. */
    fun retry(entry: Entry) {
        container.appScope.launch { container.journalActions.retry(entry) }
    }

    /** The seconds of [month], for the dots in the move dialog's calendar. */
    suspend fun clipsIn(month: YearMonth): Map<LocalDate, ClipEntity> =
        repository.clipsBetween(month.atDay(1), month.atEndOfMonth()).associateBy { it.date }

    /**
     * Files this day's second under [target]. The dialog has already shown what is
     * there and been told to replace it, if anything was; the rules are checked again
     * here in case the calendar changed in the meantime.
     */
    fun move(target: LocalDate, replace: Boolean) {
        val clip = state.value.clip ?: return
        container.appScope.launch {
            val check = ClipMoveRules.check(
                from = clip.date,
                to = target,
                today = LocalDate.now(),
                targetHasClip = repository.hasClipFor(target),
            )
            when (check) {
                ClipMoveRules.Check.SAME_DAY -> return@launch messages.show("That second is already on this day.")
                ClipMoveRules.Check.FUTURE ->
                    return@launch messages.show("A second can't be moved to a day that hasn't happened yet.")
                ClipMoveRules.Check.NEEDS_REPLACE -> if (!replace) {
                    return@launch messages.show(
                        "${RecapLabels.dayAndMonth(target)} has a second now. Open Move again to replace it.",
                    )
                }
                ClipMoveRules.Check.OK -> Unit
            }
            try {
                // Noted before the move rather than after: if noting it fails, nothing has
                // moved yet. And if the move then fails, the next backup sees a clip still
                // on that day and just drops the note, deleting nothing.
                container.pendingDriveRemovals.add(clip.date)
                repository.moveClip(clip, target, replaceExisting = replace)
                messages.show(
                    if (replace) {
                        "Moved to ${RecapLabels.dayAndMonth(target)}. The second it replaced is in Recently deleted."
                    } else {
                        "Moved to ${RecapLabels.dayAndMonth(target)}."
                    },
                )
                _movedTo.value = target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.show(e.message ?: "Could not move this second.")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                DayViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

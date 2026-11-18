package com.onedayonesecond.ui.home

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.media.GalleryAccessLevel
import com.onedayonesecond.media.GalleryScanner
import com.onedayonesecond.media.GalleryVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val clipsByDate: Map<LocalDate, ClipEntity> = emptyMap(),
    /** The mood picked for each day of the shown month, where one was picked. */
    val moodsByDate: Map<LocalDate, Mood> = emptyMap(),
    /** Days of the shown month that have a second. */
    val keptInMonth: Int = 0,
    /** Days of the shown month that have happened: all of them for a past month. */
    val daysSoFar: Int = 0,
    val hasClipToday: Boolean = false,
    /** Today's place, for the "Add to today" sheet. Null when unknown or switched off. */
    val todayPlace: String? = null,
    /** How many gallery videos were made on each day. Empty until access is granted. */
    val galleryCounts: Map<LocalDate, Int> = emptyMap(),
    /** Days that have gallery videos but no clip yet, newest first. */
    val unusedGalleryDays: List<LocalDate> = emptyList(),
    val galleryAccess: GalleryAccessLevel = GalleryAccessLevel.NONE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class HomeViewModel(container: AppContainer) : ViewModel() {

    private val prefs = container.journalPrefs

    private val repository: ClipRepository = container.repository
    private val scanner: GalleryScanner = container.galleryScanner
    private val _month = MutableStateFlow(YearMonth.now())
    private val gallery = MutableStateFlow<Map<LocalDate, List<GalleryVideo>>>(emptyMap())
    private val galleryAccess = MutableStateFlow(GalleryAccessLevel.NONE)

    private val monthClips = _month.flatMapLatest { month ->
        repository.observeBetween(month.atDay(1), month.atEndOfMonth())
    }

    private val monthMoods = _month.flatMapLatest { month ->
        container.journal.observeMetaBetween(month.atDay(1), month.atEndOfMonth())
    }

    // Clips and moods travel together, which keeps the big combine below at five flows.
    private val monthDays = combine(monthClips, monthMoods) { clips, metas ->
        clips to metas.mapNotNull { meta -> meta.mood?.let { meta.date to it } }.toMap()
    }

    private val galleryState = combine(gallery, galleryAccess) { byDate, access -> byDate to access }

    private val todayPlace = container.journal.observeMeta(LocalDate.now()).map { it?.placeName }

    val state: StateFlow<HomeUiState> = combine(
        _month,
        monthDays,
        repository.observeAll(),
        galleryState,
        todayPlace,
    ) { month, (clips, moods), all, (galleryByDate, access), todayPlace ->
        val today = LocalDate.now()
        val clipDates = all.mapTo(HashSet()) { it.date }
        HomeUiState(
            month = month,
            clipsByDate = clips.associateBy { it.date },
            moodsByDate = moods,
            keptInMonth = clips.size,
            daysSoFar = when {
                month < YearMonth.from(today) -> month.lengthOfMonth()
                month == YearMonth.from(today) -> today.dayOfMonth
                else -> 0
            },
            hasClipToday = today in clipDates,
            todayPlace = todayPlace,
            galleryCounts = galleryByDate.mapValues { it.value.size },
            unusedGalleryDays = galleryByDate.keys
                .filter { it !in clipDates && !it.isAfter(today) }
                .sortedDescending(),
            galleryAccess = access,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Re-reads which days have gallery videos. Does nothing visible until the user
     * has granted gallery access, so it is safe to call on every return to this
     * screen — which is how a video shot a minute ago shows up.
     */
    fun refreshGallery() {
        viewModelScope.launch {
            galleryAccess.value = scanner.accessLevel()
            gallery.value = scanner.scan()
        }
    }

    fun showMonth(month: YearMonth) {
        _month.value = minOf(month, YearMonth.now())
    }

    fun showPreviousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun showNextMonth() {
        // There is nothing to record in the future, so browsing stops at the
        // current month.
        if (_month.value < YearMonth.now()) _month.value = _month.value.plusMonths(1)
    }

    /** Whether the daily reminder is switched on in Settings. */
    suspend fun reminderEnabled(): Boolean = prefs.reminderEnabled.first()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                HomeViewModel(app.container)
            }
        }
    }
}

package com.onedayonesecond.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.ClipRepository
import android.graphics.Bitmap
import com.onedayonesecond.media.ClipThumbnails
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
    val totalClips: Int = 0,
    val hasClipToday: Boolean = false,
    /** How many gallery videos were made on each day. Empty until access is granted. */
    val galleryCounts: Map<LocalDate, Int> = emptyMap(),
    /** Days that have gallery videos but no clip yet, newest first. */
    val unusedGalleryDays: List<LocalDate> = emptyList(),
    val galleryAccess: GalleryAccessLevel = GalleryAccessLevel.NONE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class HomeViewModel(container: AppContainer) : ViewModel() {

    private val repository: ClipRepository = container.repository
    private val scanner: GalleryScanner = container.galleryScanner
    private val clipThumbnails: ClipThumbnails = container.clipThumbnails
    private val _month = MutableStateFlow(YearMonth.now())
    private val gallery = MutableStateFlow<Map<LocalDate, List<GalleryVideo>>>(emptyMap())
    private val galleryAccess = MutableStateFlow(GalleryAccessLevel.NONE)

    private val monthClips = _month.flatMapLatest { month ->
        repository.observeBetween(month.atDay(1), month.atEndOfMonth())
    }

    val state: StateFlow<HomeUiState> = combine(
        _month,
        monthClips,
        repository.observeAll(),
        gallery,
        galleryAccess,
    ) { month, clips, all, galleryByDate, access ->
        val today = LocalDate.now()
        val clipDates = all.mapTo(HashSet()) { it.date }
        HomeUiState(
            month = month,
            clipsByDate = clips.associateBy { it.date },
            totalClips = all.size,
            hasClipToday = today in clipDates,
            galleryCounts = galleryByDate.mapValues { it.value.size },
            unusedGalleryDays = galleryByDate.keys
                .filter { it !in clipDates && !it.isAfter(today) }
                .sortedDescending(),
            galleryAccess = access,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The calendar picture for a day's clip; null leaves the cell as a plain tile. */
    suspend fun thumbnailFor(clip: ClipEntity): Bitmap? = clipThumbnails.load(clip)

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

    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch { repository.deleteClip(clip) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                HomeViewModel(app.container)
            }
        }
    }
}

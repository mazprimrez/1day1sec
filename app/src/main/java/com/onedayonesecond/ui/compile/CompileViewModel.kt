package com.onedayonesecond.ui.compile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.media.DatedClip
import com.onedayonesecond.media.GalleryRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RangeOption(val label: String) {
    ALL_TIME("All time"),
    LAST_30_DAYS("Last 30 days"),
    THIS_MONTH("This month"),
}

/**
 * IDLE: nothing built. COMPILING: stitching. READY: built, but saving it to the
 * gallery failed and can be tried again. EXPORTING: saving. EXPORTED: saved.
 */
enum class CompilePhase { IDLE, COMPILING, READY, EXPORTING, EXPORTED }

data class CompileUiState(
    val range: RangeOption = RangeOption.ALL_TIME,
    val clipCount: Int = 0,
    val phase: CompilePhase = CompilePhase.IDLE,
    val progress: Float = 0f,
    val reelFile: File? = null,
    val exportedName: String? = null,
    val showDates: Boolean = true,
    val showCaptions: Boolean = true,
    val showPlaces: Boolean = true,
    /** How many of the [clipCount] clips in the range have a caption. */
    val captionCount: Int = 0,
    /** How many of the [clipCount] clips in the range are on a day that has a place. */
    val placeCount: Int = 0,
    /** The newest clip in the range, shown as the preview until a reel is made. */
    val previewClip: ClipEntity? = null,
    val dateStyle: DateLabelStyle = DateLabelStyle(),
    val error: String? = null,
)

@UnstableApi
class CompileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CompileUiState())
    val state: StateFlow<CompileUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refreshCount()
        viewModelScope.launch {
            val saved = container.dateStyleStore.load()
            _state.update { it.copy(dateStyle = saved) }
        }
    }

    fun selectRange(range: RangeOption) {
        _state.update {
            it.copy(
                range = range,
                // Any previously built reel belongs to the old range.
                phase = CompilePhase.IDLE,
                reelFile = null,
                exportedName = null,
                progress = 0f,
            )
        }
        refreshCount()
    }

    fun setShowDates(show: Boolean) {
        _state.update {
            it.copy(
                showDates = show,
                // A reel built with the old setting no longer matches the toggle.
                phase = CompilePhase.IDLE,
                reelFile = null,
                exportedName = null,
                progress = 0f,
            )
        }
    }

    fun setShowCaptions(show: Boolean) {
        _state.update {
            it.copy(
                showCaptions = show,
                // A reel built with the old setting no longer matches the toggle.
                phase = CompilePhase.IDLE,
                reelFile = null,
                exportedName = null,
                progress = 0f,
            )
        }
    }

    fun setShowPlaces(show: Boolean) {
        _state.update {
            it.copy(
                showPlaces = show,
                // A reel built with the old setting no longer matches the toggle.
                phase = CompilePhase.IDLE,
                reelFile = null,
                exportedName = null,
                progress = 0f,
            )
        }
    }

    fun setDateStyle(style: DateLabelStyle) {
        if (style == _state.value.dateStyle) return
        _state.update {
            it.copy(
                dateStyle = style,
                // A reel built with the old style no longer matches the choice.
                phase = CompilePhase.IDLE,
                reelFile = null,
                exportedName = null,
                progress = 0f,
            )
        }
        viewModelScope.launch { container.dateStyleStore.save(style) }
    }

    /** Also called when the tab is returned to, since seconds may have been added meanwhile. */
    fun refreshCount() {
        viewModelScope.launch {
            val (start, end) = resolveRange(_state.value.range)
            val clips = container.repository.clipsBetween(start, end)
            val places = placesBetween(start, end)
            _state.update {
                it.copy(
                    clipCount = clips.size,
                    captionCount = clips.count { clip -> clip.caption.isNotEmpty() },
                    placeCount = clips.count { clip -> clip.date in places },
                    previewClip = clips.lastOrNull(),
                )
            }
        }
    }

    /** The place of each day in the range that has one. */
    private suspend fun placesBetween(start: LocalDate, end: LocalDate): Map<LocalDate, String> =
        container.journal.metaBetween(start, end)
            .mapNotNull { meta -> meta.placeName?.let { meta.date to it } }
            .toMap()

    /**
     * "Make my reel": stitches the range's seconds together and saves the result to
     * Movies/Kilasan in one go. The finished reel stays here to be played.
     */
    fun makeReel() {
        if (job?.isActive == true) return
        _state.update { it.copy(phase = CompilePhase.COMPILING, progress = 0f, error = null, exportedName = null) }

        job = viewModelScope.launch {
            try {
                val (start, end) = resolveRange(_state.value.range)
                val clips = container.repository.clipsBetween(start, end)
                if (clips.isEmpty()) {
                    _state.update {
                        it.copy(phase = CompilePhase.IDLE, error = "There are no seconds in this range yet.")
                    }
                    return@launch
                }
                val places = placesBetween(start, end)

                val output = container.storage.newReelFile()
                container.compiler.compile(
                    clips = clips.map { DatedClip(it.date, it.file, it.caption, places[it.date].orEmpty()) },
                    output = output,
                    showDates = _state.value.showDates,
                    showCaptions = _state.value.showCaptions,
                    showPlaces = _state.value.showPlaces,
                    dateStyle = _state.value.dateStyle,
                    onProgress = { fraction ->
                        _state.update { it.copy(progress = fraction) }
                    },
                )
                _state.update {
                    it.copy(phase = CompilePhase.READY, progress = 1f, reelFile = output)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = CompilePhase.IDLE,
                        error = e.message ?: "The reel could not be made.",
                    )
                }
                return@launch
            }
            export()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(phase = CompilePhase.IDLE, progress = 0f) }
    }

    /** Saves the built reel to the gallery. Also the retry when that step alone failed. */
    fun export() {
        val reel = _state.value.reelFile ?: return
        if (_state.value.phase == CompilePhase.EXPORTING) return
        _state.update { it.copy(phase = CompilePhase.EXPORTING, error = null) }

        viewModelScope.launch {
            try {
                val name = "${GalleryRules.OWN_EXPORT_PREFIX}${LocalDate.now().format(FILE_DATE)}.mp4"
                container.exporter.exportToGallery(reel, name)
                _state.update { it.copy(phase = CompilePhase.EXPORTED, exportedName = name) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = CompilePhase.READY,
                        error = "The reel was made but could not be saved to Movies. ${e.message.orEmpty()}".trim(),
                    )
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun resolveRange(range: RangeOption): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (range) {
            RangeOption.ALL_TIME ->
                (container.repository.earliestDate() ?: today) to today
            RangeOption.LAST_30_DAYS -> today.minusDays(29) to today
            RangeOption.THIS_MONTH -> today.withDayOfMonth(1) to today
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                CompileViewModel(app.container)
            }
        }
    }
}

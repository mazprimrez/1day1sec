package com.onedayonesecond.ui.compile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.media.DatedClip
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

enum class CompilePhase { IDLE, COMPILING, READY, EXPORTING, EXPORTED }

data class CompileUiState(
    val range: RangeOption = RangeOption.ALL_TIME,
    val clipCount: Int = 0,
    val phase: CompilePhase = CompilePhase.IDLE,
    val progress: Float = 0f,
    val reelFile: File? = null,
    val exportedName: String? = null,
    val showDates: Boolean = true,
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

    private fun refreshCount() {
        viewModelScope.launch {
            val (start, end) = resolveRange(_state.value.range)
            val count = container.repository.clipsBetween(start, end).size
            _state.update { it.copy(clipCount = count) }
        }
    }

    fun compile() {
        if (job?.isActive == true) return
        _state.update { it.copy(phase = CompilePhase.COMPILING, progress = 0f, error = null) }

        job = viewModelScope.launch {
            try {
                val (start, end) = resolveRange(_state.value.range)
                val clips = container.repository.clipsBetween(start, end)
                if (clips.isEmpty()) {
                    _state.update {
                        it.copy(phase = CompilePhase.IDLE, error = "No clips in this range yet")
                    }
                    return@launch
                }

                val output = container.storage.newReelFile()
                container.compiler.compile(
                    clips = clips.map { DatedClip(it.date, it.file) },
                    output = output,
                    showDates = _state.value.showDates,
                    dateStyle = _state.value.dateStyle,
                    onProgress = { fraction ->
                        _state.update { it.copy(progress = fraction) }
                    },
                )
                _state.update {
                    it.copy(phase = CompilePhase.READY, progress = 1f, reelFile = output)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = CompilePhase.IDLE,
                        error = e.message ?: "Could not build the reel",
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(phase = CompilePhase.IDLE, progress = 0f) }
    }

    fun export() {
        val reel = _state.value.reelFile ?: return
        _state.update { it.copy(phase = CompilePhase.EXPORTING, error = null) }

        viewModelScope.launch {
            try {
                val name = "1day1sec-${LocalDate.now().format(FILE_DATE)}.mp4"
                container.exporter.exportToGallery(reel, name)
                _state.update { it.copy(phase = CompilePhase.EXPORTED, exportedName = name) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = CompilePhase.READY,
                        error = e.message ?: "Could not save to the gallery",
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

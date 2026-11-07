package com.onedayonesecond.ui.trim

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
import com.onedayonesecond.media.VideoMetadata
import com.onedayonesecond.media.VideoSpec
import com.onedayonesecond.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class TrimUiState(
    val sourceFile: File,
    val date: LocalDate,
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val isProcessing: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Latest start offset that still leaves a full second of source. */
    val maxStartMs: Long get() = (durationMs - VideoSpec.CLIP_DURATION_MS).coerceAtLeast(0)
    val endMs: Long get() = startMs + VideoSpec.CLIP_DURATION_MS
}

@UnstableApi
class TrimViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TrimUiState(
            sourceFile = File(Routes.decodePath(savedStateHandle.get<String>(Routes.ARG_PATH)!!)),
            date = LocalDate.ofEpochDay(savedStateHandle.get<Long>(Routes.ARG_DATE)!!),
        ),
    )
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val duration = VideoMetadata.durationMs(_state.value.sourceFile)
            _state.update { it.copy(durationMs = duration) }
        }
    }

    fun setStart(startMs: Long) {
        _state.update { it.copy(startMs = startMs.coerceIn(0, it.maxStartMs)) }
    }

    fun confirm() {
        val current = _state.value
        if (current.isProcessing) return
        _state.update { it.copy(isProcessing = true, error = null) }

        viewModelScope.launch {
            try {
                val output = container.storage.newTrimOutputFile()
                val duration = container.trimmer.trimToOneSecond(
                    input = current.sourceFile,
                    output = output,
                    startMs = current.startMs,
                )
                container.repository.saveClip(current.date, output, duration)
                current.sourceFile.delete()
                _state.update { it.copy(isProcessing = false, saved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, error = e.message ?: "Could not trim this clip")
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                TrimViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

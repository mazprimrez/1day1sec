package com.onedayonesecond.ui.gallery

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.media.GalleryImporter
import com.onedayonesecond.media.GalleryVideo
import com.onedayonesecond.ui.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class GalleryDayUiState(
    val date: LocalDate,
    val loading: Boolean = true,
    val videos: List<GalleryVideo> = emptyList(),
    /** The video currently being copied in, if any. */
    val importingId: Long? = null,
    /** A video that has been copied in and is ready for the trim step. */
    val readyFile: File? = null,
    val error: String? = null,
)

class GalleryDayViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GalleryDayUiState(
            date = LocalDate.ofEpochDay(savedStateHandle.get<Long>(Routes.ARG_DATE)!!),
        ),
    )
    val state: StateFlow<GalleryDayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val onThatDay = container.galleryScanner.scan()[_state.value.date].orEmpty()
            _state.update { it.copy(loading = false, videos = onThatDay) }
        }
    }

    suspend fun thumbnail(video: GalleryVideo): Bitmap? = container.thumbnails.load(video)

    fun pick(video: GalleryVideo) {
        if (_state.value.importingId != null) return
        _state.update { it.copy(importingId = video.id, error = null) }

        viewModelScope.launch {
            try {
                val imported = container.importer.import(video.uri)
                _state.update { it.copy(importingId = null, readyFile = imported.file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryImporter.ImportException) {
                _state.update { it.copy(importingId = null, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(importingId = null, error = "Could not import that video.") }
            }
        }
    }

    /** The screen has handed [GalleryDayUiState.readyFile] on, so it must not be handed on twice. */
    fun readyFileConsumed() {
        _state.update { it.copy(readyFile = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                GalleryDayViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

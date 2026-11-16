package com.onedayonesecond.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipStorage
import com.onedayonesecond.media.GalleryImporter
import com.onedayonesecond.media.VideoSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Recording(val elapsedMs: Long) : CaptureState
    data object Finalising : CaptureState
    /** Copying a video the user picked from their gallery into the app. */
    data object Importing : CaptureState
    data class Captured(val file: File, val durationMs: Long) : CaptureState
    data class Failed(val message: String) : CaptureState
}

@UnstableApi
class CameraViewModel(private val container: AppContainer) : ViewModel() {

    private val storage: ClipStorage = container.storage
    private val importer: GalleryImporter = container.importer

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _place = MutableStateFlow<String?>(null)

    /** Shown under the date: the day's place, or where the phone is if recording for today. */
    val place: StateFlow<String?> = _place.asStateFlow()

    fun findPlace(date: java.time.LocalDate) {
        viewModelScope.launch {
            _place.value = container.journal.meta(date)?.placeName ?: container.journalActions.placeFor(date)
        }
    }

    private var recording: Recording? = null
    private var pendingFile: File? = null
    private var timerJob: Job? = null

    val isRecording: Boolean get() = recording != null

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        videoCapture: VideoCapture<Recorder>,
        audioEnabled: Boolean,
    ) {
        if (recording != null) return

        val file = storage.newCaptureFile()
        pendingFile = file

        val pending = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply { if (audioEnabled) withAudioEnabled() }

        _state.value = CaptureState.Recording(0)

        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) onFinalize(event)
        }

        // CameraX's own Status events arrive roughly once a second, which is too
        // coarse both for a smooth countdown and for stopping on time, so the
        // clock and the hard stop are driven here instead.
        timerJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= VideoSpec.MAX_RECORDING_MS) {
                    stopRecording()
                    break
                }
                _state.update { current ->
                    if (current is CaptureState.Recording) CaptureState.Recording(elapsed) else current
                }
                delay(TICK_MS)
            }
        }
    }

    fun stopRecording() {
        val active = recording ?: return
        timerJob?.cancel()
        timerJob = null
        recording = null
        _state.value = CaptureState.Finalising
        active.stop()
    }

    private fun onFinalize(event: VideoRecordEvent.Finalize) {
        val file = pendingFile
        pendingFile = null

        if (event.hasError()) {
            file?.delete()
            _state.value = CaptureState.Failed(
                event.cause?.message ?: "Recording failed (code ${event.error})",
            )
            return
        }

        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
        if (file == null || !file.exists() || durationMs < VideoSpec.MIN_RECORDING_MS) {
            file?.delete()
            _state.value = CaptureState.Failed("Hold the button for at least a second")
            return
        }

        _state.value = CaptureState.Captured(file, durationMs)
    }

    /**
     * Takes a video picked from the gallery or Google Photos. On success it ends up
     * in [CaptureState.Captured], exactly like a fresh recording, so the screen
     * moves on to the trim step without caring where the video came from.
     */
    fun importVideo(uri: Uri) {
        if (recording != null || _state.value is CaptureState.Importing) return
        _state.value = CaptureState.Importing

        // viewModelScope is cancelled if the user leaves the screen, which also
        // stops the copy and removes the partial file.
        viewModelScope.launch {
            _state.value = try {
                val imported = importer.import(uri)
                CaptureState.Captured(imported.file, imported.durationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryImporter.ImportException) {
                CaptureState.Failed(e.message ?: "Could not import that video.")
            } catch (e: Exception) {
                CaptureState.Failed("Could not import that video.")
            }
        }
    }

    fun reset() {
        _state.value = CaptureState.Idle
    }

    override fun onCleared() {
        timerJob?.cancel()
        recording?.stop()
        recording = null
    }

    companion object {
        private const val TICK_MS = 50L

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                CameraViewModel(app.container)
            }
        }
    }
}

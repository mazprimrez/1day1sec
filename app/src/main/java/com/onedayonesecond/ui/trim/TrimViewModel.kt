package com.onedayonesecond.ui.trim

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import com.onedayonesecond.data.Captions
import com.onedayonesecond.data.SavedClip
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.media.VideoMetadata
import com.onedayonesecond.media.VideoSpec
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.theme.MessageAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class TrimUiState(
    val sourceFile: File,
    val date: LocalDate,
    val durationMs: Long = 0,
    val startMs: Long = 0,
    /** Shown on the day page and above the date on the reel. Optional. */
    val caption: String = "",
    /** Stills from along the recording, for the filmstrip the one-second window sits on. */
    val frames: List<Bitmap> = emptyList(),
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
            // A day that is being taken again starts from the caption it already has.
            val caption = container.repository.clipFor(_state.value.date)?.caption.orEmpty()
            _state.update { it.copy(durationMs = duration, caption = caption) }
            val frames = withContext(Dispatchers.IO) { extractFrames(_state.value.sourceFile, duration) }
            _state.update { it.copy(frames = frames) }
        }
    }

    fun setStart(startMs: Long) {
        _state.update { it.copy(startMs = startMs.coerceIn(0, it.maxStartMs)) }
    }

    fun setCaption(text: String) {
        _state.update { it.copy(caption = text.take(Captions.MAX_LENGTH)) }
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
                // Leaving the screen can cancel the trimming above, but not this: stopping
                // between "old second to the trash" and "new second recorded" would leave
                // the day looking empty.
                withContext(NonCancellable) {
                    val saved = container.repository.saveClipKeepingOld(current.date, output, duration, current.caption)
                    current.sourceFile.delete()
                    announce(saved)
                }
                // After the save and off the critical path: a slow location fix must not
                // hold up the day page.
                container.appScope.launch {
                    container.journal.notePlace(current.date, container.journalActions.placeFor(current.date))
                }
                _state.update { it.copy(isProcessing = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProcessing = false, error = e.message ?: "Could not trim this clip")
                }
            }
        }
    }

    /** "Today's second is saved.", with a way to take it back and a nudge to write. */
    private fun announce(saved: SavedClip) {
        val date = saved.clip.date
        val text = if (date == LocalDate.now()) {
            "Today's second is saved."
        } else {
            "Second for ${RecapLabels.dayAndMonth(date)} is saved."
        }
        container.messages.show(
            text,
            MessageAction("Undo", run = {
                try {
                    container.repository.undoSave(saved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    container.messages.show(e.message ?: "That could not be undone.")
                }
            }),
            MessageAction("Write a few words", route = Routes.write(date)),
        )
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** A few small stills spread evenly along the recording. Empty if it cannot be read. */
    private fun extractFrames(file: File, durationMs: Long): List<Bitmap> {
        if (durationMs <= 0) return emptyList()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            (0 until FRAME_COUNT).mapNotNull { index ->
                val atUs = durationMs * 1000L * (2 * index + 1) / (2 * FRAME_COUNT)
                val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@mapNotNull null
                val scale = FRAME_HEIGHT_PX.toFloat() / frame.height
                val small = Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    FRAME_HEIGHT_PX,
                    true,
                )
                if (small !== frame) frame.recycle()
                small
            }
        } catch (e: RuntimeException) {
            emptyList() // the filmstrip falls back to a plain strip; trimming still works
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val FRAME_COUNT = 8
        private const val FRAME_HEIGHT_PX = 160

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                TrimViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

package com.onedayonesecond.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges Transformer's listener API to a suspending call.
 *
 * Transformer must be built, started and cancelled on a thread with a Looper,
 * so the whole thing is pinned to the main thread — the actual encoding happens
 * on Transformer's own internal threads, so this does not block the UI.
 */
@UnstableApi
internal suspend fun runTransformer(
    context: Context,
    onProgress: (Float) -> Unit = {},
    start: (Transformer) -> Unit,
): ExportResult = withContext(Dispatchers.Main) {
    var transformer: Transformer? = null

    val poller = launch {
        val holder = ProgressHolder()
        while (isActive) {
            delay(PROGRESS_POLL_MS)
            val active = transformer ?: continue
            if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(holder.progress / 100f)
            }
        }
    }

    try {
        suspendCancellableCoroutine { continuation ->
            val built = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            transformer = built
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { built.cancel() }
            }
            start(built)
        }
    } finally {
        poller.cancel()
    }
}

private const val PROGRESS_POLL_MS = 200L

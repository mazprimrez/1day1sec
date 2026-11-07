package com.onedayonesecond.media

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoMetadata {

    /** Duration of [file] in milliseconds, or 0 if it cannot be read. */
    suspend fun durationMs(file: File): Long = withContext(Dispatchers.IO) {
        // MediaMetadataRetriever only became AutoCloseable in API 29.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (e: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }
}

package com.onedayonesecond.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.onedayonesecond.data.ClipStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Brings a video the user picked from their gallery (or Google Photos) into the
 * same place a camera recording lands, so it goes through the normal trim step.
 *
 * The picked Uri is copied into app cache straight away. The trim screen and the
 * Transformer both work on plain files, and the access the picker grants to the
 * original is temporary, so a private copy is the simplest thing that is robust.
 * A video that lives only in the cloud is downloaded by Android while it is read,
 * which can take a moment on a slow connection.
 */
class GalleryImporter(
    private val context: Context,
    private val storage: ClipStorage,
) {

    class Imported(val file: File, val durationMs: Long)

    /** Always carries a message that is fine to show to the user as it is. */
    class ImportException(message: String) : Exception(message)

    suspend fun import(uri: Uri): Imported = withContext(Dispatchers.IO) {
        // Refuse an obviously huge file before spending time copying it.
        val declaredSize = sizeOf(uri)
        if (declaredSize != null && declaredSize > GalleryRules.MAX_BYTES) throw tooLarge()

        val target = storage.newCaptureFile()
        var succeeded = false
        try {
            copy(uri, target)

            val duration = VideoMetadata.durationMs(target)
            when {
                duration <= 0 -> throw ImportException("Could not read that video.")
                duration < VideoSpec.MIN_RECORDING_MS ->
                    throw ImportException("That video is shorter than one second.")
                duration > GalleryRules.MAX_DURATION_MS ->
                    throw ImportException("That video is longer than 5 minutes. Trim it in your gallery first.")
            }
            succeeded = true
            Imported(target, duration)
        } catch (e: IOException) {
            throw ImportException(
                "Could not read that video. If it is in Google Photos, check your connection.",
            )
        } catch (e: SecurityException) {
            throw ImportException("Android did not allow access to that video. Please pick it again.")
        } finally {
            // Covers rejection, failure and cancellation alike: never leave a
            // half-copied or unwanted file behind in the cache.
            if (!succeeded) target.delete()
        }
    }

    private suspend fun copy(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("Could not open that video.")
        input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    // The declared size can be missing, so enforce the cap while copying too.
                    if (total > GalleryRules.MAX_BYTES) throw tooLarge()
                    sink.write(buffer, 0, read)
                    // Lets the user back out of a long copy.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    private fun sizeOf(uri: Uri): Long? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // some providers do not answer; the copy loop enforces the cap anyway
    }

    private fun tooLarge() = ImportException("That video is too large to import (over 1 GB).")

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

package com.onedayonesecond.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.onedayonesecond.data.ClipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Portrait preview pictures of the app's own stored clips, for the calendar and the recaps.
 *
 * (Gallery videos are a different case, handled by [GalleryThumbnails]: those are
 * owned by the system, which already has previews for them.)
 */
class ClipThumbnails {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // A month opens 28-31 cells at once. Decoding that many videos in parallel
    // would be slower overall and spike memory, so only a few run at a time.
    private val decoders = Semaphore(MAX_PARALLEL_DECODES)

    /** Null if the clip cannot be read, which leaves the cell as a plain coloured tile. */
    suspend fun load(clip: ClipEntity): Bitmap? {
        val key = ThumbnailRules.cacheKey(clip.filePath, clip.createdAt)
        cache.get(key)?.let { return it }

        return decoders.withPermit {
            // Another cell may have finished this one while this call was waiting.
            cache.get(key)?.let { return@withPermit it }
            withContext(Dispatchers.IO) { decode(clip) }?.also { cache.put(key, it) }
        }
    }

    private fun decode(clip: ClipEntity): Bitmap? {
        if (!clip.file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(clip.filePath)
            // Prefer the middle of the clip; if the device cannot seek there, take
            // whatever frame it gives for the start.
            val frame = retriever.getFrameAtTime(
                ThumbnailRules.frameTimeUs(clip.durationMs),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: retriever.frameAtTime
            frame?.let(::toPortrait)
        } catch (e: Exception) {
            null // a broken file should leave a plain tile, not crash the calendar
        } finally {
            retriever.release()
        }
    }

    private fun toPortrait(frame: Bitmap): Bitmap {
        val crop = ThumbnailRules.centerPortrait(frame.width, frame.height)
        val cropped = Bitmap.createBitmap(frame, crop.x, crop.y, crop.width, crop.height)
        val result = if (crop.width > THUMB_WIDTH_PX) {
            Bitmap.createScaledBitmap(cropped, THUMB_WIDTH_PX, THUMB_WIDTH_PX * crop.height / crop.width, true)
        } else {
            cropped
        }
        // Only the finished thumbnail is kept: a full 720x1280 frame is 3.5 MB.
        if (frame !== result) frame.recycle()
        if (cropped !== result && cropped !== frame) cropped.recycle()
        return result
    }

    private companion object {
        /** Tiles are around 45-60dp wide, so about 150-240px; this is sharp on all of them. */
        const val THUMB_WIDTH_PX = 216
        const val CACHE_BYTES = 16 * 1024 * 1024
        const val MAX_PARALLEL_DECODES = 3
    }
}

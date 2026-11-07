package com.onedayonesecond.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small preview images for gallery videos, cached in memory while scrolling. */
class GalleryThumbnails(private val context: Context) {

    private val cache = object : LruCache<Long, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    /** Null if the system has no preview for this video. */
    suspend fun load(video: GalleryVideo): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(video.id)?.let { return@withContext it }

        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(video.uri, Size(THUMB_PX, THUMB_PX), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    video.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            }
        } catch (e: Exception) {
            null // a missing preview should leave a grey tile, not crash the list
        }
        bitmap?.also { cache.put(video.id, it) }
    }

    private companion object {
        const val THUMB_PX = 320
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}

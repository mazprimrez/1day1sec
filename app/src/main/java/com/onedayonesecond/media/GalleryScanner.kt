package com.onedayonesecond.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** One video found in the phone's gallery. */
data class GalleryVideo(
    val id: Long,
    val uri: Uri,
    val takenAtMs: Long,
    val date: LocalDate,
    val durationMs: Long,
)

/**
 * Reads the gallery index (MediaStore) to learn which days have videos.
 *
 * Only metadata is read here — ids, dates, lengths. Nothing is copied or uploaded
 * until the user picks a video and it goes through [GalleryImporter].
 *
 * This sees videos that are on the phone, which includes ones Google Photos has
 * backed up but not yet removed from the device. It cannot see videos that exist
 * only in Google Photos' cloud: Google closed its library API to other apps in
 * March 2025, so for those the picker button on the camera screen is the way in.
 */
class GalleryScanner(private val context: Context) {

    fun accessLevel(): GalleryAccessLevel = GalleryAccess.level(context)

    /** Usable videos grouped by day, oldest first within a day. Empty without access. */
    suspend fun scan(): Map<LocalDate, List<GalleryVideo>> = withContext(Dispatchers.IO) {
        if (accessLevel() == GalleryAccessLevel.NONE) return@withContext emptyMap()
        val videos = try {
            query()
        } catch (e: SecurityException) {
            emptyList() // access was withdrawn between the check and the query
        }
        videos.groupBy { it.date }.mapValues { (_, day) -> day.sortedBy { it.takenAtMs } }
    }

    private fun query(): List<GalleryVideo> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        val zone = ZoneId.systemDefault()
        val found = mutableListOf<GalleryVideo>()

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val taken = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val modified = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val added = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val durationMs = cursor.getLong(duration)
                if (!GalleryRules.isCandidate(cursor.getString(name), durationMs, cursor.getLong(size))) {
                    continue
                }
                val takenAtMs = GalleryRules.takenAtMs(
                    dateTakenMs = cursor.getLong(taken),
                    dateModifiedSec = cursor.getLong(modified),
                    dateAddedSec = cursor.getLong(added),
                ) ?: continue

                val videoId = cursor.getLong(id)
                found += GalleryVideo(
                    id = videoId,
                    uri = ContentUris.withAppendedId(collection, videoId),
                    takenAtMs = takenAtMs,
                    date = GalleryRules.dateOf(takenAtMs, zone),
                    durationMs = durationMs,
                )
            }
        }
        return found
    }
}

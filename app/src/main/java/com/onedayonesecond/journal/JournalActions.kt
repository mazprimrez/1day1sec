package com.onedayonesecond.journal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.location.PlaceFinder
import com.onedayonesecond.work.JournalWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Adding things to a day. Each one is saved first. Only then is the place looked up
 * (it can take several seconds) and, for voice notes and pages, the typing out
 * started, so an entry is safe on the phone before anything slow or networked is tried.
 */
class JournalActions(
    context: Context,
    private val journal: JournalRepository,
    private val places: PlaceFinder,
    private val processor: EntryProcessor,
    private val appScope: CoroutineScope,
) {

    private val appContext = context.applicationContext

    /**
     * The place to record for something added to [date]: where the phone is now, but
     * only for today. Writing about last Tuesday from the sofa did not happen on the sofa.
     */
    suspend fun placeFor(date: LocalDate): String? =
        if (date == LocalDate.now()) places.current() else null

    suspend fun write(date: LocalDate, text: String): Entry =
        journal.addWritten(date, text, place = null).also { placeLater(date, listOf(it)) }

    suspend fun addVoice(date: LocalDate, recording: File): Entry =
        journal.addMedia(date, EntryType.VOICE, recording, "m4a", place = null).also {
            placeLater(date, listOf(it))
            typeOut(it)
        }

    /** Fills in where [entries] were made, in the background, after they are safely saved. */
    private fun placeLater(date: LocalDate, entries: List<Entry>) {
        if (entries.isEmpty()) return
        appScope.launch { journal.attachPlace(entries, placeFor(date)) }
    }

    /**
     * Copies photos into the journal. Photos chosen together share one moment, so the
     * day page shows them as one row. A [notebook] page also has its text typed out.
     *
     * @return the entries made. Photos that cannot be read are skipped.
     */
    suspend fun addPhotos(date: LocalDate, sources: List<Uri>, notebook: Boolean): List<Entry> {
        val createdAt = System.currentTimeMillis()
        val type = if (notebook) EntryType.NOTEBOOK_PHOTO else EntryType.PHOTO
        return sources.mapNotNull { uri ->
            val scratch = try {
                importPhoto(uri)
            } catch (e: IOException) {
                null
            } ?: return@mapNotNull null
            journal.addMedia(date, type, scratch, "jpg", place = null, createdAt).also { if (notebook) typeOut(it) }
        }.also { placeLater(date, it) }
    }

    /** A page drawn with the pen. Its handwriting is typed out like a notebook photo's. */
    suspend fun addPen(date: LocalDate, page: Bitmap): Entry {
        val scratch = withContext(Dispatchers.IO) {
            journal.storage.newScratchFile("png").also { file ->
                file.outputStream().use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return journal.addMedia(date, EntryType.PEN, scratch, "png", place = null).also {
            placeLater(date, listOf(it))
            typeOut(it)
        }
    }

    /** Starts typing out now, and leaves a job behind that retries when the phone is online. */
    fun typeOut(entry: Entry) {
        JournalWork.typeOutWhenOnline(appContext)
        appScope.launch { processor.process(entry.id) }
    }

    /** After a restore: entries that came back without their words are typed out when online. */
    fun resumeTypingOut() {
        JournalWork.typeOutWhenOnline(appContext)
    }

    /** "Try now" on an entry that is waiting or failed. */
    suspend fun retry(entry: Entry) {
        journal.setProcessing(entry.id, com.onedayonesecond.data.ProcessingState.PENDING)
        JournalWork.typeOutWhenOnline(appContext)
        processor.process(entry.id)
    }

    /**
     * Reads a picked or freshly taken photo, turns it upright and shrinks it so its
     * longest side is at most [MAX_PHOTO_SIDE]: plenty to read a page from, and small
     * enough to back up in one request.
     */
    private suspend fun importPhoto(uri: Uri): File? = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        journal.storage.newScratchFile("jpg").also { file ->
            file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            upright.recycle()
        }
    }

    private companion object {
        const val MAX_PHOTO_SIDE = 2048
        const val JPEG_QUALITY = 85
    }
}

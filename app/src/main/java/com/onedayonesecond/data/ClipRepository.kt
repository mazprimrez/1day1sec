package com.onedayonesecond.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Another clip is already on that day and the caller did not ask to replace it. */
class ClipAlreadyExistsException(val date: LocalDate) :
    Exception("There is already a clip on $date.")

class ClipRepository(
    private val dao: ClipDao,
    val storage: ClipStorage,
) {

    fun observeAll(): Flow<List<ClipEntity>> = dao.observeAll()

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ClipEntity>> =
        dao.observeBetween(start, end)

    suspend fun clipsBetween(start: LocalDate, end: LocalDate): List<ClipEntity> =
        dao.getBetween(start, end).filter { it.file.exists() }

    suspend fun clipFor(date: LocalDate): ClipEntity? = dao.getByDate(date)

    suspend fun hasClipFor(date: LocalDate): Boolean = dao.existsForDate(date)

    suspend fun earliestDate(): LocalDate? = dao.earliestDate()

    /**
     * Moves a freshly trimmed file into its permanent per-day slot and records
     * it. Re-recording a day overwrites both the file and the row.
     *
     * @param caption the caption to set, or null to keep whatever the day already has,
     * so taking a day again does not silently throw away text the user typed for it.
     */
    suspend fun saveClip(
        date: LocalDate,
        trimmedFile: File,
        durationMs: Long,
        caption: String? = null,
    ): ClipEntity =
        withContext(Dispatchers.IO) {
            val kept = caption?.let(Captions::normalize) ?: dao.getByDate(date)?.caption.orEmpty()
            val destination = storage.clipFile(date)
            if (destination.exists()) destination.delete()
            if (!trimmedFile.renameTo(destination)) {
                trimmedFile.copyTo(destination, overwrite = true)
                trimmedFile.delete()
            }
            ClipEntity(
                date = date,
                filePath = destination.absolutePath,
                durationMs = durationMs,
                caption = kept,
            ).also { dao.upsert(it) }
        }

    /** Sets, changes or (with blank text) removes a day's caption. Returns what was stored. */
    suspend fun setCaption(date: LocalDate, caption: String): String {
        val stored = Captions.normalize(caption)
        dao.setCaption(date, stored)
        return stored
    }

    /**
     * Moves a clip to another day, for videos that were filed under the wrong date.
     *
     * Each day's clip is a file named after the day, so a move is: copy the file to
     * the new day's name, record the new row, and only then remove the old row and
     * file. The copy comes first so that being interrupted part-way leaves the clip
     * on both days rather than on neither.
     *
     * @param replaceExisting what to do if another clip is already on [target]: replace
     * it (true) or refuse with [ClipAlreadyExistsException] (false).
     */
    suspend fun moveClip(
        clip: ClipEntity,
        target: LocalDate,
        replaceExisting: Boolean = false,
    ): ClipEntity = withContext(Dispatchers.IO) {
        require(target != clip.date) { "The clip is already on $target" }

        val source = clip.file
        if (!source.exists()) throw IOException("The video file for ${clip.date} is missing.")
        if (!replaceExisting && dao.existsForDate(target)) throw ClipAlreadyExistsException(target)

        val destination = storage.clipFile(target)
        source.copyTo(destination, overwrite = true)

        val moved = ClipEntity(
            date = target,
            filePath = destination.absolutePath,
            durationMs = clip.durationMs,
            // When it was recorded does not change by filing it under another day,
            // and the caption belongs to the clip, so it goes with it.
            createdAt = clip.createdAt,
            caption = clip.caption,
        )
        dao.upsert(moved)
        dao.delete(clip)
        source.delete()
        moved
    }

    suspend fun deleteClip(clip: ClipEntity) = withContext(Dispatchers.IO) {
        clip.file.delete()
        dao.delete(clip)
    }
}

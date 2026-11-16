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

/** What [ClipRepository.saveClipKeepingOld] did: the new clip, and the one it replaced, if any. */
data class SavedClip(val clip: ClipEntity, val replaced: DeletedClip?)

class ClipRepository(
    private val dao: ClipDao,
    val storage: ClipStorage,
    private val trash: DeletedClipDao,
    private val now: () -> Long = System::currentTimeMillis,
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
     * it. Re-recording a day replaces both the file and the row; the second that was
     * there goes to the trash, so it can be brought back for thirty days.
     *
     * @param caption the caption to set, or null to keep whatever the day already has,
     * so taking a day again does not silently throw away text the user typed for it.
     */
    suspend fun saveClipKeepingOld(
        date: LocalDate,
        trimmedFile: File,
        durationMs: Long,
        caption: String? = null,
    ): SavedClip =
        withContext(Dispatchers.IO) {
            val existing = dao.getByDate(date)
            val kept = caption?.let(Captions::normalize) ?: existing?.caption.orEmpty()
            val replaced = existing?.takeIf { it.file.exists() }?.let { moveToTrash(it) }
            val destination = storage.clipFile(date)
            if (destination.exists()) destination.delete()
            if (!trimmedFile.renameTo(destination)) {
                trimmedFile.copyTo(destination, overwrite = true)
                trimmedFile.delete()
            }
            val clip = ClipEntity(
                date = date,
                filePath = destination.absolutePath,
                durationMs = durationMs,
                createdAt = now(),
                caption = kept,
            ).also { dao.upsert(it) }
            SavedClip(clip, replaced)
        }

    /** [saveClipKeepingOld] for callers that have no use for what was replaced. */
    suspend fun saveClip(
        date: LocalDate,
        trimmedFile: File,
        durationMs: Long,
        caption: String? = null,
    ): ClipEntity = saveClipKeepingOld(date, trimmedFile, durationMs, caption).clip

    /**
     * Takes back a save: the new second goes to the trash and the one it replaced, if
     * there was one, returns to its day.
     */
    suspend fun undoSave(saved: SavedClip) = withContext(Dispatchers.IO) {
        dao.getByDate(saved.clip.date)
            ?.takeIf { it.createdAt == saved.clip.createdAt }
            ?.let { moveToTrash(it) }
        saved.replaced?.let { old ->
            if (trash.get(old.id) != null && !dao.existsForDate(old.date)) restoreDeleted(old.id)
        }
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
     * it (true) or refuse with [ClipAlreadyExistsException] (false). A replaced clip is
     * not destroyed: it goes to the trash like a deleted one.
     */
    suspend fun moveClip(
        clip: ClipEntity,
        target: LocalDate,
        replaceExisting: Boolean = false,
    ): ClipEntity = withContext(Dispatchers.IO) {
        require(target != clip.date) { "The clip is already on $target" }

        val source = clip.file
        if (!source.exists()) throw IOException("The video file for ${clip.date} is missing.")
        val occupant = dao.getByDate(target)
        if (!replaceExisting && occupant != null) throw ClipAlreadyExistsException(target)
        // Out of the way first, into the trash, so the copy below cannot overwrite it.
        val replaced = occupant?.takeIf { it.file.exists() }?.let { moveToTrash(it) }

        val destination = storage.clipFile(target)
        try {
            source.copyTo(destination, overwrite = true)
        } catch (e: IOException) {
            // The move did not happen (a full disk, say), so nothing should have changed:
            // the second that was pushed aside goes back on its day.
            destination.delete()
            replaced?.let { runCatching { restoreDeleted(it.id) } }
            throw e
        }

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

    /**
     * Deletes a day's second without destroying it: the video moves to the trash and
     * a row there remembers when, so it can be undone now or restored later.
     */
    suspend fun deleteClip(clip: ClipEntity): DeletedClip = withContext(Dispatchers.IO) {
        moveToTrash(clip)
    }

    fun observeDeleted(): Flow<List<DeletedClip>> = trash.observeAll()

    /**
     * Puts a deleted second back on its day.
     *
     * @param replaceExisting what to do if that day has a second again by now: swap
     * them, sending the current one to the trash (true), or refuse with
     * [ClipAlreadyExistsException] (false).
     */
    suspend fun restoreDeleted(id: Long, replaceExisting: Boolean = false): ClipEntity =
        withContext(Dispatchers.IO) {
            val deleted = trash.get(id) ?: throw IOException("That second is no longer in Recently deleted.")
            if (!deleted.file.exists()) {
                trash.delete(id)
                throw IOException("The video file for ${deleted.date} is missing.")
            }
            val occupant = dao.getByDate(deleted.date)
            if (occupant != null && !replaceExisting) throw ClipAlreadyExistsException(deleted.date)
            occupant?.takeIf { it.file.exists() }?.let { moveToTrash(it) }

            val destination = storage.clipFile(deleted.date)
            if (destination.exists()) destination.delete()
            moveFile(deleted.file, destination)
            val restored = ClipEntity(
                date = deleted.date,
                filePath = destination.absolutePath,
                durationMs = deleted.durationMs,
                createdAt = deleted.createdAt,
                caption = deleted.caption,
            )
            dao.upsert(restored)
            trash.delete(id)
            restored
        }

    /** Removes one deleted second for good. This is the only step that cannot be undone. */
    suspend fun deleteForever(id: Long) = withContext(Dispatchers.IO) {
        trash.get(id)?.let { deleted ->
            deleted.file.delete()
            trash.delete(id)
        }
    }

    /**
     * Removes what has been in the trash longer than [DeletedClip.KEEP_DAYS] days, and
     * any stray file there that no row points to once it is that old.
     *
     * @return how many seconds were removed for good.
     */
    suspend fun purgeDeleted(): Int = withContext(Dispatchers.IO) {
        val cutoff = now() - DeletedClip.KEEP_MS
        val rows = trash.getAll()
        val expired = rows.filter { it.deletedAt < cutoff }
        expired.forEach { deleted ->
            deleted.file.delete()
            trash.delete(deleted.id)
        }
        val known = rows.mapTo(HashSet()) { it.filePath }
        storage.trashFiles()
            .filter { it.absolutePath !in known && it.lastModified() < cutoff }
            .forEach { it.delete() }
        expired.size
    }

    /**
     * The file moves before the rows change, so being interrupted part-way leaves a
     * video in the trash folder rather than a row pointing at nothing recoverable.
     */
    private suspend fun moveToTrash(clip: ClipEntity): DeletedClip {
        val destination = storage.newTrashFile(clip.date, now())
        if (clip.file.exists()) moveFile(clip.file, destination)
        val row = DeletedClip(
            date = clip.date,
            filePath = destination.absolutePath,
            durationMs = clip.durationMs,
            createdAt = clip.createdAt,
            caption = clip.caption,
            deletedAt = now(),
        )
        val id = trash.insert(row)
        dao.delete(clip)
        return row.copy(id = id)
    }

    private fun moveFile(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }
}

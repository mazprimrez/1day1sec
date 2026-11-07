package com.onedayonesecond.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

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
     */
    suspend fun saveClip(date: LocalDate, trimmedFile: File, durationMs: Long): ClipEntity =
        withContext(Dispatchers.IO) {
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
            ).also { dao.upsert(it) }
        }

    suspend fun deleteClip(clip: ClipEntity) = withContext(Dispatchers.IO) {
        clip.file.delete()
        dao.delete(clip)
    }
}

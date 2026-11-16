package com.onedayonesecond.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalDao {

    // Day mood and place

    @Query("SELECT * FROM day_meta WHERE date = :date")
    fun observeMeta(date: LocalDate): Flow<DayMeta?>

    @Query("SELECT * FROM day_meta WHERE date = :date")
    suspend fun meta(date: LocalDate): DayMeta?

    @Query("SELECT * FROM day_meta WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeMetaBetween(start: LocalDate, end: LocalDate): Flow<List<DayMeta>>

    @Query("SELECT * FROM day_meta WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun metaBetween(start: LocalDate, end: LocalDate): List<DayMeta>

    @Query("SELECT * FROM day_meta ORDER BY date ASC")
    suspend fun allMeta(): List<DayMeta>

    @Upsert
    suspend fun upsertMeta(meta: DayMeta)

    // Entries

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt ASC, id ASC")
    fun observeEntries(date: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC, id ASC")
    fun observeEntriesBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC, id ASC")
    suspend fun entriesBetween(start: LocalDate, end: LocalDate): List<Entry>

    @Query("SELECT * FROM entries ORDER BY date ASC, createdAt ASC, id ASC")
    suspend fun allEntries(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): Entry?

    @Query("SELECT * FROM entries WHERE processing IN ('PENDING', 'WAITING_FOR_NETWORK')")
    suspend fun unprocessedEntries(): List<Entry>

    @Query("SELECT MIN(date) FROM entries")
    suspend fun earliestEntryDate(): LocalDate?

    @Insert
    suspend fun insertEntry(entry: Entry): Long

    @Update
    suspend fun updateEntry(entry: Entry)

    @Delete
    suspend fun deleteEntry(entry: Entry)

    // Recaps

    @Query("SELECT * FROM recaps WHERE `key` = :key")
    fun observeRecap(key: String): Flow<Recap?>

    @Query("SELECT * FROM recaps WHERE `key` = :key")
    suspend fun recap(key: String): Recap?

    @Query("SELECT * FROM recaps")
    fun observeRecaps(): Flow<List<Recap>>

    @Query("SELECT * FROM recaps")
    suspend fun allRecaps(): List<Recap>

    @Upsert
    suspend fun upsertRecap(recap: Recap)
}

/** The trash: seconds that were deleted or replaced and can still be brought back. */
@Dao
interface DeletedClipDao {

    @Query("SELECT * FROM deleted_clips ORDER BY deletedAt DESC, id DESC")
    fun observeAll(): Flow<List<DeletedClip>>

    @Query("SELECT * FROM deleted_clips")
    suspend fun getAll(): List<DeletedClip>

    @Query("SELECT * FROM deleted_clips WHERE id = :id")
    suspend fun get(id: Long): DeletedClip?

    @Insert
    suspend fun insert(row: DeletedClip): Long

    @Query("DELETE FROM deleted_clips WHERE id = :id")
    suspend fun delete(id: Long)
}

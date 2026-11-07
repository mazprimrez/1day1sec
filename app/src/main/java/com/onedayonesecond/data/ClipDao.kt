package com.onedayonesecond.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ClipDao {

    @Query("SELECT * FROM clips ORDER BY date ASC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<ClipEntity>

    @Query("SELECT * FROM clips ORDER BY date ASC")
    suspend fun getAll(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE date = :date")
    suspend fun getByDate(date: LocalDate): ClipEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM clips WHERE date = :date)")
    suspend fun existsForDate(date: LocalDate): Boolean

    @Query("SELECT MIN(date) FROM clips")
    suspend fun earliestDate(): LocalDate?

    @Upsert
    suspend fun upsert(clip: ClipEntity)

    @Delete
    suspend fun delete(clip: ClipEntity)
}

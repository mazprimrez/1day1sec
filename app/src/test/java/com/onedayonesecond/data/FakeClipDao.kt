package com.onedayonesecond.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** An in-memory stand-in for the Room table, so the real file handling can run on a plain JVM. */
internal class FakeClipDao : ClipDao {
    val rows = MutableStateFlow<Map<LocalDate, ClipEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ClipEntity>> = rows.map { it.values.sortedBy(ClipEntity::date) }
    override fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ClipEntity>> =
        rows.map { all -> all.values.filter { it.date in start..end }.sortedBy(ClipEntity::date) }

    override suspend fun getBetween(start: LocalDate, end: LocalDate) =
        rows.value.values.filter { it.date in start..end }.sortedBy(ClipEntity::date)

    override suspend fun getAll() = rows.value.values.sortedBy(ClipEntity::date)
    override suspend fun getByDate(date: LocalDate) = rows.value[date]
    override suspend fun existsForDate(date: LocalDate) = date in rows.value
    override suspend fun earliestDate() = rows.value.keys.minOrNull()
    override suspend fun upsert(clip: ClipEntity) {
        rows.value = rows.value + (clip.date to clip)
    }

    override suspend fun delete(clip: ClipEntity) {
        rows.value = rows.value - clip.date
    }

    override suspend fun setCaption(date: java.time.LocalDate, caption: String) {
        rows.value[date]?.let { rows.value = rows.value + (date to it.copy(caption = caption)) }
    }
}

/** The same for the trash table. */
internal class FakeDeletedClipDao : DeletedClipDao {
    val rows = MutableStateFlow<Map<Long, DeletedClip>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<DeletedClip>> =
        rows.map { it.values.sortedByDescending(DeletedClip::deletedAt) }

    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun insert(row: DeletedClip): Long {
        val id = nextId++
        rows.value = rows.value + (id to row.copy(id = id))
        return id
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value - id
    }
}

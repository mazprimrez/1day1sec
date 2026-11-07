package com.onedayonesecond.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** An in-memory stand-in for the Room table, so the real file handling can run on a plain JVM. */
private class FakeClipDao : ClipDao {
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
}

class ClipRepositoryMoveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var dao: FakeClipDao
    private lateinit var storage: ClipStorage
    private lateinit var repository: ClipRepository

    private val wrongDay = LocalDate.of(2026, 11, 3)
    private val rightDay = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        dao = FakeClipDao()
        storage = ClipStorage(temp.newFolder("files"), temp.newFolder("cache"))
        repository = ClipRepository(dao, storage)
    }

    /** Stores a clip the way the app does, with [content] standing in for the video. */
    private fun store(date: LocalDate, content: String, durationMs: Long = 1000, createdAt: Long = 111): ClipEntity {
        val file = storage.clipFile(date)
        file.writeText(content)
        val clip = ClipEntity(date, file.absolutePath, durationMs, createdAt)
        runBlocking { dao.upsert(clip) }
        return clip
    }

    @Test
    fun `a clip moves to the new day, file and all`() = runBlocking {
        val clip = store(wrongDay, "the video", durationMs = 1234, createdAt = 42)

        val moved = repository.moveClip(clip, rightDay)

        assertEquals(rightDay, moved.date)
        assertEquals(storage.clipFile(rightDay).absolutePath, moved.filePath)
        assertEquals("the video", storage.clipFile(rightDay).readText())
        assertEquals(1234L, moved.durationMs)
        assertEquals("when it was recorded is kept", 42L, moved.createdAt)
        assertEquals(moved, dao.getByDate(rightDay))
    }

    @Test
    fun `nothing is left behind on the old day`() = runBlocking {
        val clip = store(wrongDay, "the video")

        repository.moveClip(clip, rightDay)

        assertNull(dao.getByDate(wrongDay))
        assertFalse(storage.clipFile(wrongDay).exists())
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `other days are untouched`() = runBlocking {
        val clip = store(wrongDay, "moving")
        val bystander = store(LocalDate.of(2026, 11, 5), "staying")

        repository.moveClip(clip, rightDay)

        assertEquals(bystander, dao.getByDate(bystander.date))
        assertEquals("staying", bystander.file.readText())
    }

    @Test
    fun `an occupied day is refused and everything stays as it was`() = runBlocking {
        val clip = store(wrongDay, "mine")
        val occupant = store(rightDay, "theirs")

        val error = runCatching { repository.moveClip(clip, rightDay) }.exceptionOrNull()

        assertTrue(error is ClipAlreadyExistsException)
        assertEquals(rightDay, (error as ClipAlreadyExistsException).date)
        assertEquals(clip, dao.getByDate(wrongDay))
        assertEquals(occupant, dao.getByDate(rightDay))
        assertEquals("mine", storage.clipFile(wrongDay).readText())
        assertEquals("theirs", storage.clipFile(rightDay).readText())
    }

    @Test
    fun `replacing an occupied day when asked`() = runBlocking {
        val clip = store(wrongDay, "mine")
        store(rightDay, "theirs")

        repository.moveClip(clip, rightDay, replaceExisting = true)

        assertEquals("mine", storage.clipFile(rightDay).readText())
        assertEquals(1, dao.getAll().size)
        assertNull(dao.getByDate(wrongDay))
        assertFalse(storage.clipFile(wrongDay).exists())
    }

    @Test
    fun `a missing video file fails cleanly without touching the database`() {
        val clip = store(wrongDay, "x")
        storage.clipFile(wrongDay).delete()

        val error = runCatching { runBlocking { repository.moveClip(clip, rightDay) } }.exceptionOrNull()

        assertTrue(error is IOException)
        runBlocking {
            assertNotNull("the old row is left alone", dao.getByDate(wrongDay))
            assertNull("no row appears on the new day", dao.getByDate(rightDay))
        }
        assertFalse(storage.clipFile(rightDay).exists())
    }

    @Test
    fun `moving to the same day is rejected`() {
        val clip = store(wrongDay, "x")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.moveClip(clip, wrongDay) }
        }
        assertEquals("x", storage.clipFile(wrongDay).readText())
    }

    @Test
    fun `a clip can be moved on again`() = runBlocking {
        val clip = store(wrongDay, "the video")
        val first = repository.moveClip(clip, rightDay)

        val second = repository.moveClip(first, LocalDate.of(2026, 10, 1))

        assertEquals("the video", storage.clipFile(second.date).readText())
        assertEquals(listOf(second.date), dao.getAll().map { it.date })
        assertEquals(1, temp.root.walkTopDown().filter { it.isFile && it.extension == "mp4" }.count())
    }

    @Test
    fun `the file name follows the day`() = runBlocking {
        val clip = store(wrongDay, "x")
        val moved = repository.moveClip(clip, rightDay)
        assertEquals("$rightDay.mp4", File(moved.filePath).name)
    }
}

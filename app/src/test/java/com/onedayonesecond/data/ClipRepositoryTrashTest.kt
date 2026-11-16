package com.onedayonesecond.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Deleting, replacing and restoring seconds, with the real file handling in a temporary folder. */
class ClipRepositoryTrashTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var dao: FakeClipDao
    private lateinit var trash: FakeDeletedClipDao
    private lateinit var storage: ClipStorage
    private lateinit var repository: ClipRepository
    private var now = 1_000_000L

    private val day = LocalDate.of(2026, 11, 14)
    private val other = LocalDate.of(2026, 11, 15)

    @Before
    fun setUp() {
        dao = FakeClipDao()
        trash = FakeDeletedClipDao()
        storage = ClipStorage(temp.newFolder("files"), temp.newFolder("cache"))
        repository = ClipRepository(dao, storage, trash) { now }
    }

    private fun store(date: LocalDate, content: String, caption: String = ""): ClipEntity {
        val file = storage.clipFile(date)
        file.writeText(content)
        return ClipEntity(date, file.absolutePath, 1000, 111, caption).also { runBlocking { dao.upsert(it) } }
    }

    private fun fresh(content: String): File = storage.newTrimOutputFile().also { it.writeText(content) }

    private fun daysLater(days: Long) {
        now += days * 24 * 60 * 60 * 1000
    }

    @Test
    fun `deleting a second keeps its video in the trash`() = runBlocking {
        val clip = store(day, "the video", caption = "coffee before work")

        val deleted = repository.deleteClip(clip)

        assertNull("the day is empty", dao.getByDate(day))
        assertFalse(storage.clipFile(day).exists())
        assertEquals("the video", deleted.file.readText())
        assertEquals("coffee before work", deleted.caption)
        assertEquals(now, deleted.deletedAt)
        assertEquals(listOf(deleted), repository.observeDeleted().first())
    }

    @Test
    fun `undoing a delete puts the second back as it was`() = runBlocking {
        val clip = store(day, "the video", caption = "coffee before work")
        val deleted = repository.deleteClip(clip)

        val restored = repository.restoreDeleted(deleted.id)

        assertEquals(clip, restored)
        assertEquals(clip, dao.getByDate(day))
        assertEquals("the video", storage.clipFile(day).readText())
        assertTrue("the trash is empty again", repository.observeDeleted().first().isEmpty())
        assertFalse(deleted.file.exists())
    }

    @Test
    fun `the same day can be deleted twice without the videos colliding`() = runBlocking {
        val first = repository.deleteClip(store(day, "first"))
        val second = repository.deleteClip(store(day, "second"))

        assertEquals("first", first.file.readText())
        assertEquals("second", second.file.readText())
        assertEquals(2, repository.observeDeleted().first().size)
    }

    @Test
    fun `restoring onto a day that has a second again is refused unless asked to swap`() = runBlocking {
        val deleted = repository.deleteClip(store(day, "old"))
        val newer = store(day, "new")

        val error = runCatching { repository.restoreDeleted(deleted.id) }.exceptionOrNull()

        assertTrue(error is ClipAlreadyExistsException)
        assertEquals(newer, dao.getByDate(day))
        assertEquals("new", storage.clipFile(day).readText())
        assertEquals("old", deleted.file.readText())
    }

    @Test
    fun `swapping sends the current second to the trash, not to nowhere`() = runBlocking {
        val deleted = repository.deleteClip(store(day, "old"))
        store(day, "new")

        repository.restoreDeleted(deleted.id, replaceExisting = true)

        assertEquals("old", storage.clipFile(day).readText())
        val inTrash = repository.observeDeleted().first()
        assertEquals(1, inTrash.size)
        assertEquals("new", inTrash.single().file.readText())
    }

    @Test
    fun `moving onto an occupied day sends the replaced second to the trash`() = runBlocking {
        val clip = store(day, "mine")
        store(other, "theirs", caption = "coffee before work")

        repository.moveClip(clip, other, replaceExisting = true)

        assertEquals("mine", storage.clipFile(other).readText())
        val replaced = repository.observeDeleted().first().single()
        assertEquals(other, replaced.date)
        assertEquals("theirs", replaced.file.readText())
        assertEquals("coffee before work", replaced.caption)
    }

    @Test
    fun `taking a day again keeps the earlier second in the trash`() = runBlocking {
        store(day, "first take", caption = "kept caption")

        val saved = repository.saveClipKeepingOld(day, fresh("second take"), 1000)

        assertEquals("second take", storage.clipFile(day).readText())
        assertEquals("kept caption", saved.clip.caption)
        assertNotNull(saved.replaced)
        assertEquals("first take", saved.replaced!!.file.readText())
    }

    @Test
    fun `undoing a save brings the earlier second back`() = runBlocking {
        val first = store(day, "first take", caption = "kept caption")
        val saved = repository.saveClipKeepingOld(day, fresh("second take"), 1000)

        repository.undoSave(saved)

        assertEquals(first, dao.getByDate(day))
        assertEquals("first take", storage.clipFile(day).readText())
        // The take that was undone is not destroyed either.
        assertEquals("second take", repository.observeDeleted().first().single().file.readText())
    }

    @Test
    fun `undoing the save of a first second leaves the day empty`() = runBlocking {
        val saved = repository.saveClipKeepingOld(day, fresh("only take"), 1000)

        repository.undoSave(saved)

        assertNull(dao.getByDate(day))
        assertEquals("only take", repository.observeDeleted().first().single().file.readText())
    }

    @Test
    fun `seconds leave the trash for good after thirty days, not before`() = runBlocking {
        val old = repository.deleteClip(store(day, "old"))
        daysLater(20)
        val recent = repository.deleteClip(store(other, "recent"))

        daysLater(9) // 29 days after the first delete
        assertEquals(0, repository.purgeDeleted())
        assertTrue(old.file.exists())

        daysLater(2) // 31 days after the first, 11 after the second
        assertEquals(1, repository.purgeDeleted())
        assertFalse(old.file.exists())
        assertTrue(recent.file.exists())
        assertEquals(listOf(recent), repository.observeDeleted().first())
    }

    @Test
    fun `removing for good deletes the video and the row`() = runBlocking {
        val deleted = repository.deleteClip(store(day, "gone"))

        repository.deleteForever(deleted.id)

        assertFalse(deleted.file.exists())
        assertTrue(repository.observeDeleted().first().isEmpty())
        assertTrue(runCatching { repository.restoreDeleted(deleted.id) }.exceptionOrNull() is IOException)
    }
}

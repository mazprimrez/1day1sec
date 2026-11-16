package com.onedayonesecond.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class ClipRepositoryCaptionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var dao: FakeClipDao
    private lateinit var storage: ClipStorage
    private lateinit var repository: ClipRepository

    private val day = LocalDate.of(2026, 11, 3)
    private val other = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        dao = FakeClipDao()
        storage = ClipStorage(temp.newFolder("files"), temp.newFolder("cache"))
        repository = ClipRepository(dao, storage, FakeDeletedClipDao())
    }

    private fun store(date: LocalDate, caption: String = "", content: String = "video"): ClipEntity {
        val file = storage.clipFile(date)
        file.writeText(content)
        return ClipEntity(date, file.absolutePath, 1000, 111, caption).also { runBlocking { dao.upsert(it) } }
    }

    /** A freshly trimmed file, as the trim step hands it over. */
    private fun fresh(content: String = "new video"): File =
        storage.newTrimOutputFile().also { it.writeText(content) }

    @Test
    fun `a clip starts without a caption`() = runBlocking {
        val saved = repository.saveClip(day, fresh(), 1000)
        assertEquals("", saved.caption)
    }

    @Test
    fun `a caption can be set and is stored tidied`() = runBlocking {
        store(day)
        val stored = repository.setCaption(day, "  Lunch \n with   Sam ")
        assertEquals("Lunch with Sam", stored)
        assertEquals("Lunch with Sam", dao.getByDate(day)?.caption)
    }

    @Test
    fun `a caption can be changed`() = runBlocking {
        store(day, caption = "old")
        repository.setCaption(day, "new")
        assertEquals("new", dao.getByDate(day)?.caption)
    }

    @Test
    fun `a blank caption removes it`() = runBlocking {
        store(day, caption = "something")
        assertEquals("", repository.setCaption(day, "   "))
        assertEquals("", dao.getByDate(day)?.caption)
    }

    @Test
    fun `a caption is cut to the limit`() = runBlocking {
        store(day)
        val stored = repository.setCaption(day, "x".repeat(500))
        assertEquals(Captions.MAX_LENGTH, stored.length)
    }

    @Test
    fun `a caption on a day with no clip changes nothing`() = runBlocking {
        repository.setCaption(day, "orphan")
        assertNull(dao.getByDate(day))
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `other days keep their captions`() = runBlocking {
        store(day, caption = "mine")
        store(other, caption = "theirs")
        repository.setCaption(day, "changed")
        assertEquals("theirs", dao.getByDate(other)?.caption)
    }

    @Test
    fun `taking a day again keeps its caption`() = runBlocking {
        store(day, caption = "keep me")
        val retaken = repository.saveClip(day, fresh("second take"), 900)
        assertEquals("keep me", retaken.caption)
        assertEquals("keep me", dao.getByDate(day)?.caption)
        assertEquals("second take", storage.clipFile(day).readText())
    }

    @Test
    fun `an explicit caption replaces the old one when saving`() = runBlocking {
        store(day, caption = "old")
        val saved = repository.saveClip(day, fresh(), 900, caption = "from the backup")
        assertEquals("from the backup", saved.caption)
    }

    @Test
    fun `an explicit empty caption clears it when saving`() = runBlocking {
        store(day, caption = "old")
        assertEquals("", repository.saveClip(day, fresh(), 900, caption = "").caption)
    }

    @Test
    fun `a restored caption is tidied like a typed one`() = runBlocking {
        val saved = repository.saveClip(day, fresh(), 900, caption = "  spaced\n out ")
        assertEquals("spaced out", saved.caption)
    }

    @Test
    fun `a moved clip takes its caption with it`() = runBlocking {
        val clip = store(day, caption = "travels along")
        val moved = repository.moveClip(clip, other)
        assertEquals("travels along", moved.caption)
        assertEquals("travels along", dao.getByDate(other)?.caption)
        assertNull(dao.getByDate(day))
    }

    @Test
    fun `replacing a day also replaces its caption`() = runBlocking {
        val clip = store(day, caption = "incoming")
        store(other, caption = "outgoing")
        repository.moveClip(clip, other, replaceExisting = true)
        assertEquals("incoming", dao.getByDate(other)?.caption)
    }

    @Test
    fun `moving a clip without a caption leaves the new day without one`() = runBlocking {
        val clip = store(day)
        store(other, caption = "outgoing")
        repository.moveClip(clip, other, replaceExisting = true)
        assertEquals("", dao.getByDate(other)?.caption)
    }
}

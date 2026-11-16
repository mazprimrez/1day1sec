package com.onedayonesecond.backup

import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class JournalFileTest {

    private val date = LocalDate.of(2026, 11, 16)
    private val written = EntryBackup(1000, EntryType.WRITTEN, "Coffee before work.", null, "Kemang, Jakarta Selatan")
    private val voice = EntryBackup(2000, EntryType.VOICE, null, "2026-11-16-2000.m4a", null)
    private val day = DayBackup(date, Mood.GOOD, "Kemang, Jakarta Selatan", listOf(written, voice))

    @Test
    fun `a day survives being written to its file and read back`() {
        assertEquals(day, JournalFile.decodeDay(JournalFile.encodeDay(day)))
    }

    @Test
    fun `a day with nothing but entries reads back without a mood or place`() {
        val bare = DayBackup(date, null, null, listOf(voice))
        assertEquals(bare, JournalFile.decodeDay(JournalFile.encodeDay(bare)))
    }

    @Test
    fun `the same day always produces the same bytes, whatever order the entries came in`() {
        assertArrayEquals(
            JournalFile.encodeDay(day),
            JournalFile.encodeDay(day.copy(entries = listOf(voice, written))),
        )
    }

    @Test
    fun `a damaged day file reads as nothing and a bad entry is skipped`() {
        assertNull(JournalFile.decodeDay("not json".toByteArray()))
        assertNull(JournalFile.decodeDay("""{"mood":"GOOD"}""".toByteArray()))
        val read = JournalFile.decodeDay(
            """{"date":"2026-11-16","mood":"ECSTATIC","entries":[{"type":"HOLOGRAM","createdAt":1},
               {"type":"WRITTEN"},{"type":"WRITTEN","createdAt":5,"text":"kept"}]}""".toByteArray(),
        )
        assertEquals(DayBackup(date, null, null, listOf(EntryBackup(5, EntryType.WRITTEN, "kept", null, null))), read)
    }

    @Test
    fun `a recap survives being written to its file and read back`() {
        val recap = Recap(
            key = "week-2026-11-09",
            title = "A quiet week",
            quote = "Tired today.",
            quoteDate = LocalDate.of(2026, 11, 15),
            json = """{"note":"Rest counts.","tries":["Sleep"]}""",
            createdAt = 123,
        )
        val read = JournalFile.decodeRecap(JournalFile.encodeRecap(recap))!!
        assertEquals(recap.copy(json = read.json), read)
        assertEquals("Rest counts.", org.json.JSONObject(read.json).getString("note"))
        assertNull(JournalFile.decodeRecap("{}".toByteArray()))
    }

    // Merging with what is already in Drive

    @Test
    fun `with nothing in Drive the phone's day is what goes up`() {
        assertEquals(day, JournalSync.mergeDay(null, day))
    }

    @Test
    fun `a new phone with only a mood does not wipe the day's entries from Drive`() {
        val onNewPhone = DayBackup(date, Mood.BAD, null, emptyList())
        val merged = JournalSync.mergeDay(remote = day, local = onNewPhone)
        assertEquals(Mood.BAD, merged.mood)
        assertEquals("the place Drive had is kept", "Kemang, Jakarta Selatan", merged.place)
        assertEquals(setOf(written, voice), merged.entries.toSet())
    }

    @Test
    fun `an entry edited on the phone replaces Drive's copy of it`() {
        val edited = written.copy(text = "Coffee before work, and a walk.")
        val merged = JournalSync.mergeDay(remote = day, local = day.copy(entries = listOf(edited, voice)))
        assertEquals(setOf(edited, voice), merged.entries.toSet())
    }

    @Test
    fun `a voice note that has been typed out since the last backup is updated, not doubled`() {
        val typedOut = voice.copy(text = "Note to self.")
        val merged = JournalSync.mergeDay(remote = day, local = day.copy(entries = listOf(written, typedOut)))
        assertEquals(2, merged.entries.size)
        assertTrue(typedOut in merged.entries)
    }

    // Names in Drive

    @Test
    fun `journal files are named by day and recaps by key`() {
        assertEquals("2026-11-16.json", DriveLayout.dayFileName(date))
        assertEquals(date, DriveLayout.dateOfDayFile("2026-11-16.json"))
        assertNull(DriveLayout.dateOfDayFile("captions.json"))
        assertNull(DriveLayout.dateOfDayFile("recap-week-2026-11-09.json"))
        assertEquals("recap-week-2026-11-09.json", DriveLayout.recapFileName("week-2026-11-09"))
        assertEquals("week-2026-11-09", DriveLayout.recapKeyOf("recap-week-2026-11-09.json"))
        assertNull(DriveLayout.recapKeyOf("2026-11-16.json"))
        assertNull("a clip is not a day file", DriveLayout.dateOfDayFile("2026-11-16.mp4"))
        assertNull("a day file is not a clip", DriveLayout.dateOfClip("2026-11-16.json"))
        assertNull("entry media is not a clip", DriveLayout.dateOfClip("2026-11-16-2000.m4a"))
    }

    @Test
    fun `week and month recaps are filed in a month folder, the year's beside them`() {
        assertEquals(YearMonth.of(2026, 11), DriveLayout.recapFolder("week-2026-11-09"))
        assertEquals("a week is filed where it is listed", YearMonth.of(2026, 10), DriveLayout.recapFolder("week-2026-09-28"))
        assertEquals(YearMonth.of(2026, 10), DriveLayout.recapFolder("month-2026-10"))
        assertNull(DriveLayout.recapFolder("year-2026"))
    }
}

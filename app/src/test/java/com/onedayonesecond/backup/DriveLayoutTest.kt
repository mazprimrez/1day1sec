package com.onedayonesecond.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DriveLayoutTest {

    @Test
    fun `each day goes in a folder named for its year and month`() {
        assertEquals("2026-11", DriveLayout.monthFolderName(LocalDate.of(2026, 11, 7)))
        assertEquals("2026-01", DriveLayout.monthFolderName(LocalDate.of(2026, 1, 31)))
        assertEquals("2024-12", DriveLayout.monthFolderName(LocalDate.of(2024, 12, 1)))
    }

    @Test
    fun `month folders sort in time order by name`() {
        val names = listOf(
            LocalDate.of(2026, 11, 7), LocalDate.of(2025, 2, 1),
            LocalDate.of(2026, 2, 9), LocalDate.of(2025, 12, 30),
        ).map(DriveLayout::monthFolderName)
        assertEquals(listOf("2025-02", "2025-12", "2026-02", "2026-11"), names.sorted())
    }

    @Test
    fun `a month folder name reads back to its month`() {
        assertEquals(YearMonth.of(2026, 11), DriveLayout.parseMonthFolder("2026-11"))
    }

    @Test
    fun `names that are not month folders are not mistaken for one`() {
        assertNull(DriveLayout.parseMonthFolder("Holiday photos"))
        assertNull(DriveLayout.parseMonthFolder("2026-13"))
        assertNull(DriveLayout.parseMonthFolder("2026-1"))
        assertNull(DriveLayout.parseMonthFolder("2026-11-07"))
        assertNull(DriveLayout.parseMonthFolder(""))
    }

    @Test
    fun `clip names are the date plus mp4`() {
        assertEquals("2026-11-07.mp4", DriveLayout.clipFileName(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun `a clip file name reads back to its day`() {
        assertEquals(LocalDate.of(2026, 11, 7), DriveLayout.dateOfClip("2026-11-07.mp4"))
        val date = LocalDate.of(2024, 2, 29)
        assertEquals(date, DriveLayout.dateOfClip(DriveLayout.clipFileName(date)))
    }

    @Test
    fun `other file names are not clips`() {
        assertNull(DriveLayout.dateOfClip("notes.mp4"))
        assertNull(DriveLayout.dateOfClip("2026-13-40.mp4"))
        assertNull(DriveLayout.dateOfClip("2026-11-07.copy.mp4"))
        assertNull(DriveLayout.dateOfClip("2026-11-07.mov"))
        assertNull(DriveLayout.dateOfClip("2026-11-07"))
        assertNull(DriveLayout.dateOfClip("2026-11-07.1d1s"))
        assertNull(DriveLayout.dateOfClip(DriveLayout.CAPTIONS_FILE))
        assertNull(DriveLayout.dateOfEncryptedClip(DriveLayout.CAPTIONS_FILE))
    }

    @Test
    fun `old encrypted clips are recognised separately`() {
        assertEquals(LocalDate.of(2026, 11, 7), DriveLayout.dateOfEncryptedClip("2026-11-07.1d1s"))
        assertNull(DriveLayout.dateOfEncryptedClip("2026-11-07.mp4"))
        assertNull(DriveLayout.dateOfEncryptedClip("1day1sec.keyinfo"))
    }

    /** A stand-in Drive: top-level folders by name, and a record of what was renamed. */
    private class FakeDrive(vararg folders: Pair<String, String>, val renameFails: Boolean = false) {
        val folders = folders.toMap().toMutableMap()
        val renames = mutableListOf<Pair<String, String>>()

        fun root(): String? = DriveLayout.findRoot(
            find = { folders[it] },
            rename = { id, name ->
                if (renameFails) throw java.io.IOException("Drive is not answering")
                renames += id to name
            },
        )
    }

    @Test
    fun `the backup folder is found under the app's name`() {
        val drive = FakeDrive("Kilasan" to "new-id")

        assertEquals("new-id", drive.root())
        assertEquals(emptyList<Pair<String, String>>(), drive.renames)
    }

    @Test
    fun `a backup made under the former name is renamed, not started again`() {
        val drive = FakeDrive("1day1sec" to "old-id")

        assertEquals("old-id", drive.root())
        assertEquals(listOf("old-id" to "Kilasan"), drive.renames)
    }

    @Test
    fun `a backup under the former name is still used when it cannot be renamed`() {
        val drive = FakeDrive("1day1sec" to "old-id", renameFails = true)

        assertEquals("old-id", drive.root())
    }

    @Test
    fun `with both folders the one under the app's name is used and the other is left alone`() {
        val drive = FakeDrive("Kilasan" to "new-id", "1day1sec" to "old-id")

        assertEquals("new-id", drive.root())
        assertEquals(emptyList<Pair<String, String>>(), drive.renames)
    }

    @Test
    fun `a Drive with neither folder has no backup`() {
        assertNull(FakeDrive().root())
    }
}

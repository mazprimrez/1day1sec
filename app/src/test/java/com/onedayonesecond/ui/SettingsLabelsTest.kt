package com.onedayonesecond.ui

import com.onedayonesecond.media.DateAlign
import com.onedayonesecond.media.DateEdge
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.ui.backup.BackupLabels
import com.onedayonesecond.ui.backup.BackupPhase
import com.onedayonesecond.ui.backup.BackupUiState
import com.onedayonesecond.ui.compile.CompileUiState
import com.onedayonesecond.ui.compile.RangeOption
import com.onedayonesecond.work.Reminders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The lines Settings shows about the reel, the backup and the reminder, and the rules behind them. */
class SettingsLabelsTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val now = LocalDateTime.of(2026, 11, 18, 14, 30)

    private fun millis(time: LocalDateTime): Long = time.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a reel is the year, thirty days or a week`() {
        assertEquals(listOf("Year", "30 days", "Week"), RangeOption.entries.map { it.label })
    }

    @Test
    fun `a saved range is found by its name`() {
        assertEquals(RangeOption.LAST_7_DAYS, RangeOption.named("LAST_7_DAYS"))
        assertEquals(RangeOption.LAST_30_DAYS, RangeOption.named("LAST_30_DAYS"))
    }

    @Test
    fun `nothing saved, or a range that no longer exists, means the year`() {
        assertEquals(RangeOption.THIS_YEAR, RangeOption.named(null))
        // "All" was a choice once; a phone that saved it gets the year instead.
        assertEquals(RangeOption.THIS_YEAR, RangeOption.named("ALL_TIME"))
    }

    @Test
    fun `the text on a second is summed up in a line`() {
        assertEquals("Date, caption and place, bottom left", CompileUiState().textSummary)
        assertEquals(
            "Date and caption, top right",
            CompileUiState(
                showPlaces = false,
                dateStyle = DateLabelStyle(align = DateAlign.RIGHT, edge = DateEdge.TOP),
            ).textSummary,
        )
        assertEquals(
            "Place, bottom center",
            CompileUiState(
                showDates = false,
                showCaptions = false,
                dateStyle = DateLabelStyle(align = DateAlign.CENTER),
            ).textSummary,
        )
    }

    @Test
    fun `with every line switched off there is no text`() {
        assertEquals(
            "No text",
            CompileUiState(showDates = false, showCaptions = false, showPlaces = false).textSummary,
        )
    }

    @Test
    fun `a backup from today says the time`() {
        val at = millis(LocalDateTime.of(2026, 11, 18, 9, 12))
        assertEquals("Backed up today at 09:12", BackupLabels.lastBackup(at, now, zone))
    }

    @Test
    fun `a backup from yesterday says so`() {
        val at = millis(LocalDateTime.of(2026, 11, 17, 21, 40))
        assertEquals("Backed up yesterday at 21:40", BackupLabels.lastBackup(at, now, zone))
    }

    @Test
    fun `an older backup says the day, and the year only when it is another one`() {
        assertEquals(
            "Backed up on 14 November",
            BackupLabels.lastBackup(millis(LocalDateTime.of(2026, 11, 14, 8, 0)), now, zone),
        )
        assertEquals(
            "Backed up on 30 December 2025",
            BackupLabels.lastBackup(millis(LocalDateTime.of(2025, 12, 30, 8, 0)), now, zone),
        )
    }

    @Test
    fun `with no backup time on record it says what the backup is`() {
        assertEquals("Keeps a copy in your own Google Drive", BackupLabels.lastBackup(null, now, zone))
    }

    @Test
    fun `a backup that is running is described before the last one`() {
        assertEquals("Connecting to Google…", BackupLabels.status(BackupUiState(phase = BackupPhase.AUTHORIZING)))
        assertEquals(
            "Working, 12 of 40",
            BackupLabels.status(BackupUiState(phase = BackupPhase.WORKING, done = 12, total = 40)),
        )
        assertEquals(
            "Looking at what is in Drive…",
            BackupLabels.status(BackupUiState(phase = BackupPhase.WORKING)),
        )
    }

    @Test
    fun `a run that failed is named, whatever came before it`() {
        assertEquals(
            "The last run didn't finish",
            BackupLabels.status(BackupUiState(error = "No connection", lastBackupAt = 1L)),
        )
    }

    @Test
    fun `the reminder waits for its time later today`() {
        assertEquals(Duration.ofMinutes(330), Reminders.delayUntil(LocalTime.of(20, 0), now))
    }

    @Test
    fun `a time already past today comes tomorrow`() {
        assertEquals(Duration.ofHours(18), Reminders.delayUntil(LocalTime.of(8, 30), now))
    }

    @Test
    fun `at the very minute, it is tomorrow's turn`() {
        assertEquals(Duration.ofDays(1), Reminders.delayUntil(LocalTime.of(14, 30), now))
    }
}

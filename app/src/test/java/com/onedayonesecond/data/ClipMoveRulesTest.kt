package com.onedayonesecond.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone

class ClipMoveRulesTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val from = LocalDate.of(2026, 11, 3)

    private fun check(to: LocalDate, targetHasClip: Boolean = false) =
        ClipMoveRules.check(from, to, today, targetHasClip)

    @Test
    fun `an empty past day is fine`() {
        assertEquals(ClipMoveRules.Check.OK, check(LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `today is fine`() {
        assertEquals(ClipMoveRules.Check.OK, check(today))
    }

    @Test
    fun `moving to the same day is nothing to do`() {
        assertEquals(ClipMoveRules.Check.SAME_DAY, check(from))
    }

    @Test
    fun `tomorrow is refused`() {
        assertEquals(ClipMoveRules.Check.FUTURE, check(today.plusDays(1)))
        assertEquals(ClipMoveRules.Check.FUTURE, check(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `a day that already has a clip needs confirmation`() {
        assertEquals(ClipMoveRules.Check.NEEDS_REPLACE, check(LocalDate.of(2026, 10, 15), targetHasClip = true))
    }

    @Test
    fun `the same day wins over an occupied target`() {
        // Cannot happen in practice (the source is the clip on that day), but the
        // answer should still be "nothing to do", never "replace itself".
        assertEquals(ClipMoveRules.Check.SAME_DAY, check(from, targetHasClip = true))
    }

    @Test
    fun `the future wins over an occupied target`() {
        assertEquals(ClipMoveRules.Check.FUTURE, check(today.plusDays(2), targetHasClip = true))
    }

    @Test
    fun `picker millis read back as the same day`() {
        val days = listOf(
            LocalDate.of(2026, 11, 7), LocalDate.of(2024, 2, 29), LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31), LocalDate.of(1999, 12, 31),
        )
        for (day in days) {
            assertEquals(day, ClipMoveRules.fromPickerMillis(ClipMoveRules.toPickerMillis(day)))
        }
    }

    @Test
    fun `picker millis are midnight UTC`() {
        val millis = ClipMoveRules.toPickerMillis(LocalDate.of(2026, 11, 7))
        assertEquals(0L, millis % 86_400_000L)
        // The picker hands back exactly this for a tapped day, and it must be that day.
        assertEquals(LocalDate.of(2026, 11, 7), ClipMoveRules.fromPickerMillis(millis))
    }

    @Test
    fun `the day does not shift with the phone's time zone`() {
        // West of Greenwich, reading UTC midnight in the local zone gives the day
        // before; east of it, the right day. The conversion must not care.
        val original = TimeZone.getDefault()
        try {
            val picked = ClipMoveRules.toPickerMillis(LocalDate.of(2026, 11, 7))
            for (id in listOf("UTC", "America/Los_Angeles", "Pacific/Honolulu", "Asia/Makassar", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(id, LocalDate.of(2026, 11, 7), ClipMoveRules.fromPickerMillis(picked))
                assertEquals(id, picked, ClipMoveRules.toPickerMillis(LocalDate.of(2026, 11, 7)))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `days after today are not selectable, today and earlier are`() {
        fun selectable(day: LocalDate) = ClipMoveRules.isSelectable(ClipMoveRules.toPickerMillis(day), today)
        assertTrue(selectable(today))
        assertTrue(selectable(today.minusDays(1)))
        assertTrue(selectable(LocalDate.of(2020, 1, 1)))
        assertFalse(selectable(today.plusDays(1)))
        assertFalse(selectable(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `a typed day is read in the forms people write`() {
        val expected = LocalDate.of(2026, 11, 15)
        for (typed in listOf("15 Nov 2026", "15 November 2026", "15 nov 2026", " 15  Nov  2026 ", "15/11/2026", "15-11-2026", "2026-11-15")) {
            assertEquals(typed, expected, ClipMoveRules.parseDay(typed, today, Locale.ENGLISH))
        }
    }

    @Test
    fun `a day typed without a year is the most recent such day`() {
        // "today" in these tests is 7 November 2026.
        assertEquals(LocalDate.of(2026, 11, 3), ClipMoveRules.parseDay("3 Nov", today, Locale.ENGLISH))
        assertEquals(LocalDate.of(2026, 11, 7), ClipMoveRules.parseDay("7 November", today, Locale.ENGLISH))
        // 20 December has not happened yet this year, so last year's is meant.
        assertEquals(LocalDate.of(2025, 12, 20), ClipMoveRules.parseDay("20 Dec", today, Locale.ENGLISH))
    }

    @Test
    fun `a typed day in the future is still read, so the dialog can say why it cannot be chosen`() {
        val typed = ClipMoveRules.parseDay("1 Jan 2027", today, Locale.ENGLISH)
        assertEquals(LocalDate.of(2027, 1, 1), typed)
        assertEquals(ClipMoveRules.Check.FUTURE, ClipMoveRules.check(today, typed!!, today, targetHasClip = false))
    }

    @Test
    fun `text that is not a day reads as nothing`() {
        for (typed in listOf("", "   ", "soon", "32 Nov 2026", "15 Smarch 2026", "Nov")) {
            assertNull(typed, ClipMoveRules.parseDay(typed, today, Locale.ENGLISH))
        }
    }
}

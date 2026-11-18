package com.onedayonesecond.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RecapPeriodTest {

    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `a week runs Monday to Sunday and is named by its Monday`() {
        val week = RecapPeriod.weekOf(LocalDate.of(2026, 11, 14)) // a Saturday
        assertEquals(RecapPeriod.Week(monday), week)
        assertEquals(LocalDate.of(2026, 11, 15), week.end)
        assertEquals("week-2026-11-09", week.key)
        assertEquals(week, RecapPeriod.weekOf(monday))
        assertEquals(week, RecapPeriod.weekOf(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `a week cannot start on another day`() {
        assertThrows(IllegalArgumentException::class.java) { RecapPeriod.Week(LocalDate.of(2026, 11, 10)) }
    }

    @Test
    fun `month and year keys`() {
        assertEquals("month-2026-10", RecapPeriod.Month(YearMonth.of(2026, 10)).key)
        assertEquals("year-2026", RecapPeriod.Year(2026).key)
        assertEquals(LocalDate.of(2026, 10, 31), RecapPeriod.Month(YearMonth.of(2026, 10)).end)
        assertEquals(LocalDate.of(2026, 12, 31), RecapPeriod.Year(2026).end)
    }

    @Test
    fun `keys read back to the period they came from`() {
        val periods = listOf(RecapPeriod.Week(monday), RecapPeriod.Month(YearMonth.of(2026, 10)), RecapPeriod.Year(2026))
        for (period in periods) assertEquals(period, RecapPeriod.parse(period.key))
    }

    @Test
    fun `anything else is not a key`() {
        for (key in listOf("", "week-", "week-2026-11-10", "month-2026-13", "year-soon", "day-2026-11-09")) {
            assertNull(key, RecapPeriod.parse(key))
        }
    }

    @Test
    fun `previous and next step by one period`() {
        assertEquals(RecapPeriod.Week(LocalDate.of(2026, 11, 2)), RecapPeriod.Week(monday).previous())
        assertEquals(RecapPeriod.Month(YearMonth.of(2025, 12)), RecapPeriod.Month(YearMonth.of(2026, 1)).previous())
        assertEquals(RecapPeriod.Year(2027), RecapPeriod.Year(2026).next())
    }

    @Test
    fun `November 2026 lists four weeks, the ones with their Thursday in it`() {
        val weeks = RecapPeriod.weeksOf(YearMonth.of(2026, 11))
        assertEquals(
            listOf("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23"),
            weeks.map { it.monday.toString() },
        )
    }

    @Test
    fun `every week of a year is listed under exactly one month`() {
        val listed = (1..12).flatMap { RecapPeriod.weeksOf(YearMonth.of(2026, it)) }
        assertEquals(listed.size, listed.toSet().size)
        var week = RecapPeriod.weekOf(LocalDate.of(2026, 1, 8))
        while (week.month.year == 2026) {
            assertTrue("$week is listed", week in listed)
            week = week.next()
        }
        for (month in 1..12) assertTrue(RecapPeriod.weeksOf(YearMonth.of(2026, month)).size in 4..5)
    }

    @Test
    fun `a week across two months says both`() {
        assertEquals("9 to 15 Nov", RecapLabels.short(RecapPeriod.Week(monday)))
        assertEquals("26 Oct to 1 Nov", RecapLabels.short(RecapPeriod.Week(LocalDate.of(2026, 10, 26))))
        assertEquals("9 to 15 November", RecapLabels.long(RecapPeriod.Week(monday)))
    }

    @Test
    fun `the tight form of a week uses a dash and names the first month only when it differs`() {
        assertEquals("9\u201315 Nov", RecapLabels.tight(RecapPeriod.Week(monday)))
        assertEquals("26 Oct\u20131 Nov", RecapLabels.tight(RecapPeriod.Week(LocalDate.of(2026, 10, 26))))
    }

    // When recaps are written

    @Test
    fun `the week's recap is due on Sunday night, not before`() {
        val week = RecapPeriod.Week(monday)
        assertFalse(RecapRules.isDue(week, LocalDateTime.of(2026, 11, 15, 20, 59)))
        assertTrue(RecapRules.isDue(week, LocalDateTime.of(2026, 11, 15, 21, 0)))
        assertTrue(RecapRules.isDue(week, LocalDateTime.of(2026, 11, 16, 8, 0)))
    }

    @Test
    fun `the month's recap is due on the 1st of the next month`() {
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        assertFalse(RecapRules.isDue(october, LocalDateTime.of(2026, 10, 31, 23, 59)))
        assertTrue(RecapRules.isDue(october, LocalDateTime.of(2026, 11, 1, 0, 0)))
        assertEquals(LocalDate.of(2026, 12, 1), RecapRules.arrives(RecapPeriod.Month(YearMonth.of(2026, 11))))
    }

    @Test
    fun `the year's recap is due on the night of 31 December`() {
        val year = RecapPeriod.Year(2026)
        assertFalse(RecapRules.isDue(year, LocalDateTime.of(2026, 12, 31, 12, 0)))
        assertTrue(RecapRules.isDue(year, LocalDateTime.of(2026, 12, 31, 21, 0)))
    }

    @Test
    fun `on a Monday morning the newest due recaps are last week, last month, last year`() {
        val due = RecapRules.latestDue(LocalDateTime.of(2026, 11, 16, 9, 0))
        assertEquals(
            listOf("week-2026-11-09", "month-2026-10", "year-2025"),
            due.map { it.key },
        )
    }

    @Test
    fun `on Sunday night the week just ending is due`() {
        assertEquals("week-2026-11-09", RecapRules.latestDue(LocalDateTime.of(2026, 11, 15, 21, 5)).first().key)
        assertEquals("week-2026-11-02", RecapRules.latestDue(LocalDateTime.of(2026, 11, 15, 12, 0)).first().key)
    }

    @Test
    fun `on the night of 31 December the year just ending is due`() {
        assertEquals("year-2026", RecapRules.latestDue(LocalDateTime.of(2026, 12, 31, 21, 5)).last().key)
    }

    @Test
    fun `a recap needs three days of writing`() {
        assertFalse(RecapRules.hasEnough(0))
        assertFalse(RecapRules.hasEnough(2))
        assertTrue(RecapRules.hasEnough(3))
        assertTrue(RecapRules.hasEnough(7))
    }
}

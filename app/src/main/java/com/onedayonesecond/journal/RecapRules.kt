package com.onedayonesecond.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month

/**
 * When recaps are written and whether there is enough to write one from. Pure, so it
 * can be unit-tested on a plain JVM.
 */
object RecapRules {

    /** A recap of fewer days than this would be the model guessing, so none is written. */
    const val MIN_DAYS_WITH_ENTRIES = 3

    /** "Sunday night" and the evening of 31 December start here. */
    val NIGHT: LocalTime = LocalTime.of(21, 0)

    fun hasEnough(daysWithEntries: Int): Boolean = daysWithEntries >= MIN_DAYS_WITH_ENTRIES

    /**
     * True once [period]'s recap may be written: the week's on Sunday night, the
     * month's from the 1st of the next month, the year's on the night of 31 December.
     */
    fun isDue(period: RecapPeriod, now: LocalDateTime): Boolean = !now.isBefore(dueAt(period))

    fun dueAt(period: RecapPeriod): LocalDateTime = when (period) {
        is RecapPeriod.Week -> period.end.atTime(NIGHT)
        is RecapPeriod.Month -> period.end.plusDays(1).atStartOfDay()
        is RecapPeriod.Year -> period.end.atTime(NIGHT)
    }

    /**
     * The newest period of each kind whose recap is due at [now]. The nightly job
     * writes whichever of these is still missing, which also catches up after a night
     * the phone was off or offline. Older gaps are left for the user to fill by hand.
     */
    fun latestDue(now: LocalDateTime): List<RecapPeriod> {
        val today = now.toLocalDate()
        val thisWeek = RecapPeriod.weekOf(today)
        val week = if (isDue(thisWeek, now)) thisWeek else thisWeek.previous()
        val month = RecapPeriod.monthOf(today).previous()
        val thisYear = RecapPeriod.Year(today.year)
        val year = if (isDue(thisYear, now)) thisYear else thisYear.previous()
        return listOf(week, month, year)
    }

    /** When the next recap of the period containing [today] arrives, for "Letter arrives on…". */
    fun arrives(period: RecapPeriod): LocalDate = dueAt(period).toLocalDate()

    /** True on a Sunday or on 31 December: the nights a recap is written the same evening. */
    fun isRecapNight(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY || (date.month == Month.DECEMBER && date.dayOfMonth == 31)
}

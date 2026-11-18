package com.onedayonesecond.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A stretch of days a recap is written about: a week (Monday to Sunday), a calendar
 * month or a calendar year. Free of Android types so it can be unit-tested on a plain JVM.
 */
sealed interface RecapPeriod {

    /** The recap's name in the database and in the Drive backup, e.g. "week-2026-11-09". */
    val key: String
    val start: LocalDate
    val end: LocalDate

    /** The same kind of period just before this one. */
    fun previous(): RecapPeriod

    fun next(): RecapPeriod

    /** "week", "month" or "year", as used in sentences such as "Since last week". */
    val noun: String

    data class Week(val monday: LocalDate) : RecapPeriod {
        init {
            require(monday.dayOfWeek == DayOfWeek.MONDAY) { "$monday is not a Monday" }
        }

        override val key get() = "week-$monday"
        override val start get() = monday
        override val end: LocalDate get() = monday.plusDays(6)
        override fun previous() = Week(monday.minusWeeks(1))
        override fun next() = Week(monday.plusWeeks(1))
        override val noun get() = "week"

        /**
         * The month this week is listed under: the one its Thursday falls in, so a week
         * that straddles two months belongs to the one holding most of its days.
         */
        val month: YearMonth get() = YearMonth.from(monday.plusDays(3))
    }

    data class Month(val month: YearMonth) : RecapPeriod {
        override val key get() = "month-$month"
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.atEndOfMonth()
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
        override val noun get() = "month"
    }

    data class Year(val year: Int) : RecapPeriod {
        override val key get() = "year-$year"
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
        override val noun get() = "year"
    }

    companion object {

        fun weekOf(date: LocalDate): Week =
            Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

        fun monthOf(date: LocalDate): Month = Month(YearMonth.from(date))

        /** The weeks listed under [month], oldest first: four or five of them. */
        fun weeksOf(month: YearMonth): List<Week> {
            var week = weekOf(month.atDay(1))
            if (week.month < month) week = week.next()
            return generateSequence(week) { it.next() }.takeWhile { it.month == month }.toList()
        }

        /** Reads a key back, or null if it is not one of ours. */
        fun parse(key: String): RecapPeriod? = runCatching {
            when {
                key.startsWith("week-") -> Week(LocalDate.parse(key.removePrefix("week-")))
                key.startsWith("month-") -> Month(YearMonth.parse(key.removePrefix("month-")))
                key.startsWith("year-") -> Year(key.removePrefix("year-").toInt())
                else -> null
            }
        }.getOrNull()
    }
}

/** How periods and days are written out on screen. Kept in one place so they stay consistent. */
object RecapLabels {

    private val locale: Locale get() = Locale.getDefault()
    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale)

    /** "9 to 15 November", "26 October to 1 November", "November 2026", "2026". */
    fun long(period: RecapPeriod): String = when (period) {
        is RecapPeriod.Week -> range(period, "MMMM")
        is RecapPeriod.Month -> period.month.atDay(1).format(pattern("MMMM yyyy"))
        is RecapPeriod.Year -> period.year.toString()
    }

    /** "9 to 15 Nov", for the week chips. */
    fun short(week: RecapPeriod.Week): String = range(week, "MMM")

    /** "9–15 Nov", "26 Oct–1 Nov": the tightest form, for a row with little room. */
    fun tight(week: RecapPeriod.Week): String {
        val month = pattern("MMM")
        val endText = "${week.end.dayOfMonth} ${week.end.format(month)}"
        return if (week.start.month == week.end.month) {
            "${week.start.dayOfMonth}\u2013$endText"
        } else {
            "${week.start.dayOfMonth} ${week.start.format(month)}\u2013$endText"
        }
    }

    private fun range(week: RecapPeriod.Week, monthPattern: String): String {
        val month = pattern(monthPattern)
        val endText = "${week.end.dayOfMonth} ${week.end.format(month)}"
        return if (week.start.month == week.end.month) {
            "${week.start.dayOfMonth} to $endText"
        } else {
            "${week.start.dayOfMonth} ${week.start.format(month)} to $endText"
        }
    }

    /** Who said the quote and when: "You, on Saturday" within a week, "You, on 14 November" otherwise. */
    fun quoteSource(period: RecapPeriod, quoteDate: LocalDate): String = when (period) {
        is RecapPeriod.Week -> "You, on ${quoteDate.format(pattern("EEEE"))}"
        else -> "You, on ${dayAndMonth(quoteDate)}"
    }

    /** "14 November". */
    fun dayAndMonth(date: LocalDate): String = "${date.dayOfMonth} ${date.format(pattern("MMMM"))}"

    /** "Saturday, 14 November 2026": the full date, also what screen readers are given. */
    fun fullDate(date: LocalDate): String =
        "${date.format(pattern("EEEE"))}, ${date.dayOfMonth} ${date.format(pattern("MMMM yyyy"))}"

    /** "Monday, 16 November". */
    fun weekdayAndDay(date: LocalDate): String = "${date.format(pattern("EEEE"))}, ${dayAndMonth(date)}"

    /** "15 Nov 2026": the form the move dialog shows and accepts. */
    fun compact(date: LocalDate): String = "${date.dayOfMonth} ${date.format(pattern("MMM yyyy"))}"

    /** "15 Nov". */
    fun compactDay(date: LocalDate): String = "${date.dayOfMonth} ${date.format(pattern("MMM"))}"
}

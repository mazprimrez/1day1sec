package com.onedayonesecond.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The rules for moving a clip to another day, and the date-picker's quirks. Free of
 * Android types so they can be unit-tested on a plain JVM.
 */
object ClipMoveRules {

    enum class Check {
        /** Free to move. */
        OK,

        /** Same day it is already on, so there is nothing to do. */
        SAME_DAY,

        /** There is nothing to record in the future. */
        FUTURE,

        /** Another clip is on that day; moving there means replacing it, so ask first. */
        NEEDS_REPLACE,
    }

    fun check(from: LocalDate, to: LocalDate, today: LocalDate, targetHasClip: Boolean): Check =
        when {
            to == from -> Check.SAME_DAY
            to.isAfter(today) -> Check.FUTURE
            targetHasClip -> Check.NEEDS_REPLACE
            else -> Check.OK
        }

    // Material's date picker deals in milliseconds, and always as midnight UTC of the
    // chosen day — not midnight in the phone's time zone. Reading it back with the
    // local zone would be a day out for anyone west of Greenwich, so both directions
    // are pinned to UTC here.

    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** The picker greys out days after [today]. */
    fun isSelectable(pickerMillis: Long, today: LocalDate): Boolean =
        !fromPickerMillis(pickerMillis).isAfter(today)

    /**
     * Reads a day the user typed, such as "15 Nov 2026", "15 November", "15/11/2026" or
     * "2026-11-15". Month names are understood in English and in [locale]. Without a
     * year it means the most recent such day: this year's, or last year's if this
     * year's has not happened yet.
     *
     * @return null if it cannot be read as a day. Whether the day may be chosen is a
     * separate question, answered by [check].
     */
    fun parseDay(text: String, today: LocalDate, locale: Locale = Locale.getDefault()): LocalDate? {
        val typed = text.trim().replace(Regex("\\s+"), " ").replace(",", "")
        if (typed.isEmpty()) return null
        runCatching { return LocalDate.parse(typed) }

        for (language in listOf(locale, Locale.ENGLISH).distinct()) {
            for (pattern in WITH_YEAR) {
                parse(typed, pattern, language, defaultYear = null)?.let { return it }
            }
            for (pattern in WITHOUT_YEAR) {
                val thisYear = parse(typed, pattern, language, defaultYear = today.year) ?: continue
                return if (thisYear.isAfter(today)) {
                    parse(typed, pattern, language, defaultYear = today.year - 1) ?: thisYear
                } else {
                    thisYear
                }
            }
        }
        return null
    }

    private fun parse(text: String, pattern: String, locale: Locale, defaultYear: Int?): LocalDate? {
        val builder = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
        if (defaultYear != null) builder.parseDefaulting(ChronoField.YEAR, defaultYear.toLong())
        val formatter: DateTimeFormatter = builder.toFormatter(locale)
        return try {
            LocalDate.parse(text, formatter)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private val WITH_YEAR = listOf("d MMM uuuu", "d MMMM uuuu", "d/M/uuuu", "d-M-uuuu", "d.M.uuuu")
    private val WITHOUT_YEAR = listOf("d MMM", "d MMMM", "d/M")
}

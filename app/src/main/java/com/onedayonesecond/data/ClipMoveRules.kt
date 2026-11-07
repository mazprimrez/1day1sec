package com.onedayonesecond.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
}

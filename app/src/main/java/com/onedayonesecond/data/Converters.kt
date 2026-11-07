package com.onedayonesecond.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Dates are stored as epoch-day integers so that SQL ordering, BETWEEN range
 * queries and MIN() all behave correctly without any string parsing.
 */
class Converters {

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}

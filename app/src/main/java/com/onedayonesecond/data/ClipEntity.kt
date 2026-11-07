package com.onedayonesecond.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File
import java.time.LocalDate

/**
 * One finished, already-trimmed clip. The date is the primary key because the
 * app allows exactly one clip per day — re-recording replaces the row and the
 * file rather than accumulating history.
 */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey val date: LocalDate,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** Text drawn above the date on the reel. Empty means none. See [Captions]. */
    @ColumnInfo(defaultValue = "''")
    val caption: String = "",
) {
    val file: File get() = File(filePath)
}

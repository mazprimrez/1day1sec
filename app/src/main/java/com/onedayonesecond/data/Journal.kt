package com.onedayonesecond.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File
import java.time.LocalDate

/** How a day felt, from hardest to best. [score] is what averages are worked out from. */
enum class Mood(val score: Int, val label: String) {
    ROUGH(1, "Rough"),
    BAD(2, "Bad"),
    NEUTRAL(3, "Neutral"),
    GOOD(4, "Good"),
    GREAT(5, "Great"),
}

/** What belongs to a day as a whole rather than to its clip or to one entry. */
@Entity(tableName = "day_meta")
data class DayMeta(
    @PrimaryKey val date: LocalDate,
    val mood: Mood? = null,
    /** A short place name such as "Kemang, Jakarta Selatan". Null hides the place line. */
    val placeName: String? = null,
)

enum class EntryType { WRITTEN, VOICE, NOTEBOOK_PHOTO, PEN, PHOTO }

/**
 * Where an entry is in being turned into text. Only voice notes, notebook photos and
 * pen pages are ever anything but [DONE]: their words are typed out for the user.
 */
enum class ProcessingState {
    DONE,

    /** Waiting its turn or being worked on right now. */
    PENDING,

    /** The phone was offline. It is tried again by itself once it is back online. */
    WAITING_FOR_NETWORK,

    /** Tried and could not be done. The media itself is safe; the user can try again. */
    FAILED,
}

/** One thing added to a day: some writing, a voice note, a photo, a pen page. */
@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val createdAt: Long,
    val type: EntryType,
    /** Written text, a voice note's transcript, or text typed out from a page. */
    val text: String? = null,
    /** The audio, photo or pen image file, for every type but [EntryType.WRITTEN]. */
    val mediaPath: String? = null,
    val placeName: String? = null,
    val processing: ProcessingState = ProcessingState.DONE,
) {
    val mediaFile: File? get() = mediaPath?.let(::File)

    /** Types whose words are typed out by the AI service after they are saved. */
    val needsText: Boolean
        get() = type == EntryType.VOICE || type == EntryType.NOTEBOOK_PHOTO || type == EntryType.PEN
}

/**
 * A written recap of a week, month or year. [key] names the period, for example
 * "week-2026-11-09" (the Monday), "month-2026-10" or "year-2026".
 */
@Entity(tableName = "recaps")
data class Recap(
    @PrimaryKey val key: String,
    val title: String,
    /** One sentence taken word for word from the user's own entries. */
    val quote: String,
    val quoteDate: LocalDate,
    /** The chapters: what happened, the note, changes, things to try, places. */
    val json: String,
    val createdAt: Long,
)

/**
 * A second that was deleted or replaced. Its video sits in the trash folder until it
 * is restored or thirty days have passed, so deleting is never the point of no return.
 */
@Entity(tableName = "deleted_clips")
data class DeletedClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val caption: String,
    val deletedAt: Long,
) {
    val file: File get() = File(filePath)

    companion object {
        const val KEEP_DAYS = 30L
        const val KEEP_MS = KEEP_DAYS * 24 * 60 * 60 * 1000
    }
}

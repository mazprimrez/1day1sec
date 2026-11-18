package com.onedayonesecond.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File
import java.time.LocalDate

enum class BookStatus { READING, FINISHED }

/**
 * What a thought is made of. A [PAGE_PHOTO] is never typed out: the words on the page
 * are the book's, not the reader's.
 */
enum class ThoughtType { WRITTEN, VOICE, PAGE_PHOTO }

/** A book on the shelf: being read, or finished. */
@Entity(tableName = "books", indices = [Index(value = ["backupKey"], unique = true)])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * The book's name in the Drive backup, such as `2026-11-02-laut-bercerita-7k2q`.
     * Set once when the book is added and never changed, so editing the title does not
     * make a second book in Drive. Row ids cannot do this: they differ between phones.
     */
    val backupKey: String,
    val title: String,
    val author: String? = null,
    val isbn: String? = null,
    /** Which printing this is, for example "Gramedia, 2017". Page counts differ between editions. */
    val edition: String? = null,
    val totalPages: Int? = null,
    /** The cover, kept on the phone. Null shows the title printed on a blush cover. */
    val coverPath: String? = null,
    /** The bookmark. Logging a thought only ever moves it forward. */
    val currentPage: Int = 0,
    val status: BookStatus = BookStatus.READING,
    val startedOn: LocalDate,
    val finishedOn: LocalDate? = null,
) {
    val coverFile: File? get() = coverPath?.let(::File)
}

/** Something the reader noted on a page: written, said, or a photo of the page. */
@Entity(tableName = "book_thoughts", indices = [Index("bookId")])
data class BookThought(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val createdAt: Long,
    val type: ThoughtType,
    /** What was written, or what was said in a voice note once it is typed out. */
    val text: String? = null,
    /** The voice note or the photo of the page. */
    val mediaPath: String? = null,
    /** Also listed on the day page of the day it was logged. */
    val showInDiary: Boolean = true,
    val processing: ProcessingState = ProcessingState.DONE,
) {
    val mediaFile: File? get() = mediaPath?.let(::File)

    /** Only a voice note is typed out. */
    val needsText: Boolean get() = type == ThoughtType.VOICE

    val hasWords: Boolean get() = !text.isNullOrBlank()
}

/**
 * The "Summary and review ideas" page of a book, kept until the book's thoughts change.
 * [thoughtCount] is how many thoughts with words it was written from.
 */
@Entity(tableName = "book_summaries")
data class BookSummary(
    @PrimaryKey val bookId: Long,
    /** Title, quote and its page, summary, points and the gap. */
    val json: String,
    val thoughtCount: Int,
    val createdAt: Long,
)

/** A thought as the day page lists it: with the name of the book it belongs to. */
data class DiaryThought(
    @Embedded val thought: BookThought,
    val bookTitle: String,
)

/**
 * The tables version 4 adds, statement for statement. They have to match what Room
 * generates for the entities above, column for column (see app/schemas/.../4.json), or
 * Room refuses to open the database. A unit test compares the two.
 */
object BookSchema {
    val MIGRATION_3_4_SQL = listOf(
        "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`backupKey` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `isbn` TEXT, `edition` TEXT, " +
            "`totalPages` INTEGER, `coverPath` TEXT, `currentPage` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
            "`startedOn` INTEGER NOT NULL, `finishedOn` INTEGER)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_backupKey` ON `books` (`backupKey`)",
        "CREATE TABLE IF NOT EXISTS `book_thoughts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`bookId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`type` TEXT NOT NULL, `text` TEXT, `mediaPath` TEXT, `showInDiary` INTEGER NOT NULL, " +
            "`processing` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_book_thoughts_bookId` ON `book_thoughts` (`bookId`)",
        "CREATE TABLE IF NOT EXISTS `book_summaries` (`bookId` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
            "`thoughtCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
    )
}

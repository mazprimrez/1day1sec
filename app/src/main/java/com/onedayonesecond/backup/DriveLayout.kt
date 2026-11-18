package com.onedayonesecond.backup

import java.time.LocalDate
import java.time.YearMonth

/**
 * How the backup is laid out in Drive, and how names map to days.
 * Free of Android types so it can be unit-tested on a plain JVM.
 *
 * ```
 * Kilasan/
 *   2026-10/
 *     2026-10-31.mp4
 *   2026-11/
 *     2026-11-01.mp4
 *     2026-11-01.json                    the day's mood, place and entries
 *     2026-11-01-1793500000000.m4a       an entry's voice note, photo or pen page
 *     2026-11-02.mp4
 *     recap-week-2026-11-02.json
 *     recap-month-2026-11.json
 *   books/
 *     2026-11-02-laut-bercerita-7k2q.json                the book and its thoughts
 *     2026-11-02-laut-bercerita-7k2q.jpg                 its cover
 *     2026-11-02-laut-bercerita-7k2q-1793500000000.m4a   a thought's voice note or page photo
 *   recap-year-2026.json
 *   captions.json
 * ```
 *
 * One folder per month, named `yyyy-MM` so they sort chronologically, holding that
 * month's clips named by day, with each day's journal beside its clip. The day is
 * always read from the file name, never from the folder, so a file that ends up in
 * the wrong folder is still restored to the right day.
 *
 * Books sit in one folder of their own, each under a key made when it was added (the
 * day it was started, its title, four random letters), so two copies of the same
 * book, or a title that is later corrected, never collide.
 */
object DriveLayout {

    const val ROOT_FOLDER = "Kilasan"

    /** What the folder was called while the app was named 1day1sec. A backup found under it is renamed. */
    const val FORMER_ROOT_FOLDER = "1day1sec"

    /**
     * The id of the backup's folder, or null if this Drive has none.
     *
     * A backup made while the app was called 1day1sec sits in a folder of that name. It
     * is the same backup, so it is renamed rather than left behind and started again.
     * If the renaming fails the folder is used as it is: its name is cosmetic, what is
     * in it is not, and the next backup tries the renaming again.
     *
     * @param find the id of the top-level folder with a name, or null
     * @param rename gives the folder with an id a new name
     */
    fun findRoot(find: (name: String) -> String?, rename: (id: String, name: String) -> Unit): String? {
        find(ROOT_FOLDER)?.let { return it }
        val former = find(FORMER_ROOT_FOLDER) ?: return null
        try {
            rename(former, ROOT_FOLDER)
        } catch (e: Exception) {
            // Used under its old name; see above.
        }
        return former
    }

    /** Holds the clips' captions, which live in the app's database rather than in the videos. */
    const val CAPTIONS_FILE = "captions.json"

    private const val CLIP_EXT = ".mp4"

    /** Clips from a short-lived encrypted version of the backup. They can no longer be opened. */
    private const val ENCRYPTED_EXT = ".1d1s"

    fun monthFolderName(date: LocalDate): String = YearMonth.from(date).toString()

    /** The month a folder name stands for, or null if it is not one of ours. */
    fun parseMonthFolder(name: String): YearMonth? =
        runCatching { YearMonth.parse(name) }.getOrNull()

    fun clipFileName(date: LocalDate): String = "$date$CLIP_EXT"

    /** The day of a `<yyyy-MM-dd>.mp4` file name, or null for any other name. */
    fun dateOfClip(fileName: String): LocalDate? = dateBefore(fileName, CLIP_EXT)

    /** The day of an old `<yyyy-MM-dd>.1d1s` file name, or null for any other name. */
    fun dateOfEncryptedClip(fileName: String): LocalDate? = dateBefore(fileName, ENCRYPTED_EXT)

    private fun dateBefore(fileName: String, extension: String): LocalDate? {
        if (!fileName.endsWith(extension)) return null
        return runCatching { LocalDate.parse(fileName.removeSuffix(extension)) }.getOrNull()
    }

    private const val JSON_EXT = ".json"
    private const val RECAP_PREFIX = "recap-"

    /** A day's mood, place and entries, next to that day's clip. */
    fun dayFileName(date: LocalDate): String = "$date$JSON_EXT"

    /** The day of a `<yyyy-MM-dd>.json` file name, or null for any other name. */
    fun dateOfDayFile(fileName: String): LocalDate? = dateBefore(fileName, JSON_EXT)

    /** A recap, named by its key: `recap-week-2026-11-09.json`. */
    fun recapFileName(key: String): String = "$RECAP_PREFIX$key$JSON_EXT"

    /** The recap key of a `recap-<key>.json` file name, or null for any other name. */
    fun recapKeyOf(fileName: String): String? =
        if (fileName.startsWith(RECAP_PREFIX) && fileName.endsWith(JSON_EXT)) {
            fileName.removePrefix(RECAP_PREFIX).removeSuffix(JSON_EXT).ifEmpty { null }
        } else {
            null
        }

    /**
     * The month folder a recap is filed in: a week's under the month it is listed in,
     * a month's in its own. Null for a year's recap, which sits beside the month folders.
     */
    fun recapFolder(key: String): YearMonth? = when (val period = com.onedayonesecond.journal.RecapPeriod.parse(key)) {
        is com.onedayonesecond.journal.RecapPeriod.Week -> period.month
        is com.onedayonesecond.journal.RecapPeriod.Month -> period.month
        else -> null
    }

    /** The one folder that holds every book, beside the month folders. */
    const val BOOKS_FOLDER = "books"

    /** A book and its thoughts: `<key>.json`. */
    fun bookFileName(key: String): String = "$key$JSON_EXT"

    /** The book key of a `<key>.json` file name in the books folder, or null for any other name. */
    fun bookKeyOf(fileName: String): String? =
        if (fileName.endsWith(JSON_EXT)) fileName.removeSuffix(JSON_EXT).ifEmpty { null } else null

    /** A book's cover: `<key>.jpg`. */
    fun bookCoverName(key: String): String = "$key.jpg"

    /** A thought's voice note or page photo: `<key>-<createdAt>.<extension>`. */
    fun thoughtMediaName(key: String, createdAt: Long, extension: String): String = "$key-$createdAt.$extension"

    /**
     * True if a file in the books folder belongs to the book with this key: its file,
     * its cover, or a thought's media. A longer key that merely starts with this one
     * does not match, because what follows the key must be a time in digits.
     */
    fun isBookFile(fileName: String, key: String): Boolean {
        if (key.isEmpty() || !fileName.startsWith(key)) return false
        val rest = fileName.substring(key.length)
        return rest == JSON_EXT || rest == ".jpg" || THOUGHT_MEDIA_REST.matches(rest)
    }

    private val THOUGHT_MEDIA_REST = Regex("-\\d+(-\\d+)?\\.[A-Za-z0-9]+")

    /** What Drive is told a journal file is, from its extension. */
    fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "m4a" -> "audio/mp4"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "json" -> "application/json"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}

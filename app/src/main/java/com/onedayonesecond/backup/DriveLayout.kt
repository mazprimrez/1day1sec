package com.onedayonesecond.backup

import java.time.LocalDate
import java.time.YearMonth

/**
 * How the backup is laid out in Drive, and how names map to days.
 * Free of Android types so it can be unit-tested on a plain JVM.
 *
 * ```
 * 1day1sec/
 *   2026-10/
 *     2026-10-31.mp4
 *   2026-11/
 *     2026-11-01.mp4
 *     2026-11-01.json                    the day's mood, place and entries
 *     2026-11-01-1793500000000.m4a       an entry's voice note, photo or pen page
 *     2026-11-02.mp4
 *     recap-week-2026-11-02.json
 *     recap-month-2026-11.json
 *   recap-year-2026.json
 *   captions.json
 * ```
 *
 * One folder per month, named `yyyy-MM` so they sort chronologically, holding that
 * month's clips named by day, with each day's journal beside its clip. The day is
 * always read from the file name, never from the folder, so a file that ends up in
 * the wrong folder is still restored to the right day.
 */
object DriveLayout {

    const val ROOT_FOLDER = "1day1sec"

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

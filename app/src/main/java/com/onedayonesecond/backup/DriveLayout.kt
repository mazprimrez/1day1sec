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
 *     2026-11-02.mp4
 *   captions.json
 * ```
 *
 * One folder per month, named `yyyy-MM` so they sort chronologically, holding that
 * month's clips named by day. The day is always read from the file name, never from
 * the folder, so a file that ends up in the wrong folder is still restored to the
 * right day.
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
}

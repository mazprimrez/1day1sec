package com.onedayonesecond.data

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * Owns every path the app writes to.
 *
 * Finished clips live in filesDir (app-private, survives reboot, not visible to
 * other apps). Raw camera recordings and compiled reels live in cacheDir — both
 * are disposable, and the compiled reel only becomes permanent once the user
 * exports it to MediaStore.
 */
class ClipStorage(private val filesDir: File, private val cacheDir: File) {

    // Taking plain folders (rather than only a Context) lets the file handling be
    // unit-tested on a plain JVM with a temporary directory.
    constructor(context: Context) : this(context.filesDir, context.cacheDir)

    private val clipsDir: File
        get() = File(filesDir, "clips").apply { mkdirs() }

    private val trashDir: File
        get() = File(filesDir, "trash").apply { mkdirs() }

    private val journalDir: File
        get() = File(filesDir, "journal").apply { mkdirs() }

    private val booksDir: File
        get() = File(filesDir, "books").apply { mkdirs() }

    private val capturesDir: File
        get() = File(cacheDir, "captures").apply { mkdirs() }

    private val reelsDir: File
        get() = File(cacheDir, "reels").apply { mkdirs() }

    /** Stable, permanent location for a given day's trimmed second. */
    fun clipFile(date: LocalDate): File = File(clipsDir, "$date.mp4")

    /**
     * Where a deleted or replaced second waits until it is restored or purged. The
     * time is part of the name because the same day can be deleted more than once.
     */
    fun newTrashFile(date: LocalDate, now: Long = System.currentTimeMillis()): File {
        var file = File(trashDir, "$date-$now.mp4")
        var n = 1
        while (file.exists()) file = File(trashDir, "$date-$now-${n++}.mp4")
        return file
    }

    /** Every file in the trash folder, including any that lost their database row. */
    fun trashFiles(): List<File> = trashDir.listFiles()?.toList().orEmpty()

    /**
     * Permanent home of an entry's audio, photo or pen page. Named by day and the
     * moment it was made, which is also its name in the Drive backup.
     */
    fun newJournalFile(date: LocalDate, createdAt: Long, extension: String): File {
        var file = File(journalDir, "$date-$createdAt.$extension")
        var n = 1
        while (file.exists()) file = File(journalDir, "$date-$createdAt-${n++}.$extension")
        return file
    }

    /**
     * Permanent home of a book's cover. The time is part of the name so that a changed
     * cover is a new file, which is how the screens know to show the new one.
     */
    fun newCoverFile(bookKey: String, now: Long = System.currentTimeMillis()): File {
        var file = File(booksDir, "$bookKey.cover-$now.jpg")
        var n = 1
        while (file.exists()) file = File(booksDir, "$bookKey.cover-$now-${n++}.jpg")
        return file
    }

    /**
     * Permanent home of a thought's voice note or page photo. Named by its book and
     * the moment it was made, which is also its name in the Drive backup.
     */
    fun newThoughtFile(bookKey: String, createdAt: Long, extension: String): File {
        var file = File(booksDir, "$bookKey-$createdAt.$extension")
        var n = 1
        while (file.exists()) file = File(booksDir, "$bookKey-$createdAt-${n++}.$extension")
        return file
    }

    /** Scratch file for a photo being taken or a voice note being recorded. */
    fun newScratchFile(extension: String): File =
        File(capturesDir, "scratch-${System.nanoTime()}.$extension")

    /** Scratch file for a fresh camera recording, before trimming. */
    fun newCaptureFile(): File = File(capturesDir, "capture-${System.currentTimeMillis()}.mp4")

    /** Scratch file for the output of a trim, before it replaces the day's clip. */
    fun newTrimOutputFile(): File = File(capturesDir, "trim-${System.currentTimeMillis()}.mp4")

    /** Scratch file for a compiled reel, before the user exports it. */
    fun newReelFile(): File = File(reelsDir, "reel-${System.currentTimeMillis()}.mp4")

    fun clearCaptures() {
        capturesDir.listFiles()?.forEach { it.delete() }
    }

    fun clearReels() {
        reelsDir.listFiles()?.forEach { it.delete() }
    }
}

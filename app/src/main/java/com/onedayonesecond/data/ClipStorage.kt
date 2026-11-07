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
class ClipStorage(private val context: Context) {

    private val clipsDir: File
        get() = File(context.filesDir, "clips").apply { mkdirs() }

    private val capturesDir: File
        get() = File(context.cacheDir, "captures").apply { mkdirs() }

    private val reelsDir: File
        get() = File(context.cacheDir, "reels").apply { mkdirs() }

    /** Stable, permanent location for a given day's trimmed second. */
    fun clipFile(date: LocalDate): File = File(clipsDir, "$date.mp4")

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

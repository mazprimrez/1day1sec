package com.onedayonesecond.media

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The decisions about which gallery videos are usable and which day they belong
 * to. Kept free of Android types so they can be unit-tested on a plain JVM.
 */
object GalleryRules {

    const val MAX_DURATION_MS = 5 * 60_000L
    const val MAX_BYTES = 1L shl 30

    /** Reels this app exports are named like this; they are output, not source material. */
    const val OWN_EXPORT_PREFIX = "kilasan-"

    /** How they were named while the app was called 1day1sec. Those are still its own output. */
    private const val FORMER_EXPORT_PREFIX = "1day1sec-"

    /** True if [displayName] is a reel this app exported, under its name now or before. */
    fun isOwnExport(displayName: String?): Boolean =
        displayName != null &&
            (displayName.startsWith(OWN_EXPORT_PREFIX, ignoreCase = true) || displayName.startsWith(FORMER_EXPORT_PREFIX))

    /** True if a video can go through the import and trim steps. */
    fun isCandidate(displayName: String?, durationMs: Long, sizeBytes: Long): Boolean =
        !isOwnExport(displayName) &&
            durationMs in VideoSpec.MIN_RECORDING_MS..MAX_DURATION_MS &&
            sizeBytes <= MAX_BYTES

    /**
     * When the video was made, as epoch milliseconds, or null if nothing is known.
     *
     * DATE_TAKEN is the real capture time but is often missing for videos that were
     * downloaded or received in a chat app, so fall back to the file's modified
     * time (usually the day it arrived), then to the day it was added.
     */
    fun takenAtMs(dateTakenMs: Long, dateModifiedSec: Long, dateAddedSec: Long): Long? = when {
        dateTakenMs > 0 -> dateTakenMs
        dateModifiedSec > 0 -> dateModifiedSec * 1000
        dateAddedSec > 0 -> dateAddedSec * 1000
        else -> null
    }

    /** The calendar day, in the user's own time zone, that [takenAtMs] falls on. */
    fun dateOf(takenAtMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(takenAtMs).atZone(zone).toLocalDate()
}

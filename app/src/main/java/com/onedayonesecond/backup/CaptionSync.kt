package com.onedayonesecond.backup

import java.time.LocalDate

/**
 * Works out what the captions file in Drive should say after a backup. Pure, so it
 * can be unit-tested on a plain JVM.
 *
 * It is a merge, not a copy of the phone's captions, on purpose. Imagine a new phone
 * with one clip on it and a backup holding a year of them: if "Back up now" were
 * tapped before restoring, writing only the phone's captions would delete the other
 * 364 from Drive.
 */
object CaptionSync {

    /**
     * @param remote captions currently in Drive, by day.
     * @param local the phone's clips, by day, with their caption (empty if none).
     * Every clip on the phone is listed, including those without a caption.
     * @param movedAway days whose clip was moved to another day on the phone; their old
     * caption has gone with the clip.
     */
    fun merge(
        remote: Map<LocalDate, String>,
        local: Map<LocalDate, String>,
        movedAway: Set<LocalDate>,
    ): Map<LocalDate, String> {
        val merged = HashMap<LocalDate, String>()

        // A day the phone has no clip for keeps what Drive says, unless it was moved
        // away. (Deleting a clip on the phone does not delete it from Drive either.)
        for ((day, text) in remote) {
            if (day !in local && day !in movedAway && text.isNotBlank()) merged[day] = text
        }
        // For a day the phone does have, the phone is right: a caption added or
        // changed here is saved, and one that was cleared here is dropped.
        for ((day, text) in local) {
            if (text.isNotBlank()) merged[day] = text
        }
        return merged
    }
}

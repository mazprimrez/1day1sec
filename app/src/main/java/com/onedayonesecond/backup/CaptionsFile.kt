package com.onedayonesecond.backup

import com.onedayonesecond.data.Captions
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * The captions file in Drive: a small JSON object from day to caption, readable by
 * anyone who opens it.
 *
 * ```
 * {
 *   "2026-11-03": "Lunch with Sam",
 *   "2026-11-07": "First day of the project"
 * }
 * ```
 */
object CaptionsFile {

    /**
     * Days are written in date order, so the same captions always produce the same
     * bytes. That is what lets a backup see "nothing changed" by comparing checksums
     * instead of uploading the file every time.
     */
    fun encode(captions: Map<LocalDate, String>): ByteArray {
        val json = JSONObject()
        for (day in captions.keys.sorted()) json.put(day.toString(), captions.getValue(day))
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    /**
     * Reads a file back. Anything unusable — a damaged file, a key that is not a date,
     * a blank value — is skipped rather than failing the whole restore, and what is
     * kept goes through the same tidying as typed captions.
     */
    fun decode(bytes: ByteArray): Map<LocalDate, String> {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            return emptyMap()
        }
        val captions = HashMap<LocalDate, String>()
        for (key in json.keys()) {
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val text = Captions.normalize(json.optString(key))
            if (text.isNotEmpty()) captions[day] = text
        }
        return captions
    }
}

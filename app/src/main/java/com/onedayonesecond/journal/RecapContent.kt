package com.onedayonesecond.journal

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** Which way something went compared with the period before. */
enum class Direction(val word: String) {
    UP("up"),
    DOWN("down"),
    SAME("same");

    companion object {
        fun of(word: String?): Direction =
            entries.firstOrNull { it.word.equals(word?.trim(), ignoreCase = true) } ?: SAME
    }
}

data class DatedLine(val date: LocalDate, val text: String)

data class Change(val direction: Direction, val text: String)

data class PlaceDays(val name: String, val days: Int)

/**
 * The chapters of a recap, as stored in `Recap.json`:
 *
 * ```
 * {
 *   "whatHappened": [{"date": "2026-11-09", "text": "Started the new project."}],
 *   "note": "You kept going on the days that felt heavy…",
 *   "changes": [{"direction": "up", "text": "You wrote on 5 days, up from 3."}],
 *   "tries": ["Write one line before bed.", "Take Wednesday's walk again."],
 *   "places": [{"name": "Jakarta Selatan", "days": 212}]
 * }
 * ```
 */
data class RecapContent(
    val whatHappened: List<DatedLine> = emptyList(),
    val note: String = "",
    val changes: List<Change> = emptyList(),
    val tries: List<String> = emptyList(),
    /** Days per place. Counted on the phone, never written by the model; only on year recaps. */
    val places: List<PlaceDays> = emptyList(),
) {

    fun encode(): String = JSONObject()
        .put(
            "whatHappened",
            JSONArray(whatHappened.map { JSONObject().put("date", it.date.toString()).put("text", it.text) }),
        )
        .put("note", note)
        .put(
            "changes",
            JSONArray(changes.map { JSONObject().put("direction", it.direction.word).put("text", it.text) }),
        )
        .put("tries", JSONArray(tries))
        .put("places", JSONArray(places.map { JSONObject().put("name", it.name).put("days", it.days) }))
        .toString()

    companion object {

        /** Suggestions are kept to two: the page promises something to try, not a list of chores. */
        const val MAX_TRIES = 2

        /**
         * Reads the chapters back. Anything unusable, such as a line without a date or
         * an empty suggestion, is skipped; a damaged file gives empty chapters rather
         * than a crash.
         */
        fun decode(json: String): RecapContent {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return RecapContent()
            }
            return RecapContent(
                whatHappened = root.objects("whatHappened").mapNotNull { line ->
                    val date = runCatching { LocalDate.parse(line.optString("date")) }.getOrNull()
                    val text = line.optString("text").trim()
                    if (date == null || text.isEmpty()) null else DatedLine(date, text)
                },
                note = root.optString("note").trim(),
                changes = root.objects("changes").mapNotNull { change ->
                    val text = change.optString("text").trim()
                    if (text.isEmpty()) null else Change(Direction.of(change.optString("direction")), text)
                },
                tries = root.strings("tries").take(MAX_TRIES),
                places = root.objects("places").mapNotNull { place ->
                    val name = place.optString("name").trim()
                    val days = place.optInt("days")
                    if (name.isEmpty() || days <= 0) null else PlaceDays(name, days)
                },
            )
        }

        private fun JSONObject.objects(name: String): List<JSONObject> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }

        private fun JSONObject.strings(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }
        }
    }
}

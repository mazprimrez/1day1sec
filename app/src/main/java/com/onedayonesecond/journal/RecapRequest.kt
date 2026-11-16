package com.onedayonesecond.journal

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * What is sent to the backend to have a recap written, and how its answer is read.
 * Pure JSON in and out, so both directions can be unit-tested on a plain JVM. The
 * shape is documented in docs/JOURNAL_BACKEND.md.
 */
object RecapRequest {

    /**
     * Entries are cut to this many characters each before they are sent. A year is a
     * lot of text; the recap needs the gist of each day, not every word.
     */
    fun maxEntryChars(period: RecapPeriod): Int = when (period) {
        is RecapPeriod.Week -> 4000
        is RecapPeriod.Month -> 1500
        is RecapPeriod.Year -> 400
    }

    /**
     * @param days every day of the period that has anything at all, oldest first.
     * @param previous the numbers of the period before, or null if there was nothing then.
     * @param name what the letter should call the user; blank leaves it to the model.
     */
    fun build(
        period: RecapPeriod,
        days: List<DaySummary>,
        previous: PeriodStats?,
        name: String,
    ): JSONObject {
        val limit = maxEntryChars(period)
        return JSONObject()
            .put("period", period.noun)
            .put("key", period.key)
            .put("start", period.start.toString())
            .put("end", period.end.toString())
            .put("name", name.trim())
            .put("language", Locale.getDefault().toLanguageTag())
            .put(
                "days",
                JSONArray(
                    days.sortedBy { it.date }.map { day ->
                        JSONObject()
                            .put("date", day.date.toString())
                            .put("weekday", day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                            .put("mood", day.mood?.name ?: JSONObject.NULL)
                            .put("place", day.place ?: JSONObject.NULL)
                            .put("caption", day.caption)
                            .put("entries", JSONArray(day.texts.map { it.take(limit) }))
                    },
                ),
            )
            .put("stats", stats(PeriodStats.of(days)))
            .put("previousStats", previous?.let(::stats) ?: JSONObject.NULL)
    }

    private fun stats(stats: PeriodStats): JSONObject = JSONObject()
        .put("daysKept", stats.daysKept)
        .put("daysWritten", stats.daysWritten)
        .put("words", stats.words)
        .put("averageMood", stats.averageMood ?: JSONObject.NULL)

    /** A recap as the model wrote it, before the quote is checked against the entries. */
    data class Draft(
        val title: String,
        val quote: String,
        val quoteDate: LocalDate?,
        val content: RecapContent,
    )

    /** Null when the answer has no title or no note: that is not a recap worth showing. */
    fun parse(reply: JSONObject): Draft? {
        val title = reply.optString("title").trim()
        val content = RecapContent.decode(reply.toString())
        if (title.isEmpty() || content.note.isEmpty()) return null
        return Draft(
            title = title,
            quote = reply.optString("quote").trim(),
            quoteDate = runCatching { LocalDate.parse(reply.optString("quoteDate")) }.getOrNull(),
            // Places are counted on the phone; whatever the model says about them is dropped.
            content = content.copy(places = emptyList()),
        )
    }
}

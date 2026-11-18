package com.onedayonesecond.backup

import com.onedayonesecond.data.BookStatus
import com.onedayonesecond.data.ThoughtType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** One thought as it is written in a book's backup file. [media] is the name of its file beside it. */
data class ThoughtBackup(
    val page: Int,
    val createdAt: Long,
    val type: ThoughtType,
    val text: String?,
    val media: String?,
    val showInDiary: Boolean,
)

/** A book as it is written in the backup. [cover] is the name of its cover file beside it. */
data class BookBackup(
    val key: String,
    val title: String,
    val author: String?,
    val isbn: String?,
    val edition: String?,
    val totalPages: Int?,
    val currentPage: Int,
    val status: BookStatus,
    val startedOn: LocalDate,
    val finishedOn: LocalDate?,
    val cover: String?,
    val thoughts: List<ThoughtBackup>,
)

/**
 * A book's file in Drive: one small, readable JSON file per book, with its cover and
 * its thoughts' voice notes and page photos beside it.
 *
 * ```
 * {
 *   "key": "2026-11-02-laut-bercerita-7k2q",
 *   "title": "Laut Bercerita",
 *   "author": "Leila S. Chudori",
 *   "totalPages": 379,
 *   "currentPage": 214,
 *   "status": "READING",
 *   "startedOn": "2026-11-02",
 *   "cover": "2026-11-02-laut-bercerita-7k2q.jpg",
 *   "thoughts": [
 *     {"page": 214, "createdAt": 1793500000000, "type": "WRITTEN", "text": "…", "showInDiary": true}
 *   ]
 * }
 * ```
 *
 * The summary is not in it: it is written from the thoughts, and can be written again.
 */
object BookFile {

    /**
     * Thoughts are written oldest first and keys in a fixed order, so the same book
     * always produces the same bytes. That is what lets a backup see "nothing changed"
     * by comparing checksums.
     */
    fun encode(book: BookBackup): ByteArray {
        val thoughts = JSONArray()
        for (thought in book.thoughts.sortedWith(ORDER)) {
            val json = JSONObject()
                .put("page", thought.page)
                .put("createdAt", thought.createdAt)
                .put("type", thought.type.name)
            thought.text?.let { json.put("text", it) }
            thought.media?.let { json.put("media", it) }
            json.put("showInDiary", thought.showInDiary)
            thoughts.put(json)
        }
        val json = JSONObject().put("key", book.key).put("title", book.title)
        book.author?.let { json.put("author", it) }
        book.isbn?.let { json.put("isbn", it) }
        book.edition?.let { json.put("edition", it) }
        book.totalPages?.let { json.put("totalPages", it) }
        json.put("currentPage", book.currentPage)
        json.put("status", book.status.name)
        json.put("startedOn", book.startedOn.toString())
        book.finishedOn?.let { json.put("finishedOn", it.toString()) }
        book.cover?.let { json.put("cover", it) }
        json.put("thoughts", thoughts)
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    /**
     * Reads a book's file back. Null for a damaged file or one without a key, a title
     * or a start date; a thought that cannot be understood is skipped rather than
     * failing the whole book.
     */
    fun decode(bytes: ByteArray): BookBackup? {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            return null
        }
        val key = json.textOrNull("key") ?: return null
        val title = json.textOrNull("title") ?: return null
        val startedOn = json.dateOrNull("startedOn") ?: return null
        val array = json.optJSONArray("thoughts")
        val thoughts = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val thought = array?.optJSONObject(index) ?: return@mapNotNull null
            val type = ThoughtType.entries.firstOrNull { it.name == thought.optString("type") } ?: return@mapNotNull null
            val createdAt = thought.optLong("createdAt", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val page = thought.optInt("page", -1).takeIf { it >= 1 } ?: return@mapNotNull null
            ThoughtBackup(
                page = page,
                createdAt = createdAt,
                type = type,
                text = thought.textOrNull("text"),
                media = thought.textOrNull("media"),
                showInDiary = thought.optBoolean("showInDiary", true),
            )
        }
        return BookBackup(
            key = key,
            title = title,
            author = json.textOrNull("author"),
            isbn = json.textOrNull("isbn"),
            edition = json.textOrNull("edition"),
            totalPages = json.optInt("totalPages", 0).takeIf { it > 0 },
            currentPage = json.optInt("currentPage", 0).coerceAtLeast(0),
            status = BookStatus.entries.firstOrNull { it.name == json.optString("status") } ?: BookStatus.READING,
            startedOn = startedOn,
            finishedOn = json.dateOrNull("finishedOn"),
            cover = json.textOrNull("cover"),
            thoughts = thoughts.sortedWith(ORDER),
        )
    }

    private fun JSONObject.textOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }

    private fun JSONObject.dateOrNull(name: String): LocalDate? =
        runCatching { LocalDate.parse(optString(name)) }.getOrNull()

    private val ORDER = compareBy<ThoughtBackup>({ it.createdAt }, { it.type.name }, { it.media.orEmpty() })
}

/**
 * Works out what a book's file in Drive should say after a backup. Pure, so it can be
 * unit-tested on a plain JVM.
 *
 * As with a day's journal, it is a merge and not a copy of the phone: thoughts that
 * are in Drive but not on the phone are kept, so a phone that has only part of a book
 * cannot wipe the rest from the backup. (That also means a thought deleted on the
 * phone stays in Drive, as a deleted entry does. A removed book is different: its
 * files are taken out of Drive by the next backup.)
 */
object BookSync {

    fun merge(remote: BookBackup?, local: BookBackup): BookBackup {
        if (remote == null) return local
        // The same thought on both sides is the phone's version: its words or its page
        // may have been edited. A thought is known by when it was logged, not by its
        // page, or one moved to another page would be kept twice.
        val merged = LinkedHashMap<Pair<Long, ThoughtType>, ThoughtBackup>()
        for (thought in remote.thoughts) merged[keyOf(thought)] = thought
        for (thought in local.thoughts) merged[keyOf(thought)] = thought
        // The book's details are the phone's, which is where they are edited. Only a
        // cover the phone does not have is carried over from Drive.
        return local.copy(cover = local.cover ?: remote.cover, thoughts = merged.values.toList())
    }

    private fun keyOf(thought: ThoughtBackup) = thought.createdAt to thought.type
}

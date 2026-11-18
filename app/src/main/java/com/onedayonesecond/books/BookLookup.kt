package com.onedayonesecond.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** How a search ended. */
sealed interface SearchOutcome {
    data class Found(val results: List<BookResult>) : SearchOutcome

    data object NoResults : SearchOutcome

    /** The phone could not reach Open Library at all. */
    data object Offline : SearchOutcome

    /**
     * Open Library answered with a refusal: it is down, or busy. The reader can still
     * add the book by hand.
     */
    data object Unavailable : SearchOutcome
}

/**
 * Looks books up, straight from the phone, at Open Library: search and covers. It is
 * free, needs no key, and does not go through the app's backend. Plain HTTPS, in the
 * same style as the Drive calls.
 */
class BookLookup(
    private val newScratchFile: (extension: String) -> File,
) {

    private val openLibraryLimiter = LookupLimiter()

    /** Small covers already fetched, so scrolling the results does not fetch them again. */
    private val images = object : LruCache<String, ByteArray>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun search(query: String): SearchOutcome = withContext(Dispatchers.IO) {
        try {
            val reply = get(OpenLibrary.searchUrl(query))
            if (reply.status != HttpURLConnection.HTTP_OK) return@withContext SearchOutcome.Unavailable
            val results = BookSearchOrder.arrange(query, OpenLibrary.parse(reply.bytes.toString(Charsets.UTF_8)))
            if (results.isEmpty()) SearchOutcome.NoResults else SearchOutcome.Found(results)
        } catch (e: IOException) {
            SearchOutcome.Offline
        }
    }

    /**
     * The bytes of a small cover for a list, or null if there is none or it cannot be
     * fetched now. Open Library lookups by ISBN are counted against its limit.
     */
    suspend fun image(url: String): ByteArray? = withContext(Dispatchers.IO) {
        images.get(url)?.let { return@withContext it }
        fetchImage(url)?.also { images.put(url, it) }
    }

    /**
     * Downloads the cover to keep for a book: the large version of the one its search
     * result showed, that one itself if there is no larger, then Open Library's by ISBN.
     *
     * @param resultCover the cover address of a search result ([BookResult.coverUrl]).
     * @return a scratch JPEG, or null if no cover was found or the phone is offline.
     */
    suspend fun downloadCover(resultCover: String?, isbn: String?): File? = withContext(Dispatchers.IO) {
        val candidates = buildList {
            resultCover?.let {
                add(OpenLibrary.largeCover(it))
                add(it)
            }
            isbn?.let { add(OpenLibrary.coverUrl(it)) }
        }.distinct()
        for (url in candidates) {
            val bytes = fetchImage(url) ?: continue
            return@withContext newScratchFile("jpg").also { it.writeBytes(asJpeg(bytes)) }
        }
        null
    }

    /**
     * The cover of a book known only by its ISBN: one added by hand, or scanned while
     * offline. Asked for by ISBN first; then whatever a search for that ISBN turns up.
     */
    suspend fun coverByIsbn(isbn: String): File? {
        downloadCover(resultCover = null, isbn = isbn)?.let { return it }
        val found = (search(isbn) as? SearchOutcome.Found)?.results?.firstOrNull { it.coverUrl != null }
        return found?.let { downloadCover(it.coverUrl, isbn = null) }
    }

    private fun fetchImage(url: String): ByteArray? {
        if (OpenLibrary.isIsbnLookup(url) && !openLibraryLimiter.tryAcquire()) return null
        val reply = try {
            get(url)
        } catch (e: IOException) {
            return null
        }
        if (reply.status != HttpURLConnection.HTTP_OK) return null
        // Asked for a cover it does not have, Open Library can send a one-pixel blank.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(reply.bytes, 0, reply.bytes.size, bounds)
        return reply.bytes.takeIf { bounds.outWidth >= MIN_COVER_WIDTH && bounds.outHeight > 0 }
    }

    /** Covers are kept as `.jpg`; the rare one that arrives as something else is converted. */
    private fun asJpeg(bytes: ByteArray): ByteArray {
        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (isJpeg) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private class Reply(val status: Int, val bytes: ByteArray)

    private fun get(url: String): Reply {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_REPLY_BYTES) throw IOException("The reply is too large")
                }
                out.toByteArray()
            } ?: ByteArray(0)
            Reply(status, bytes)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_REPLY_BYTES = 4 * 1024 * 1024
        private const val IMAGE_CACHE_BYTES = 4 * 1024 * 1024
        private const val MIN_COVER_WIDTH = 40
    }
}

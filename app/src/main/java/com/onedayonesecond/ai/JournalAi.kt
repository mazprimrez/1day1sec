package com.onedayonesecond.ai

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The three things the journal asks an AI model to do. The model (Gemini Flash-Lite)
 * is never called from the phone: requests go to the app's backend, which holds the
 * model key. See docs/JOURNAL_BACKEND.md for the contract.
 *
 * Every call throws [IOException] when the phone cannot reach the backend, and
 * [AiException] when the backend answers with an error.
 */
interface JournalAi {

    /** False when no backend address was set for this build; every call would fail. */
    val isConfigured: Boolean

    /** What was said in a voice note, as plain text. */
    suspend fun transcribe(audio: File): String

    /** The handwriting or print on a notebook photo or pen page, as plain text. */
    suspend fun extractText(image: File): String

    /** Writes a recap from the days in [request] and returns its JSON. */
    suspend fun writeRecap(request: JSONObject): JSONObject
}

/** The backend answered, but with an error. [retryable] means trying later may work. */
class AiException(val status: Int, message: String) : Exception(message) {
    val retryable: Boolean get() = status == 408 || status == 429 || status >= 500
}

/** This build has no backend address, so nothing can be typed out or written yet. */
class AiNotConfiguredException :
    Exception("The AI service is not set up for this build. Set journal.backend.url in local.properties.")

/** Plain HTTPS calls to the backend, in the same style as the Drive calls: no client library. */
class BackendJournalAi(baseUrl: String, private val key: String) : JournalAi {

    private val base = baseUrl.trim().trimEnd('/')

    override val isConfigured: Boolean get() = base.isNotEmpty()

    override suspend fun transcribe(audio: File): String =
        post("/v1/transcribe", audio.readBytes(), "audio/mp4").text()

    override suspend fun extractText(image: File): String {
        val type = if (image.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
        return post("/v1/extract-text", image.readBytes(), type).text()
    }

    override suspend fun writeRecap(request: JSONObject): JSONObject =
        post("/v1/recap", request.toString().toByteArray(Charsets.UTF_8), "application/json; charset=UTF-8")

    private fun JSONObject.text(): String = optString("text").trim()

    private suspend fun post(path: String, body: ByteArray, contentType: String): JSONObject =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!isConfigured) throw AiNotConfiguredException()
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Accept", "application/json")
                if (key.isNotEmpty()) setRequestProperty("Authorization", "Bearer $key")
                setFixedLengthStreamingMode(body.size)
            }
            try {
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val reply = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                if (status !in 200..299) throw AiException(status, errorMessage(status, reply))
                try {
                    JSONObject(reply)
                } catch (e: JSONException) {
                    throw AiException(status, "The AI service sent an answer that could not be read.")
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun errorMessage(status: Int, reply: String): String {
        val detail = runCatching { JSONObject(reply).optString("error") }.getOrNull().orEmpty()
        return detail.ifEmpty { "The AI service answered with error $status." }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000

        // A year's recap is one long model call; it has to be allowed to take a while.
        const val READ_TIMEOUT_MS = 120_000
    }
}

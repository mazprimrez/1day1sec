package com.onedayonesecond.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A Drive request that came back with an HTTP error. [reason] is Google's short code
 * for what went wrong when it gave one, such as "userRateLimitExceeded".
 */
class DriveException(
    val httpCode: Int,
    message: String,
    val reason: String? = null,
) : IOException(message)

/**
 * The Drive v3 calls the backup needs, over plain HTTPS.
 *
 * Every method blocks, so call them from Dispatchers.IO. Verbs are limited to
 * GET, POST and DELETE on purpose: HttpURLConnection on Android rejects PATCH.
 *
 * Every call retries a few times when it fails for a reason that can pass (see
 * [Retry]), so a brief loss of connection does not end a long backup. Calls that
 * create something check first whether an earlier try had already succeeded, so a
 * reply that was lost on the way back does not leave a duplicate behind.
 */
class DriveApi(private val accessToken: String) {

    /** [md5] is Drive's checksum of the bytes; null for folders. */
    data class RemoteFile(
        val id: String,
        val name: String,
        val isFolder: Boolean,
        val md5: String?,
    )

    /** A folder with this name, or null if there is none. Inside [parentId] if given. */
    fun findFolder(name: String, parentId: String? = null): String? {
        require('\'' !in name) { "Folder name cannot contain a quote" }
        val query = buildString {
            append("name = '$name' and mimeType = '$FOLDER_MIME' and trashed = false")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val json = get("$FILES_URL?q=${enc(query)}&fields=${enc("files(id)")}&pageSize=1&spaces=drive")
        val files = json.getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    /** Creates a folder, inside [parentId] if given or at the top of the Drive otherwise. */
    fun createFolder(name: String, parentId: String? = null): String {
        val body = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        if (parentId != null) body.put("parents", JSONArray().put(parentId))
        return Retry.run(
            // If an earlier try went through but its reply was lost, the folder is
            // already there; making a second one would leave two with the same name.
            recover = { findFolder(name, parentId) },
        ) {
            postJson("$FILES_URL?fields=id", body.toString()).getString("id")
        }
    }

    /** Everything directly inside [folderId] (files and folders), following pagination. */
    fun listChildren(folderId: String): List<RemoteFile> {
        val found = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val query = "'$folderId' in parents and trashed = false"
            val fields = "nextPageToken,files(id,name,mimeType,md5Checksum)"
            var url = "$FILES_URL?q=${enc(query)}&fields=${enc(fields)}&pageSize=1000&spaces=drive"
            if (pageToken != null) url += "&pageToken=${enc(pageToken)}"

            val json = get(url)
            val files = json.getJSONArray("files")
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                found += RemoteFile(
                    id = file.getString("id"),
                    name = file.getString("name"),
                    isFolder = file.getString("mimeType") == FOLDER_MIME,
                    md5 = file.optString("md5Checksum").ifEmpty { null },
                )
            }
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        return found
    }

    /**
     * Single-request multipart upload, which Drive supports up to 5 MB. A one-second
     * 720p clip is around half a megabyte, so the resumable protocol would only add
     * calls.
     */
    fun upload(folderId: String, name: String, bytes: ByteArray, mimeType: String): String {
        require(bytes.size <= MAX_SIMPLE_UPLOAD_BYTES) {
            "$name is too large (${bytes.size / 1024} KB) for a simple upload"
        }
        val boundary = "1day1sec-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(folderId))
            .toString()

        val head = (
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: $mimeType\r\n\r\n"
            ).toByteArray()
        val tail = "\r\n--$boundary--".toByteArray()
        val checksum = md5Hex(bytes)

        return Retry.run(
            // A connection that drops while the reply is on its way back looks the same
            // as one that dropped mid-upload. Look for the file before sending it again,
            // so the same clip is never uploaded twice.
            recover = { listChildren(folderId).firstOrNull { it.name == name && it.md5 == checksum }?.id },
        ) {
            val connection = open("$UPLOAD_URL?uploadType=multipart&fields=id", "POST")
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(head.size + bytes.size + tail.size)
            connection.outputStream.use { out ->
                out.write(head)
                out.write(bytes)
                out.write(tail)
            }
            JSONObject(String(readBytes(connection))).getString("id")
        }
    }

    fun download(fileId: String): ByteArray = Retry.run {
        readBytes(open("$FILES_URL/$fileId?alt=media", "GET"))
    }

    fun delete(fileId: String) {
        Retry.run {
            try {
                readBytes(open("$FILES_URL/$fileId", "DELETE"))
            } catch (e: DriveException) {
                // Already gone — including by an earlier try whose reply was lost.
                // Deleting is meant to end with the file not existing, and it does.
                if (e.httpCode != 404) throw e
            }
        }
    }

    // --- plumbing -------------------------------------------------------------

    private fun get(url: String): JSONObject = Retry.run {
        JSONObject(String(readBytes(open(url, "GET"))))
    }

    private fun postJson(url: String, body: String): JSONObject {
        val connection = open(url, "POST")
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray()) }
        return JSONObject(String(readBytes(connection)))
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

    private fun readBytes(connection: HttpURLConnection): ByteArray {
        try {
            val code = connection.responseCode
            if (code in 200..299) {
                return connection.inputStream.use { it.readBytes() }
            }
            val body = connection.errorStream?.use { String(it.readBytes()) }.orEmpty()
            throw DriveException(code, errorMessage(code, body), errorReason(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        // Google's own message is the useful part: for a disabled API it even
        // includes the link to switch it on.
        val google = runCatching {
            JSONObject(body).getJSONObject("error").getString("message")
        }.getOrNull()
        return when {
            code == 401 -> "Google Drive rejected the sign-in. Please try again."
            google != null -> google
            else -> "Google Drive returned an error (HTTP $code)."
        }
    }

    /** Google's short code for the first thing that went wrong, if the reply gave one. */
    private fun errorReason(body: String): String? = runCatching {
        JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).getString("reason")
    }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /**
         * The largest file [upload] accepts. Public so a backup can leave out anything
         * bigger instead of failing on it.
         */
        const val MAX_SIMPLE_UPLOAD_BYTES = 5 * 1024 * 1024 - 4096 // headroom for the metadata part

        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}

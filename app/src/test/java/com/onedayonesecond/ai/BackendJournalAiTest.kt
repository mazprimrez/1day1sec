package com.onedayonesecond.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/** The backend only answers its owner, so every request must carry the Google token. */
class BackendJournalAiTest {

    private class FakeTokens(vararg tokens: String) : AccessTokens {
        private val waiting = ArrayDeque(tokens.toList())
        val discarded = mutableListOf<String>()
        var signInNeeded = false

        override suspend fun current(): String {
            if (signInNeeded) throw AiSignInNeededException()
            return waiting.removeFirst()
        }

        override suspend fun discard(token: String) {
            discarded += token
        }
    }

    private lateinit var server: ServerSocket
    private val seen = Collections.synchronizedList(mutableListOf<String>())

    /** The backend lets these tokens in; anything else gets the 401 it gives strangers. */
    @Volatile
    private var accepted = setOf("good")

    /** A stand-in backend: reads one request per connection and answers by its token. */
    @Before
    fun start() {
        server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: IOException) { break }
                socket.use(::answer)
            }
        }
    }

    private fun answer(socket: Socket) {
        val input = socket.getInputStream().buffered()
        val headers = generateSequence { readLine(input) }.takeWhile { it.isNotEmpty() }.toList()
        fun header(name: String) = headers.firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        repeat(header("Content-Length").toIntOrNull() ?: 0) { input.read() }
        val token = header("Authorization").removePrefix("Bearer ")
        seen += token
        val (status, body) = if (token in accepted) {
            "200 OK" to """{"title":"A week","note":"A note."}"""
        } else {
            "401 Unauthorized" to """{"error":"Your Google sign-in is no longer valid. Sign in again."}"""
        }
        val bytes = body.toByteArray()
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
    }

    @After
    fun stop() = server.close()

    private fun ai(tokens: AccessTokens) = BackendJournalAi("http://127.0.0.1:${server.localPort}/", tokens)

    @Test
    fun `a request carries the Google token`() = runBlocking {
        val reply = ai(FakeTokens("good")).writeRecap(JSONObject().put("period", "week"))

        assertEquals("A week", reply.getString("title"))
        assertEquals(listOf("good"), seen.toList())
    }

    @Test
    fun `a refused token is thrown away and a fresh one is tried once`() = runBlocking {
        val tokens = FakeTokens("stale", "good")

        val reply = ai(tokens).writeRecap(JSONObject())

        assertEquals("A week", reply.getString("title"))
        assertEquals(listOf("stale", "good"), seen.toList())
        assertEquals(listOf("stale"), tokens.discarded)
    }

    @Test
    fun `an account the backend does not allow fails after one retry, not in a loop`() {
        accepted = emptySet()
        val tokens = FakeTokens("stranger-1", "stranger-2", "stranger-3")

        val error = assertThrows(AiException::class.java) { runBlocking { ai(tokens).writeRecap(JSONObject()) } }

        assertEquals(401, error.status)
        // Not retried by itself: the entry shows "Try again" instead of waiting forever.
        assertTrue(!error.retryable)
        assertEquals(listOf("stranger-1", "stranger-2"), seen.toList())
    }

    @Test
    fun `nothing is sent while Google still has to ask the user`() {
        val tokens = FakeTokens("good").apply { signInNeeded = true }

        assertThrows(AiSignInNeededException::class.java) { runBlocking { ai(tokens).writeRecap(JSONObject()) } }

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `nothing is sent, and no token is asked for, without a backend address`() {
        val tokens = FakeTokens()

        assertThrows(AiNotConfiguredException::class.java) {
            runBlocking { BackendJournalAi("  ", tokens).writeRecap(JSONObject()) }
        }

        assertTrue(seen.isEmpty())
    }
}

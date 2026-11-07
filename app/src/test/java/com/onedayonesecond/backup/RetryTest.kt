package com.onedayonesecond.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class RetryTest {

    /** Records the waits instead of really waiting. */
    private val waits = mutableListOf<Long>()
    private val noSleep: (Long) -> Unit = { waits += it }

    /** A block that fails with [failure] this many times, then returns [result]. */
    private class Flaky<T>(private val failures: Int, private val failure: () -> Exception, private val result: T) {
        var calls = 0
        fun call(): T {
            calls++
            if (calls <= failures) throw failure()
            return result
        }
    }

    private fun drive(code: Int, reason: String? = null) = DriveException(code, "http $code", reason)

    // --- the loop -----------------------------------------------------------------

    @Test
    fun `a request that works is run once with no waiting`() {
        val flaky = Flaky(0, { IOException() }, "ok")
        assertEquals("ok", Retry.run(noSleep) { flaky.call() })
        assertEquals(1, flaky.calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun `a dropped connection is retried and then succeeds`() {
        val flaky = Flaky(2, { SocketException("Connection reset") }, "ok")
        assertEquals("ok", Retry.run(noSleep) { flaky.call() })
        assertEquals(3, flaky.calls)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test
    fun `it gives up after five tries and reports the last failure`() {
        val flaky = Flaky(99, { SocketException("still down") }, "never")
        val error = assertThrows(SocketException::class.java) { Retry.run(noSleep) { flaky.call() } }
        assertEquals("still down", error.message)
        assertEquals(Retry.MAX_ATTEMPTS, flaky.calls)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), waits)
    }

    @Test
    fun `the last of exactly five failures still succeeds on the fifth try`() {
        val flaky = Flaky(4, { IOException("blip") }, "made it")
        assertEquals("made it", Retry.run(noSleep) { flaky.call() })
        assertEquals(5, flaky.calls)
    }

    @Test
    fun `a failure that would repeat is thrown at once without waiting`() {
        for (code in listOf(400, 401, 403, 404)) {
            waits.clear()
            val flaky = Flaky(99, { drive(code) }, "never")
            val error = assertThrows(DriveException::class.java) { Retry.run(noSleep) { flaky.call() } }
            assertEquals(code, error.httpCode)
            assertEquals("code $code", 1, flaky.calls)
            assertTrue("code $code", waits.isEmpty())
        }
    }

    @Test
    fun `a programming error is not retried`() {
        val flaky = Flaky(99, { IllegalStateException("bug") }, "never")
        assertThrows(IllegalStateException::class.java) { Retry.run(noSleep) { flaky.call() } }
        assertEquals(1, flaky.calls)
    }

    // --- recovering from a lost reply ---------------------------------------------

    @Test
    fun `if the earlier try had really worked its result is used instead of trying again`() {
        val flaky = Flaky(99, { SocketException("reply lost") }, "second upload")
        val result = Retry.run(noSleep, recover = { "found the first upload" }) { flaky.call() }
        assertEquals("found the first upload", result)
        assertEquals("the request was not sent a second time", 1, flaky.calls)
    }

    @Test
    fun `recovery that finds nothing means the request is tried again`() {
        val flaky = Flaky(1, { SocketException("lost") }, "uploaded")
        assertEquals("uploaded", Retry.run(noSleep, recover = { null }) { flaky.call() })
        assertEquals(2, flaky.calls)
    }

    @Test
    fun `recovery that itself fails is treated as finding nothing`() {
        val flaky = Flaky(1, { SocketException("lost") }, "uploaded")
        val result = Retry.run(noSleep, recover = { throw IOException("still offline") }) { flaky.call() }
        assertEquals("uploaded", result)
        assertEquals(2, flaky.calls)
    }

    @Test
    fun `recovery is not consulted when the first try works`() {
        var asked = false
        val result = Retry.run(noSleep, recover = { asked = true; "x" }) { "fine" }
        assertEquals("fine", result)
        assertFalse(asked)
    }

    @Test
    fun `recovery is not consulted for a failure that is not retried`() {
        var asked = false
        assertThrows(DriveException::class.java) {
            Retry.run<String>(noSleep, recover = { asked = true; "x" }) { throw drive(404) }
        }
        assertFalse(asked)
    }

    // --- which failures count as temporary ----------------------------------------

    @Test
    fun `network trouble of every kind is temporary`() {
        val temporary = listOf(
            SocketException("Software caused connection abort"),
            SocketException("Connection reset by peer"),
            SocketTimeoutException("Read timed out"),
            UnknownHostException("Unable to resolve host"),
            EOFException(),
            ProtocolException("unexpected end of stream"),
            SSLException("Read error: Connection reset by peer"),
            IOException("anything else"),
        )
        for (e in temporary) assertTrue(e.toString(), Retry.isRetryable(e))
    }

    @Test
    fun `google being busy or failing is temporary`() {
        for (code in listOf(408, 429, 500, 502, 503, 504, 599)) {
            assertTrue("code $code", Retry.isRetryable(drive(code)))
        }
    }

    @Test
    fun `being asked to slow down is temporary even when reported as forbidden`() {
        assertTrue(Retry.isRetryable(drive(403, "userRateLimitExceeded")))
        assertTrue(Retry.isRetryable(drive(403, "rateLimitExceeded")))
    }

    @Test
    fun `a real permission or quota problem is not temporary`() {
        assertFalse(Retry.isRetryable(drive(403)))
        assertFalse(Retry.isRetryable(drive(403, "storageQuotaExceeded")))
        assertFalse(Retry.isRetryable(drive(403, "insufficientFilePermissions")))
    }

    @Test
    fun `a rejected sign-in or bad request is not temporary`() {
        for (code in listOf(400, 401, 404, 410)) assertFalse("code $code", Retry.isRetryable(drive(code)))
    }

    @Test
    fun `things that are not network errors are not temporary`() {
        assertFalse(Retry.isRetryable(IllegalArgumentException()))
        assertFalse(Retry.isRetryable(RuntimeException()))
    }

    // --- the pauses ---------------------------------------------------------------

    @Test
    fun `the pauses double and then stop growing`() {
        val delays = (1..8).map(Retry::delayAfterFailureMs)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 16_000L, 16_000L, 16_000L), delays)
    }

    @Test
    fun `the total wait before giving up is about a quarter of a minute`() {
        val total = (1 until Retry.MAX_ATTEMPTS).sumOf(Retry::delayAfterFailureMs)
        assertEquals(15_000L, total)
    }

    @Test
    fun `the same failure object is what comes out`() {
        val original = SocketException("same one")
        val thrown = assertThrows(SocketException::class.java) {
            Retry.run<String>(noSleep) { throw original }
        }
        assertSame(original, thrown)
    }
}

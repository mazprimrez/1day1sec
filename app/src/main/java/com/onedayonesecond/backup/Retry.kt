package com.onedayonesecond.backup

import java.io.IOException

/**
 * Retrying Drive requests that failed for a reason that may pass. A backup is about a
 * hundred requests in a row on a phone's network, and without this a single dropped
 * connection ended the whole run. Free of Android types so it can be unit-tested.
 */
internal object Retry {

    /** The first try plus four more. */
    const val MAX_ATTEMPTS = 5

    private const val BASE_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 16_000L

    private val RETRYABLE_CODES = setOf(408, 429)

    /** Drive's own names for "slow down", which it reports as 403 rather than 429. */
    private val RATE_LIMIT_REASONS = setOf("userRateLimitExceeded", "rateLimitExceeded")

    /**
     * How long to wait after the [attempt]th try failed: 1s, 2s, 4s, 8s, then never
     * more than 16s. Growing pauses give a briefly lost Wi-Fi time to come back, and
     * give an overloaded server room, which a quick-fire retry would not.
     */
    fun delayAfterFailureMs(attempt: Int): Long =
        minOf(MAX_DELAY_MS, BASE_DELAY_MS shl (attempt - 1).coerceIn(0, 10))

    /**
     * True for failures that can pass by themselves: any network trouble, Google
     * being busy or erroring, or being asked to slow down. False for those that would
     * fail the same way again, such as a rejected sign-in or a bad request.
     */
    fun isRetryable(e: Throwable): Boolean = when (e) {
        is DriveException ->
            e.httpCode in RETRYABLE_CODES ||
                e.httpCode in 500..599 ||
                (e.httpCode == 403 && e.reason in RATE_LIMIT_REASONS)
        // Dropped connections, resets, timeouts, no route, and DNS failing while the
        // phone reconnects all arrive as IOExceptions.
        is IOException -> true
        else -> false
    }

    /**
     * Runs [block], retrying while it fails with something [isRetryable] — up to
     * [MAX_ATTEMPTS] tries — and rethrows the last failure if it never works.
     *
     * @param recover for requests that are not safe to simply repeat (creating a
     * file or folder): called before each retry, and if it returns a result the
     * earlier try had in fact already succeeded, so that is used instead of trying
     * again. This is what stops a lost *reply* from becoming a duplicate upload. If
     * recovering itself fails it is treated as "found nothing".
     * @param sleep replaceable so tests need not wait.
     */
    fun <T> run(
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        recover: () -> T? = { null },
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= MAX_ATTEMPTS || !isRetryable(e)) throw e
                sleep(delayAfterFailureMs(attempt))
                val alreadyDone = try {
                    recover()
                } catch (recoveryFailure: Exception) {
                    null
                }
                if (alreadyDone != null) return alreadyDone
                attempt++
            }
        }
    }
}

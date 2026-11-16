package com.onedayonesecond.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthOutcome {
    /** Google already trusts this app for Drive; [accessToken] is ready to use. */
    data class Granted(val accessToken: String) : AuthOutcome

    /** First run: Google must show its consent screen, launched from [pendingIntent]. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome
}

/**
 * Gets a short-lived Drive access token through Google's Authorization API.
 *
 * The `drive.file` scope only lets the app see files it created itself, so it can
 * never read or touch anything else in the user's Drive. There is no client ID or
 * secret in this code: Google identifies the app by its package name plus signing
 * certificate, which are registered in Google Cloud Console (docs/DRIVE_BACKUP.md).
 */
class DriveAuth(context: Context) {

    private val client = Identity.getAuthorizationClient(context)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    /** Silent when consent was given before, otherwise asks for the consent screen. */
    suspend fun authorize(): AuthOutcome = client.authorize(request).await().toOutcome()

    /** Reads the token out of the intent returned by the consent screen. */
    fun fromConsentResult(data: Intent?): AuthOutcome.Granted? {
        if (data == null) return null
        return client.getAuthorizationResultFromIntent(data).toOutcome() as? AuthOutcome.Granted
    }

    private fun AuthorizationResult.toOutcome(): AuthOutcome {
        if (hasResolution()) {
            val pending = pendingIntent ?: error("Google asked for consent but gave no screen to show")
            return AuthOutcome.NeedsConsent(pending)
        }
        val token = accessToken ?: error("Google did not return an access token")
        return AuthOutcome.Granted(token)
    }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

/** Play Services returns `Task`s; this avoids a dependency just to await one. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

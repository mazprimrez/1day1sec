package com.onedayonesecond.ai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.onedayonesecond.backup.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** Where the backend's proof of who is asking comes from. */
interface AccessTokens {

    /**
     * A token to send with the next request. Throws [AiSignInNeededException] when
     * Google has to ask the user first, and [IOException] when Google cannot be reached.
     */
    suspend fun current(): String

    /** The backend refused [token]; the next call to [current] must not return it again. */
    suspend fun discard(token: String)
}

/** Google has to ask the user once before the backend can be told who they are. */
class AiSignInNeededException :
    Exception("Allow the app to confirm your Google account first, then try again.")

/**
 * Gets a short-lived Google access token that says which account is using the app, the
 * same way [com.onedayonesecond.backup.DriveAuth] gets one for Drive. The backend asks
 * Google whom the token belongs to and lets only its owner's account through.
 *
 * Once the user has allowed it, a token comes back without any screen, which is what
 * lets the nightly recap job run with the app closed. The only thing asked for is the
 * account's email address. There is no client ID or secret in this code: Google knows
 * the app by its package name and signing certificate (docs/DRIVE_BACKUP.md).
 */
class BackendAuth(context: Context) : AccessTokens {

    private val client = Identity.getAuthorizationClient(context)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(EMAIL_SCOPE)))
        .build()

    private val _consent = MutableStateFlow<PendingIntent?>(null)

    /** Google's consent screen, while one has to be shown before the backend will answer. */
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    override suspend fun current(): String {
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw e.asAiFailure()
        }
        if (result.hasResolution()) {
            _consent.value = result.pendingIntent
            throw AiSignInNeededException()
        }
        _consent.value = null
        return result.accessToken ?: throw AiException(SIGN_IN_FAILED, "Google did not confirm your account.")
    }

    override suspend fun discard(token: String) {
        // Best effort: if this fails the retry simply gets the same answer from the backend.
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** [data] is the consent screen's result, or null if it was dismissed. True when it was allowed. */
    fun onConsentResult(data: Intent?): Boolean {
        val allowed = data != null &&
            runCatching { client.getAuthorizationResultFromIntent(data).accessToken != null }.getOrDefault(false)
        if (allowed) _consent.value = null
        return allowed
    }

    private fun ApiException.asAiFailure(): Exception = when (statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT ->
            IOException("Google could not be reached to confirm your account.", this)
        else -> AiException(SIGN_IN_FAILED, "Google sign-in is not working on this phone (code $statusCode).")
    }

    private companion object {
        const val EMAIL_SCOPE = "email"

        // Reported like the backend's own "not signed in", which the app does not retry by itself.
        const val SIGN_IN_FAILED = 401
    }
}

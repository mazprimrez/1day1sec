package com.onedayonesecond.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.onedayonesecond.journal.PlaceNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where the phone is right now, as a short name such as "Kemang, Jakarta Selatan".
 *
 * One fix per entry, taken when the entry is made; nothing is tracked in between and
 * the coordinates themselves are never stored. Without location permission, or when
 * the phone cannot tell, the answer is simply null and place lines are left out.
 */
class PlaceFinder(context: Context) {

    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    val hasPermission: Boolean
        get() = PERMISSIONS.any {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Null when not allowed, not known within a few seconds, or not nameable. Never throws. */
    suspend fun current(): String? {
        if (!hasPermission) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val location = fix() ?: return@withTimeoutOrNull null
                withContext(Dispatchers.IO) { name(location) }
            }
        } catch (e: SecurityException) {
            null // permission was taken away between the check and the call
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null // no Play services, no geocoder, no network: just no place
        }
    }

    @SuppressLint("MissingPermission") // checked by the caller, and SecurityException is caught
    private suspend fun fix(): Location? {
        val cancel = CancellationTokenSource()
        val fresh = client
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .awaitOrNull { cancel.cancel() }
        return fresh ?: client.lastLocation.awaitOrNull { }
    }

    @Suppress("DEPRECATION") // the callback version needs API 33; this already runs off the main thread
    private fun name(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val address = Geocoder(appContext, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull() ?: return null
        return PlaceNames.short(
            neighbourhood = address.subLocality,
            district = address.locality,
            city = address.subAdminArea,
            region = address.adminArea,
        )
    }

    private suspend fun <T> Task<T>.awaitOrNull(onCancel: () -> Unit): T? =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
            addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
            continuation.invokeOnCancellation { onCancel() }
        }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        private const val TIMEOUT_MS = 6_000L
    }
}

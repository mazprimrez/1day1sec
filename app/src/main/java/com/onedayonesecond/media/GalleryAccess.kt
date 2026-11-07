package com.onedayonesecond.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class GalleryAccessLevel {
    /** Not allowed (or not asked yet). The app cannot see the gallery at all. */
    NONE,

    /** Android 14+: the user allowed only some videos. Those are all that is visible. */
    PARTIAL,

    /** Every video on the phone is visible. */
    FULL,
}

/**
 * Permission rules for reading the phone's gallery index.
 *
 * This is separate from the Photo Picker used by the camera screen's gallery
 * button: the picker needs no permission but cannot tell the app which days have
 * videos. Finding those days means reading the gallery index, which needs the
 * user's say-so — so it is only ever requested after they tap the gallery icon.
 */
object GalleryAccess {

    /**
     * What to ask for. On Android 14+ the "selected videos only" permission must be
     * requested alongside the full one, or the system will not offer the choice.
     */
    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun level(context: Context): GalleryAccessLevel {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= 33 -> when {
                granted(Manifest.permission.READ_MEDIA_VIDEO) -> GalleryAccessLevel.FULL
                Build.VERSION.SDK_INT >= 34 &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
                    GalleryAccessLevel.PARTIAL
                else -> GalleryAccessLevel.NONE
            }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccessLevel.FULL
            else -> GalleryAccessLevel.NONE
        }
    }
}

package com.onedayonesecond.ui.nav

import android.util.Base64
import java.io.File
import java.time.LocalDate

object Routes {

    const val ARG_DATE = "date"
    const val ARG_PATH = "path"

    const val HOME = "home"
    const val COMPILE = "compile"
    const val BACKUP = "backup"
    const val CAMERA_PATTERN = "camera/{$ARG_DATE}"
    const val GALLERY_DAY_PATTERN = "gallery/{$ARG_DATE}"
    const val TRIM_PATTERN = "trim/{$ARG_DATE}/{$ARG_PATH}"

    fun camera(date: LocalDate) = "camera/${date.toEpochDay()}"

    fun galleryDay(date: LocalDate) = "gallery/${date.toEpochDay()}"

    fun trim(date: LocalDate, file: File) =
        "trim/${date.toEpochDay()}/${encodePath(file.absolutePath)}"

    // Base64url rather than Uri.encode: NavController percent-decodes route
    // segments before matching, so an encoded '/' in a file path would split the
    // segment and the route would never match.
    private fun encodePath(path: String): String =
        Base64.encodeToString(path.toByteArray(), BASE64_FLAGS)

    fun decodePath(encoded: String): String =
        String(Base64.decode(encoded, BASE64_FLAGS))

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}

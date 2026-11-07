package com.onedayonesecond.backup

import java.security.MessageDigest

/**
 * Drive reports an MD5 for every binary file, so matching it means "same bytes".
 * Used only to tell files apart, never for security.
 */
internal fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

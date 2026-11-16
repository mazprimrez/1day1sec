package com.onedayonesecond.media

/**
 * The arithmetic behind clip thumbnails, free of Android types so it can be
 * unit-tested on a plain JVM. The decoding itself is in ClipThumbnails.kt.
 */
object ThumbnailRules {

    /** A region of a frame: top-left corner and size, in pixels. */
    data class Crop(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * The largest 9:16 region in the middle of a [width] x [height] frame. Clip tiles
     * are portrait everywhere in the app, and stored clips already are 9:16, so for
     * them this is the whole frame; anything else loses its sides or its top and bottom.
     */
    fun centerPortrait(width: Int, height: Int): Crop {
        require(width > 0 && height > 0) { "Frame has no size: ${width}x$height" }
        return if (width.toLong() * 16 > height.toLong() * 9) {
            val w = maxOf(1, height * 9 / 16)
            Crop(x = (width - w) / 2, y = 0, width = w, height = height)
        } else {
            val h = maxOf(1, minOf(height, width * 16 / 9))
            Crop(x = 0, y = (height - h) / 2, width = width, height = h)
        }
    }

    /**
     * Which moment of the clip to show, in microseconds: the middle. The first frame
     * of a recording is often a blur or a hand reaching for the button.
     */
    fun frameTimeUs(durationMs: Long): Long = maxOf(0L, durationMs) * 1000L / 2

    /**
     * Identifies one particular recording. The file path alone is not enough: taking
     * a day again writes a new file to the same path, so the creation time is part of
     * the key and the old picture is never shown for the new clip.
     */
    fun cacheKey(filePath: String, createdAt: Long): String = "$createdAt:$filePath"
}

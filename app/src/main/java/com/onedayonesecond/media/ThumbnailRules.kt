package com.onedayonesecond.media

/**
 * The arithmetic behind calendar thumbnails, free of Android types so it can be
 * unit-tested on a plain JVM. The decoding itself is in ClipThumbnails.kt.
 */
object ThumbnailRules {

    /** A square region of a frame: top-left corner and side length, in pixels. */
    data class Square(val x: Int, val y: Int, val side: Int)

    /**
     * The largest square in the middle of a [width] x [height] frame. Calendar cells
     * are square and clips are tall, so this keeps the centre of the picture and
     * drops the top and bottom, which is also what the cell would crop anyway.
     */
    fun centerSquare(width: Int, height: Int): Square {
        require(width > 0 && height > 0) { "Frame has no size: ${width}x$height" }
        val side = minOf(width, height)
        return Square(x = (width - side) / 2, y = (height - side) / 2, side = side)
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

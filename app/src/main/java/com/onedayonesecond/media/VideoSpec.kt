package com.onedayonesecond.media

/**
 * Every clip is normalised to one shape at trim time.
 *
 * This matters more than it looks: concatenating clips with mixed resolutions,
 * rotations or codecs is where video stitching usually falls apart. By forcing
 * a single portrait H.264/AAC profile the moment a clip is saved, the compile
 * step only ever sees homogeneous input.
 */
object VideoSpec {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val CLIP_DURATION_MS = 1_000L
    const val MAX_RECORDING_MS = 5_000L
    /** Keeps the trim window from running past the end of a short recording. */
    const val MIN_RECORDING_MS = 1_000L
}

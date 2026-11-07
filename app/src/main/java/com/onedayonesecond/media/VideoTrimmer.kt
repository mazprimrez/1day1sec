package com.onedayonesecond.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import java.io.File

/**
 * Cuts a recording down to exactly one second, re-encoded to [VideoSpec].
 *
 * Why Media3 Transformer and not FFmpeg: see docs/MEDIA_PIPELINE.md. Short
 * version — ffmpeg-kit was retired upstream, and Transformer covers trim and
 * concatenate with hardware codecs and no licensing tail.
 */
@UnstableApi
class VideoTrimmer(private val context: Context) {

    /**
     * @param startMs offset into [input] where the kept second begins.
     * @return the duration of [output] in milliseconds.
     */
    suspend fun trimToOneSecond(input: File, output: File, startMs: Long): Long {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(startMs + VideoSpec.CLIP_DURATION_MS)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(input))
            .setClippingConfiguration(clipping)
            .build()

        // The Presentation effect does double duty: it normalises every clip to
        // the same frame size, and because an effect is present Transformer must
        // decode and re-encode, which makes the cut frame-accurate instead of
        // snapping back to the previous keyframe.
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    ImmutableList.of(),
                    ImmutableList.of<Effect>(
                        Presentation.createForWidthAndHeight(
                            VideoSpec.WIDTH,
                            VideoSpec.HEIGHT,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                        ),
                    ),
                ),
            )
            .build()

        val result = runTransformer(context) { transformer ->
            transformer.start(editedItem, output.absolutePath)
        }

        return result.durationMs.takeIf { it > 0 } ?: VideoSpec.CLIP_DURATION_MS
    }
}

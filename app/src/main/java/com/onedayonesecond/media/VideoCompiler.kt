package com.onedayonesecond.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import java.io.File
import java.time.LocalDate

/** A stored clip together with the day it belongs to. */
data class DatedClip(val date: LocalDate, val file: File)

/**
 * Stitches daily clips into a single reel, in the order given.
 */
@UnstableApi
class VideoCompiler(private val context: Context) {

    /**
     * @param clips source clips, already in chronological order.
     * @param output destination file for the reel.
     * @param showDates burn each clip's date into the frame.
     * @param dateStyle font and position of that date; ignored when [showDates] is false.
     * @param onProgress fraction complete, 0f..1f.
     */
    suspend fun compile(
        clips: List<DatedClip>,
        output: File,
        showDates: Boolean = true,
        dateStyle: DateLabelStyle = DateLabelStyle(),
        onProgress: (Float) -> Unit = {},
    ) {
        require(clips.isNotEmpty()) { "Nothing to compile" }

        val items = clips.map { clip ->
            // Order matters: Presentation fixes the frame size first, so the
            // overlay below is laid out against the final 720x1280 frame.
            val videoEffects = buildList<Effect> {
                add(
                    Presentation.createForWidthAndHeight(
                        VideoSpec.WIDTH,
                        VideoSpec.HEIGHT,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                    ),
                )
                if (showDates) add(dateOverlay(clip.date, dateStyle))
            }

            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clip.file)))
                .setEffects(Effects(ImmutableList.of(), videoEffects))
                .build()
        }

        val sequence = EditedMediaItemSequence.Builder(*items.toTypedArray()).build()

        // forceAudioTrack inserts silence for any clip that has no audio, which
        // otherwise desynchronises everything downstream of the silent clip.
        val composition = Composition.Builder(sequence)
            .experimentalSetForceAudioTrack(true)
            .build()

        runTransformer(context, onProgress) { transformer ->
            transformer.start(composition, output.absolutePath)
        }
    }

    /**
     * A small caption pinned to the chosen edge and side of the frame.
     *
     * Anchors are in normalised device coordinates: (-1, -1) is the bottom-left
     * corner. The overlay's own anchor point (its corner or edge-midpoint on the
     * aligned side) is pinned to a point slightly inside the frame, which gives an
     * even margin on both axes. See [DateLabelStyle] for the arithmetic.
     */
    private fun dateOverlay(date: LocalDate, style: DateLabelStyle): OverlayEffect {
        // One space each side leaves room for the soft shadow, which is drawn
        // outside the text's own bounds and would otherwise be clipped.
        val label = dateLabelText(" ${date.format(DATE_LABEL_FORMAT)} ", style, DATE_TEXT_SIZE_PX)

        val (overlayX, overlayY) = style.overlayAnchor
        val (frameX, frameY) = style.frameAnchor(DATE_MARGIN_PX, VideoSpec.WIDTH, VideoSpec.HEIGHT)
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(overlayX, overlayY)
            .setBackgroundFrameAnchor(frameX, frameY)
            .build()

        return OverlayEffect(
            ImmutableList.of<TextureOverlay>(
                TextOverlay.createStaticTextOverlay(label, settings),
            ),
        )
    }
}

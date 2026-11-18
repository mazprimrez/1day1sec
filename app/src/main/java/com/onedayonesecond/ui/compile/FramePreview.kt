package com.onedayonesecond.ui.compile

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import com.onedayonesecond.ui.theme.Corners
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onedayonesecond.media.CAPTION_TEXT_SIZE_PX
import com.onedayonesecond.media.DATE_LABEL_FORMAT
import com.onedayonesecond.media.DATE_MARGIN_PX
import com.onedayonesecond.media.DATE_TEXT_SIZE_PX
import com.onedayonesecond.media.DateAlign
import com.onedayonesecond.media.DateEdge
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.media.VideoSpec
import com.onedayonesecond.media.typeface
import java.time.LocalDate

/**
 * A scaled-down frame with the text where it will land on every second of a reel: the
 * caption, the date under it and the place under that, each only if it is switched on.
 * The text size and margin keep the same proportions to the frame as on the real
 * 720x1280 output, so what is seen here is what the reel gets (apart from the font
 * itself being drawn by Compose rather than by the video pipeline).
 *
 * [still] is what the frame holds. Without one it is a bright sky over dark ground,
 * since the white text has to cope with both.
 */
@Composable
fun FramePreview(
    style: DateLabelStyle,
    showDates: Boolean,
    showCaptions: Boolean,
    showPlaces: Boolean,
    modifier: Modifier = Modifier,
    date: LocalDate = LocalDate.now(),
    caption: String = "Your caption",
    still: (@Composable BoxScope.() -> Unit)? = null,
) {
    val scale = PREVIEW_WIDTH.value / VideoSpec.WIDTH

    val position = when (style.edge) {
        DateEdge.BOTTOM -> when (style.align) {
            DateAlign.LEFT -> Alignment.BottomStart
            DateAlign.CENTER -> Alignment.BottomCenter
            DateAlign.RIGHT -> Alignment.BottomEnd
        }
        DateEdge.TOP -> when (style.align) {
            DateAlign.LEFT -> Alignment.TopStart
            DateAlign.CENTER -> Alignment.TopCenter
            DateAlign.RIGHT -> Alignment.TopEnd
        }
    }

    Box(
        modifier = modifier
            .width(PREVIEW_WIDTH)
            .aspectRatio(VideoSpec.WIDTH.toFloat() / VideoSpec.HEIGHT)
            .clip(Corners.Still)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFBFD9F2), Color(0xFFE8E2D0), Color(0xFF3B4A3A)),
                ),
            )
            // The words are read out by the switches beside it; the picture adds nothing to them.
            .clearAndSetSemantics { },
    ) {
        if (still != null) still()

        val family = remember(style.font) { FontFamily(style.font.typeface()) }
        fun textStyle(sizePx: Int) = TextStyle(
            color = Color.White,
            fontSize = (sizePx * scale).sp,
            fontFamily = family,
            // The typeface already carries its own weight.
            fontWeight = FontWeight.Normal,
            // Same soft shadow the reel uses, in place of a background box.
            shadow = Shadow(Color(0xAA000000), Offset(0f, 2f * scale), 6f * scale),
        )

        // The caption sits above the date, both lined up on the chosen side.
        Column(
            modifier = Modifier.align(position).padding((DATE_MARGIN_PX * scale).dp),
            horizontalAlignment = when (style.align) {
                DateAlign.LEFT -> Alignment.Start
                DateAlign.CENTER -> Alignment.CenterHorizontally
                DateAlign.RIGHT -> Alignment.End
            },
        ) {
            if (showCaptions) Text(" $caption ", style = textStyle(CAPTION_TEXT_SIZE_PX), maxLines = 2)
            if (showDates) Text(" ${date.format(DATE_LABEL_FORMAT)} ", style = textStyle(DATE_TEXT_SIZE_PX))
            if (showPlaces) Text(" Your place ", style = textStyle(CAPTION_TEXT_SIZE_PX))
        }
    }
}

/** The preview is this wide, on the settings page as on the Reel tab. */
val PREVIEW_WIDTH = 150.dp

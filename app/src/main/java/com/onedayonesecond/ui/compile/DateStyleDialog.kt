package com.onedayonesecond.ui.compile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.onedayonesecond.media.DATE_LABEL_FORMAT
import com.onedayonesecond.media.DATE_MARGIN_PX
import com.onedayonesecond.media.DATE_TEXT_SIZE_PX
import com.onedayonesecond.media.DateAlign
import com.onedayonesecond.media.DateEdge
import com.onedayonesecond.media.DateFont
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.media.VideoSpec
import com.onedayonesecond.media.typeface
import java.time.LocalDate

/**
 * Lets the user pick the date caption's font and position, with a small preview of
 * a reel frame so the choice can be judged without building a reel.
 *
 * Choices are held in a draft and only applied with "Apply", because applying
 * discards an already-built reel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateStyleDialog(
    current: DateLabelStyle,
    onApply: (DateLabelStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date style") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FramePreview(draft)
                }

                Section("Font") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DateFont.entries.forEach { font ->
                            FilterChip(
                                selected = draft.font == font,
                                onClick = { draft = draft.copy(font = font) },
                                // Each name in its own font, so the list is its own preview.
                                label = {
                                    Text(
                                        font.label,
                                        fontFamily = remember(font) { FontFamily(font.typeface()) },
                                    )
                                },
                            )
                        }
                    }
                }

                Section("Side") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateAlign.entries.forEach { align ->
                            FilterChip(
                                selected = draft.align == align,
                                onClick = { draft = draft.copy(align = align) },
                                label = { Text(align.label) },
                            )
                        }
                    }
                }

                Section("Edge") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateEdge.entries.forEach { edge ->
                            FilterChip(
                                selected = draft.edge == edge,
                                onClick = { draft = draft.copy(edge = edge) },
                                label = { Text(edge.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

/**
 * A scaled-down frame with the caption where it will land. The text size and margin
 * keep the same proportions to the frame as on the real 720x1280 output, so what is
 * seen here is what the reel gets (apart from the font itself being drawn by
 * Compose rather than by the video pipeline).
 */
@Composable
private fun FramePreview(style: DateLabelStyle) {
    val previewWidth = 150.dp
    val scale = previewWidth.value / VideoSpec.WIDTH

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
        modifier = Modifier
            .width(previewWidth)
            .aspectRatio(VideoSpec.WIDTH.toFloat() / VideoSpec.HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            // A bright sky-to-dark-ground gradient: the white caption has to cope with both.
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFBFD9F2), Color(0xFFE8E2D0), Color(0xFF3B4A3A)),
                ),
            )
            .padding((DATE_MARGIN_PX * scale).dp),
        contentAlignment = position,
    ) {
        Text(
            text = " ${LocalDate.now().format(DATE_LABEL_FORMAT)} ",
            style = TextStyle(
                color = Color.White,
                fontSize = (DATE_TEXT_SIZE_PX * scale).sp,
                fontFamily = remember(style.font) { FontFamily(style.font.typeface()) },
                // The typeface already carries its own weight.
                fontWeight = FontWeight.Normal,
                // Same soft shadow the reel uses, in place of a background box.
                shadow = Shadow(Color(0xAA000000), Offset(0f, 2f * scale), 6f * scale),
            ),
        )
    }
}

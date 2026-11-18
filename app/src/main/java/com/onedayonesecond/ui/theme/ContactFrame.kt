package com.onedayonesecond.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.Mood

/** What a day's frame holds. */
enum class FrameKind {
    /** The day has a second: its still fills the frame. */
    Kept,

    /** The day passed without one: a dashed, empty frame that can still be filled. */
    Missed,

    /** The day has not come yet: a faint frame that cannot be tapped. */
    Future,
}

/**
 * One frame of the contact sheet: a day as a small portrait rectangle with its number
 * printed underneath, the way frames are numbered on a strip of film. This is the
 * shape every day takes in the app: on the month sheet, in a week's strip, on the
 * reel's timeline.
 *
 * [label] is the full date and [stateLabel] what is in the frame; together they are
 * all a screen reader says. A mood is always shown as its face, never as a colour.
 * [marked] is a missed day that could be filled from the gallery. [frameHeight] is
 * for strips whose frames have a fixed height; otherwise the frame is 3:4. [small]
 * is for those narrower strips, and shrinks the mood badge to fit.
 */
@Composable
fun ContactFrame(
    kind: FrameKind,
    label: String,
    modifier: Modifier = Modifier,
    number: String? = null,
    stateLabel: String? = null,
    today: Boolean = false,
    selected: Boolean = false,
    mood: Mood? = null,
    marked: Boolean = false,
    frameHeight: Dp? = null,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
    still: @Composable BoxScope.() -> Unit = {},
) {
    val shape = Corners.Frame
    val corner = 3.dp
    val tappable = onClick != null && kind != FrameKind.Future
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val keepStill = reducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !keepStill) 0.96f else 1f,
        animationSpec = tween(160, easing = PressEasing),
        label = "frame press",
    )
    val numberColor = when {
        today || selected -> Palette.Rose
        kind == FrameKind.Kept -> Palette.Ink
        else -> Palette.Muted
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (tappable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick!!,
                    )
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = label
                if (stateLabel != null) stateDescription = stateLabel
                if (selected) this.selected = true
                if (tappable) role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (frameHeight != null) Modifier.height(frameHeight) else Modifier.aspectRatio(3f / 4f))
                .drawBehind {
                    val radius = CornerRadius(corner.toPx())
                    // Selection is an outline standing 1 dp off the frame, so it shows on any still.
                    if (selected) {
                        val out = 2.dp.toPx()
                        drawRoundRect(
                            Palette.Rose,
                            topLeft = Offset(-out, -out),
                            size = Size(size.width + out * 2, size.height + out * 2),
                            cornerRadius = CornerRadius(corner.toPx() + out),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    when {
                        today -> edge(1.dp.toPx(), radius, Palette.Rose, 2.dp.toPx())
                        kind == FrameKind.Missed -> edge(
                            0.5.dp.toPx(),
                            radius,
                            Palette.FrameLine,
                            1.dp.toPx(),
                            PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        )
                        kind == FrameKind.Future -> edge(0.5.dp.toPx(), radius, Palette.Hairline, 1.dp.toPx())
                    }
                },
        ) {
            if (kind == FrameKind.Kept) {
                // Today's still sits inside the rose outline rather than under it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (today) 3.dp else 0.dp)
                        .clip(if (today) RoundedCornerShape(1.dp) else shape)
                        .background(Palette.Blush),
                    content = still,
                )
                if (mood != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(if (today) 5.dp else 3.dp)
                            .size(if (small) 18.dp else 22.dp)
                            .clip(CircleShape)
                            .background(Palette.Paper.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MoodFace(mood, size = if (small) 14.dp else 18.dp)
                    }
                }
            } else if (today) {
                Icon(
                    LineIcons.Plus,
                    contentDescription = null,
                    tint = Palette.Rose,
                    modifier = Modifier.align(Alignment.Center).size(18.dp),
                )
            } else if (marked) {
                Box(Modifier.align(Alignment.Center).size(6.dp).clip(CircleShape).background(Palette.Rose))
            }
        }
        if (number != null) {
            Text(
                number,
                style = AppType.FrameNumber,
                color = numberColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.height(16.dp).padding(top = 1.dp),
            )
        }
    }
}

/** A border drawn just inside the frame's edge, so neighbouring frames keep their gutter. */
private fun DrawScope.edge(
    inset: Float,
    radius: CornerRadius,
    color: Color,
    width: Float,
    dash: PathEffect? = null,
) {
    drawRoundRect(
        color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = radius,
        style = Stroke(width, pathEffect = dash),
    )
}

/**
 * Frames laid out like a contact sheet: [columns] across with a 4 dp gutter, each row
 * with its line of numbers under it. A short last row is left empty at the end.
 */
@Composable
fun ContactSheet(
    count: Int,
    modifier: Modifier = Modifier,
    columns: Int = 6,
    rowGap: Dp = 10.dp,
    frame: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { index -> frame(index, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The curve of the press: quick to start, soft to land. */
private val PressEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** True when the phone has animations switched off, in which case nothing should move. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

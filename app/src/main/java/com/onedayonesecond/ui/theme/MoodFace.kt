package com.onedayonesecond.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.Mood

/**
 * A mood as a line-drawn face, never an emoji: a circle, two eyes and a mouth whose
 * curve is the mood. Decorative on its own; give the thing that holds it a label.
 */
@Composable
fun MoodFace(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = Palette.Rose,
) {
    Canvas(modifier.size(size)) { drawFace(mood, color) }
}

/** The stand-in for a day that has no mood yet: a dashed circle with a plus. */
@Composable
fun MoodPlaceholder(modifier: Modifier = Modifier, size: Dp = 32.dp, color: Color = Palette.Rose) {
    Canvas(modifier.size(size)) {
        val stroke = strokeFor(this.size.minDimension)
        val dash = this.size.minDimension / 9f
        drawCircle(
            color = color,
            radius = this.size.minDimension / 2f - stroke,
            style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        )
        val c = center
        val arm = this.size.minDimension * 0.16f
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke, StrokeCap.Round)
    }
}

/** Line weight grows gently with the face, staying within the 1.4 to 2 dp of the icons. */
private fun DrawScope.strokeFor(sidePx: Float): Float =
    (sidePx / 20f).coerceIn(1.4.dp.toPx(), 2.dp.toPx())

/** Shared with the year-in-pixels picture, which draws faces onto a bitmap legend. */
fun DrawScope.drawFace(mood: Mood, color: Color) {
    val side = size.minDimension
    val stroke = strokeFor(side)
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    fun at(x: Float, y: Float) = Offset(left + side * x, top + side * y)

    drawCircle(color, radius = side / 2f - stroke, center = center, style = Stroke(stroke))

    // Eyes.
    val eyeY = if (mood == Mood.ROUGH) 0.44f else 0.41f
    drawCircle(color, radius = stroke * 0.85f, center = at(0.36f, eyeY))
    drawCircle(color, radius = stroke * 0.85f, center = at(0.64f, eyeY))

    val line = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mouth = Path()
    when (mood) {
        Mood.GREAT -> {
            // An open smile: a level top lip and a deep curve below it.
            mouth.moveTo(at(0.30f, 0.57f).x, at(0.30f, 0.57f).y)
            mouth.lineTo(at(0.70f, 0.57f).x, at(0.70f, 0.57f).y)
            mouth.quadraticTo(at(0.5f, 0.92f).x, at(0.5f, 0.92f).y, at(0.30f, 0.57f).x, at(0.30f, 0.57f).y)
            mouth.close()
        }
        Mood.GOOD -> {
            mouth.moveTo(at(0.33f, 0.60f).x, at(0.33f, 0.60f).y)
            mouth.quadraticTo(at(0.5f, 0.78f).x, at(0.5f, 0.78f).y, at(0.67f, 0.60f).x, at(0.67f, 0.60f).y)
        }
        Mood.NEUTRAL -> {
            mouth.moveTo(at(0.36f, 0.65f).x, at(0.36f, 0.65f).y)
            mouth.lineTo(at(0.64f, 0.65f).x, at(0.64f, 0.65f).y)
        }
        Mood.BAD -> {
            mouth.moveTo(at(0.35f, 0.69f).x, at(0.35f, 0.69f).y)
            mouth.quadraticTo(at(0.5f, 0.58f).x, at(0.5f, 0.58f).y, at(0.65f, 0.69f).x, at(0.65f, 0.69f).y)
        }
        Mood.ROUGH -> {
            mouth.moveTo(at(0.32f, 0.74f).x, at(0.32f, 0.74f).y)
            mouth.quadraticTo(at(0.5f, 0.52f).x, at(0.5f, 0.52f).y, at(0.68f, 0.74f).x, at(0.68f, 0.74f).y)
            // Brows drawn in towards the middle.
            drawLine(color, at(0.27f, 0.27f), at(0.41f, 0.33f), stroke, StrokeCap.Round)
            drawLine(color, at(0.73f, 0.27f), at(0.59f, 0.33f), stroke, StrokeCap.Round)
        }
    }
    drawPath(mouth, color, style = line)
}

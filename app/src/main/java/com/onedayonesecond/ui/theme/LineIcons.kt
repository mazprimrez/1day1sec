package com.onedayonesecond.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icons: thin lines with round ends, drawn on a 24 x 24 grid. Material's
 * own icon sets are filled or heavier, so these are drawn here to match the mood faces.
 */
object LineIcons {

    private fun icon(name: String, width: Float = 1.6f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** A dot: a line too short to see, which the round ends turn into a circle. */
    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y)
        lineTo(x + 0.01f, y)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, c: Float) {
        moveTo(l + c, t)
        lineTo(r - c, t)
        arcTo(c, c, 0f, false, true, r, t + c)
        lineTo(r, b - c)
        arcTo(c, c, 0f, false, true, r - c, b)
        lineTo(l + c, b)
        arcTo(c, c, 0f, false, true, l, b - c)
        lineTo(l, t + c)
        arcTo(c, c, 0f, false, true, l + c, t)
        close()
    }

    val Back = icon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
    val ChevronLeft = icon("ChevronLeft") { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) }
    val ChevronRight = icon("ChevronRight") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
    val ChevronDown = icon("ChevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
    val ChevronUp = icon("ChevronUp") { moveTo(6f, 14.5f); lineTo(12f, 8.5f); lineTo(18f, 14.5f) }
    val Plus = icon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val More = icon("More", width = 2.4f) { dot(5f, 12f); dot(12f, 12f); dot(19f, 12f) }
    val Close = icon("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    /** Days tab: a calendar page. */
    val Days = icon("Days") {
        roundRect(4f, 5.5f, 20f, 20f, 3f)
        moveTo(4f, 10f); lineTo(20f, 10f)
        moveTo(8.5f, 3.5f); lineTo(8.5f, 7f)
        moveTo(15.5f, 3.5f); lineTo(15.5f, 7f)
    }

    /** Recaps tab: a letter. */
    val Recaps = icon("Recaps") {
        roundRect(3.5f, 6f, 20.5f, 18.5f, 2.5f)
        moveTo(4.5f, 7.5f); lineTo(12f, 13.5f); lineTo(19.5f, 7.5f)
    }

    /** Books tab: an open book. */
    val Books = icon("Books") {
        moveTo(12f, 6.5f); curveTo(10f, 5f, 6.5f, 4.8f, 4f, 5.5f)
        lineTo(4f, 17.5f); curveTo(6.5f, 16.8f, 10f, 17f, 12f, 18.5f)
        moveTo(12f, 6.5f); curveTo(14f, 5f, 17.5f, 4.8f, 20f, 5.5f)
        lineTo(20f, 17.5f); curveTo(17.5f, 16.8f, 14f, 17f, 12f, 18.5f)
        moveTo(12f, 6.5f); lineTo(12f, 18.5f)
    }

    /** The barcode on a book's back cover. */
    val Barcode = icon("Barcode") {
        moveTo(4.5f, 6f); lineTo(4.5f, 18f)
        moveTo(7.5f, 6f); lineTo(7.5f, 18f)
        moveTo(10f, 6f); lineTo(10f, 15.5f)
        moveTo(13.5f, 6f); lineTo(13.5f, 15.5f)
        moveTo(16f, 6f); lineTo(16f, 18f)
        moveTo(19.5f, 6f); lineTo(19.5f, 18f)
    }
    val Minus = icon("Minus") { moveTo(5f, 12f); lineTo(19f, 12f) }
    val Check = icon("Check", width = 2f) { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }

    /** Reel tab: a frame with a play triangle. */
    val Reel = icon("Reel") {
        roundRect(4f, 4f, 20f, 20f, 3.5f)
        moveTo(10.5f, 9f); lineTo(15f, 12f); lineTo(10.5f, 15f); close()
    }
    val Video = icon("Video") {
        roundRect(3.5f, 7f, 15f, 17f, 2.5f)
        moveTo(15f, 11f); lineTo(20.5f, 8f); lineTo(20.5f, 16f); lineTo(15f, 13f)
    }
    val Flip = icon("Flip") {
        moveTo(20f, 11f); arcTo(8f, 8f, 0f, false, false, 5.5f, 7.5f)
        moveTo(5f, 4f); lineTo(5.5f, 7.5f); lineTo(9f, 7.5f)
        moveTo(4f, 13f); arcTo(8f, 8f, 0f, false, false, 18.5f, 16.5f)
        moveTo(19f, 20f); lineTo(18.5f, 16.5f); lineTo(15f, 16.5f)
    }
    val Image = icon("Image") {
        roundRect(4f, 4.5f, 20f, 19.5f, 3f)
        circle(9f, 9.5f, 1.4f)
        moveTo(4.5f, 17f); lineTo(9.5f, 12.5f); lineTo(13f, 15.5f); lineTo(15.5f, 13.5f); lineTo(19.5f, 17f)
    }
    val Write = icon("Write") {
        moveTo(4.5f, 19.5f); lineTo(5.5f, 15.5f); lineTo(16f, 5f); lineTo(19f, 8f); lineTo(8.5f, 18.5f); close()
        moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
    }
    val Mic = icon("Mic") {
        roundRect(9f, 3.5f, 15f, 14f, 3f)
        moveTo(5.5f, 11.5f); arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11.5f)
        moveTo(12f, 18f); lineTo(12f, 21f)
    }

    /** Draw with S Pen: a pen tip leaving a line. */
    val Pen = icon("Pen") {
        moveTo(14f, 4f); lineTo(20f, 10f); lineTo(11f, 15f); lineTo(9f, 13f); close()
        moveTo(9.8f, 14.2f); lineTo(6f, 18f)
        moveTo(4f, 20.5f); curveTo(7f, 18.5f, 9f, 22f, 12f, 20f); curveTo(14f, 18.7f, 16f, 20.5f, 19f, 20f)
    }
    val Play = icon("Play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }
    val Pause = icon("Pause", width = 2f) {
        moveTo(8.5f, 6f); lineTo(8.5f, 18f)
        moveTo(15.5f, 6f); lineTo(15.5f, 18f)
    }
    val ArrowUp = icon("ArrowUp") {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(6.5f, 10.5f); lineTo(12f, 5f); lineTo(17.5f, 10.5f)
    }
    val ArrowDown = icon("ArrowDown") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(6.5f, 13.5f); lineTo(12f, 19f); lineTo(17.5f, 13.5f)
    }

    /** "About the same": a level line with an arrow head. */
    val ArrowSame = icon("ArrowSame") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(14f, 7f); lineTo(19f, 12f); lineTo(14f, 17f)
    }
    val Place = icon("Place") {
        moveTo(12f, 21f)
        curveTo(8f, 16.5f, 5.5f, 13.5f, 5.5f, 10f)
        arcTo(6.5f, 6.5f, 0f, false, true, 18.5f, 10f)
        curveTo(18.5f, 13.5f, 16f, 16.5f, 12f, 21f)
        close()
        circle(12f, 10f, 2.2f)
    }
    val Trash = icon("Trash") {
        moveTo(5f, 7f); lineTo(19f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 7f)
    }
}

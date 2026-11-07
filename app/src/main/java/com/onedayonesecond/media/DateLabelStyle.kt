package com.onedayonesecond.media

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * How the date caption on the reel looks and where it sits. Free of Android types
 * so the positioning maths can be unit-tested on a plain JVM; the Android side
 * (typefaces, spans) lives in DateLabelSpans.kt.
 */

/**
 * Text sizes and distance from the frame edge, in pixels of the 720x1280 output frame.
 * The caption is a touch larger than the date it sits above.
 */
const val DATE_TEXT_SIZE_PX = 40
const val CAPTION_TEXT_SIZE_PX = 44
const val DATE_MARGIN_PX = 36

/** Locale-aware, e.g. "Nov 7, 2026" or "7 Nov 2026". Shared by the reel and the style preview. */
val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * Fonts that ship with every Android phone, so nothing has to be bundled. They are
 * named by family; the exact look can differ a little between manufacturers.
 */
enum class DateFont(val label: String, val family: String, val bold: Boolean) {
    CLASSIC("Classic", "sans-serif", bold = true),
    LIGHT("Light", "sans-serif-light", bold = false),
    CONDENSED("Condensed", "sans-serif-condensed", bold = true),
    SERIF("Serif", "serif", bold = true),
    MONO("Mono", "monospace", bold = false),
    HANDWRITING("Handwriting", "cursive", bold = false),
    CASUAL("Casual", "casual", bold = false),
}

/** Left, centre or right along the chosen edge. [anchorX] is -1..1 across the frame. */
enum class DateAlign(val label: String, val anchorX: Float) {
    LEFT("Left", -1f),
    CENTER("Center", 0f),
    RIGHT("Right", 1f),
}

/** Bottom or top of the frame. [anchorY] is -1..1, with -1 the bottom. */
enum class DateEdge(val label: String, val anchorY: Float) {
    BOTTOM("Bottom", -1f),
    TOP("Top", 1f),
}

data class DateLabelStyle(
    val font: DateFont = DateFont.CLASSIC,
    val align: DateAlign = DateAlign.LEFT,
    val edge: DateEdge = DateEdge.BOTTOM,
) {

    /**
     * The point on the caption itself that gets pinned to the frame: its own corner
     * or edge-midpoint on the side it is aligned to, so it grows away from the edge.
     */
    val overlayAnchor: Pair<Float, Float> get() = align.anchorX to edge.anchorY

    /**
     * Where on the frame that point is pinned, in the same -1..1 coordinates, pulled
     * [marginPx] in from the edge so the caption does not touch it. A centred caption
     * has no side margin.
     */
    fun frameAnchor(marginPx: Int, frameWidth: Int, frameHeight: Int): Pair<Float, Float> {
        val sideInset = 2f * marginPx / frameWidth
        val edgeInset = 2f * marginPx / frameHeight
        val x = when (align) {
            DateAlign.LEFT -> -1f + sideInset
            DateAlign.CENTER -> 0f
            DateAlign.RIGHT -> 1f - sideInset
        }
        val y = when (edge) {
            DateEdge.BOTTOM -> -1f + edgeInset
            DateEdge.TOP -> 1f - edgeInset
        }
        return x to y
    }
}

/** Reads a saved name back, falling back if it is missing or from a removed option. */
inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

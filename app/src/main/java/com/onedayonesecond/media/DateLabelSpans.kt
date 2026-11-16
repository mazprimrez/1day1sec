package com.onedayonesecond.media

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import com.onedayonesecond.data.Captions

/** The real typeface for a font choice; also used to preview it on screen. */
fun DateFont.typeface(): Typeface =
    Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)

/**
 * The styled text handed to Transformer's text overlay: the clip's caption, wrapped
 * over a few lines, with the date on the line below it and the day's place under
 * that. Any part can be left out.
 * Returns null when there is nothing to draw.
 *
 * There is deliberately no background box: the text is white with a soft shadow,
 * which keeps it readable on both dark and bright footage without a rectangle
 * sitting on the picture.
 *
 * Transformer draws this with an Android StaticLayout, which is what makes the line
 * breaks and the per-line alignment below work.
 *
 * @param caption the clip's caption, or null / blank for none.
 * @param dateText the formatted date, or null to leave the date out.
 * @param place the day's place, or null / blank for none. It is kept to one line.
 */
internal fun overlayLabelText(
    caption: String?,
    dateText: String?,
    style: DateLabelStyle,
    place: String? = null,
): SpannableString? {
    val captionText = caption?.let(Captions::normalize).orEmpty()
    val placeText = place?.let(Captions::normalize).orEmpty()
    if (captionText.isEmpty() && dateText == null && placeText.isEmpty()) return null

    // The overlay does no wrapping of its own: text wider than the frame would simply
    // be cut off. So the caption is broken into lines here, measured in the real font.
    val measuring = TextPaint().apply {
        typeface = style.font.typeface()
        textSize = CAPTION_TEXT_SIZE_PX.toFloat()
    }
    val maxWidth = VideoSpec.WIDTH - 2f * DATE_MARGIN_PX - measuring.measureText(PAD + PAD)
    val captionLines = Captions.wrap(captionText, maxWidth) { measuring.measureText(it) }

    // One space either side of each line leaves room for the soft shadow, which is
    // drawn outside the text's own bounds and would otherwise be clipped.
    val lines = buildList {
        captionLines.forEach { add("$PAD$it$PAD" to CAPTION_TEXT_SIZE_PX) }
        if (dateText != null) add("$PAD$dateText$PAD" to DATE_TEXT_SIZE_PX)
        // One line only: a place is a label under the date, not a second caption.
        Captions.wrap(placeText, maxWidth, maxLines = 1) { measuring.measureText(it) }
            .forEach { add("$PAD$it$PAD" to CAPTION_TEXT_SIZE_PX) }
    }

    val label = SpannableString(lines.joinToString("\n") { it.first })
    val all = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    var start = 0
    for ((text, size) in lines) {
        label.setSpan(AbsoluteSizeSpan(size, false), start, start + text.length, all)
        start += text.length + 1 // the line break
    }

    label.setSpan(ForegroundColorSpan(Color.WHITE), 0, label.length, all)
    // The typeface is created with its weight already applied (see DateFont.bold),
    // so no separate bold span is added on top of it.
    label.setSpan(TypefaceStyleSpan(style.font.typeface()), 0, label.length, all)
    label.setSpan(ShadowStyleSpan(SHADOW_RADIUS_PX, 0f, SHADOW_OFFSET_Y_PX, SHADOW_COLOR), 0, label.length, all)
    // Where the lines sit relative to each other. Without this a shorter line would
    // hug the left edge of the block even when the block is centred or right-aligned.
    label.setSpan(AlignmentSpan.Standard(style.align.layoutAlignment()), 0, label.length, all)
    return label
}

private fun DateAlign.layoutAlignment(): Layout.Alignment = when (this) {
    DateAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
    DateAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
    DateAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
}

/** Android's own TypefaceSpan only takes a Typeface object from API 28; minSdk here is 26. */
private class TypefaceStyleSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = typeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}

/** Android has no shadow span, but any span can set the paint's shadow layer. */
private class ShadowStyleSpan(
    private val radius: Float,
    private val dx: Float,
    private val dy: Float,
    private val color: Int,
) : CharacterStyle() {
    override fun updateDrawState(paint: TextPaint) {
        paint.setShadowLayer(radius, dx, dy, color)
    }
}

private const val PAD = " "

// Sized for the 720x1280 frame. The caption is smaller than the date.
private const val SHADOW_RADIUS_PX = 6f
private const val SHADOW_OFFSET_Y_PX = 2f
private val SHADOW_COLOR = Color.argb(170, 0, 0, 0)

package com.onedayonesecond.media

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan

/** The real typeface for a font choice; also used to preview it on screen. */
fun DateFont.typeface(): Typeface =
    Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)

/**
 * The styled caption handed to Transformer's text overlay.
 *
 * There is deliberately no background box: the caption is white with a soft
 * shadow, which keeps it readable on both dark and bright footage without a
 * rectangle sitting on the picture.
 */
internal fun dateLabelText(text: String, style: DateLabelStyle, textSizePx: Int): SpannableString {
    val label = SpannableString(text)
    val all = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    label.setSpan(AbsoluteSizeSpan(textSizePx, false), 0, label.length, all)
    label.setSpan(ForegroundColorSpan(Color.WHITE), 0, label.length, all)
    // The typeface is created with its weight already applied (see DateFont.bold),
    // so no separate bold span is added on top of it.
    label.setSpan(TypefaceStyleSpan(style.font.typeface()), 0, label.length, all)
    label.setSpan(ShadowStyleSpan(SHADOW_RADIUS_PX, 0f, SHADOW_OFFSET_Y_PX, SHADOW_COLOR), 0, label.length, all)
    return label
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

// Sized for the 40px caption on the 720x1280 frame.
private const val SHADOW_RADIUS_PX = 6f
private const val SHADOW_OFFSET_Y_PX = 2f
private val SHADOW_COLOR = Color.argb(170, 0, 0, 0)

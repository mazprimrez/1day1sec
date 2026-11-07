package com.onedayonesecond.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateLabelStyleTest {

    private val w = 720
    private val h = 1280
    private val margin = 36
    private val delta = 0.0001f

    @Test
    fun `the default is bottom left, as before`() {
        val style = DateLabelStyle()
        assertEquals(DateAlign.LEFT, style.align)
        assertEquals(DateEdge.BOTTOM, style.edge)

        val (x, y) = style.frameAnchor(margin, w, h)
        assertEquals(-0.9f, x, delta)
        assertEquals(-1f + 2f * margin / h, y, delta)
        assertEquals(-1f to -1f, style.overlayAnchor)
    }

    @Test
    fun `right aligned mirrors left aligned`() {
        val left = DateLabelStyle(align = DateAlign.LEFT).frameAnchor(margin, w, h)
        val right = DateLabelStyle(align = DateAlign.RIGHT).frameAnchor(margin, w, h)
        assertEquals(-left.first, right.first, delta)
        assertEquals(left.second, right.second, delta)
        assertEquals(1f, DateLabelStyle(align = DateAlign.RIGHT).overlayAnchor.first, delta)
    }

    @Test
    fun `centred has no side margin`() {
        val style = DateLabelStyle(align = DateAlign.CENTER)
        assertEquals(0f, style.frameAnchor(margin, w, h).first, delta)
        assertEquals(0f, style.overlayAnchor.first, delta)
    }

    @Test
    fun `top mirrors bottom`() {
        val bottom = DateLabelStyle(edge = DateEdge.BOTTOM).frameAnchor(margin, w, h)
        val top = DateLabelStyle(edge = DateEdge.TOP).frameAnchor(margin, w, h)
        assertEquals(-bottom.second, top.second, delta)
        assertEquals(1f, DateLabelStyle(edge = DateEdge.TOP).overlayAnchor.second, delta)
    }

    @Test
    fun `every position keeps the caption inside the frame`() {
        for (align in DateAlign.entries) for (edge in DateEdge.entries) {
            val (x, y) = DateLabelStyle(align = align, edge = edge).frameAnchor(margin, w, h)
            assertTrue("$align/$edge x=$x", x in -1f..1f)
            assertTrue("$align/$edge y=$y", y in -1f..1f)
        }
    }

    @Test
    fun `the same margin looks equal in pixels on both axes`() {
        // 1 unit of x is w/2 pixels and 1 unit of y is h/2 pixels, so the inset in
        // pixels should be the margin for both.
        val (x, y) = DateLabelStyle().frameAnchor(margin, w, h)
        assertEquals(margin.toFloat(), (x + 1f) * w / 2f, 0.01f)
        assertEquals(margin.toFloat(), (y + 1f) * h / 2f, 0.01f)
    }

    @Test
    fun `the caption is smaller than the date it sits above`() {
        assertTrue(CAPTION_TEXT_SIZE_PX < DATE_TEXT_SIZE_PX)
    }

    @Test
    fun `font names and families are unique and non-empty`() {
        assertEquals(DateFont.entries.size, DateFont.entries.map { it.label }.toSet().size)
        assertEquals(DateFont.entries.size, DateFont.entries.map { it.family }.toSet().size)
        assertTrue(DateFont.entries.all { it.label.isNotBlank() && it.family.isNotBlank() })
    }

    @Test
    fun `a saved name reads back and a bad one falls back`() {
        assertEquals(DateFont.SERIF, enumOrDefault("SERIF", DateFont.CLASSIC))
        assertEquals(DateFont.CLASSIC, enumOrDefault("COMIC_SANS", DateFont.CLASSIC))
        assertEquals(DateFont.CLASSIC, enumOrDefault(null, DateFont.CLASSIC))
        assertEquals(DateAlign.CENTER, enumOrDefault("CENTER", DateAlign.LEFT))
        assertEquals(DateEdge.TOP, enumOrDefault("TOP", DateEdge.BOTTOM))
    }
}

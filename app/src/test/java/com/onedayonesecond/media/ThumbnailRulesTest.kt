package com.onedayonesecond.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailRulesTest {

    @Test
    fun `a tall frame is cropped to a square from its middle`() {
        assertEquals(
            ThumbnailRules.Square(x = 0, y = 280, side = 720),
            ThumbnailRules.centerSquare(720, 1280),
        )
    }

    @Test
    fun `a wide frame is cropped from the sides`() {
        assertEquals(
            ThumbnailRules.Square(x = 280, y = 0, side = 720),
            ThumbnailRules.centerSquare(1280, 720),
        )
    }

    @Test
    fun `a square frame is kept whole`() {
        assertEquals(ThumbnailRules.Square(0, 0, 500), ThumbnailRules.centerSquare(500, 500))
    }

    @Test
    fun `the crop always stays inside the frame`() {
        for ((w, h) in listOf(720 to 1280, 1280 to 720, 1081 to 1921, 3 to 1000, 1000 to 3, 1 to 1)) {
            val s = ThumbnailRules.centerSquare(w, h)
            assertTrue("$w x $h -> $s", s.x >= 0 && s.y >= 0 && s.side >= 1)
            assertTrue("$w x $h -> $s", s.x + s.side <= w && s.y + s.side <= h)
        }
    }

    @Test
    fun `an empty frame is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ThumbnailRules.centerSquare(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { ThumbnailRules.centerSquare(100, 0) }
    }

    @Test
    fun `the frame shown is the middle of the clip`() {
        assertEquals(500_000L, ThumbnailRules.frameTimeUs(1000))
        assertEquals(250_000L, ThumbnailRules.frameTimeUs(500))
    }

    @Test
    fun `a missing duration falls back to the first frame`() {
        assertEquals(0L, ThumbnailRules.frameTimeUs(0))
        assertEquals(0L, ThumbnailRules.frameTimeUs(-1))
    }

    @Test
    fun `retaking a day gives a different key even at the same path`() {
        val path = "/data/clips/2026-11-07.mp4"
        assertEquals(
            ThumbnailRules.cacheKey(path, 1000),
            ThumbnailRules.cacheKey(path, 1000),
        )
        assertNotEquals(
            ThumbnailRules.cacheKey(path, 1000),
            ThumbnailRules.cacheKey(path, 2000),
        )
    }

    @Test
    fun `different days never share a key`() {
        assertNotEquals(
            ThumbnailRules.cacheKey("/clips/2026-11-07.mp4", 1000),
            ThumbnailRules.cacheKey("/clips/2026-11-08.mp4", 1000),
        )
    }
}

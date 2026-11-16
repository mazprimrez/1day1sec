package com.onedayonesecond.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailRulesTest {

    @Test
    fun `a stored clip is already portrait and is kept whole`() {
        assertEquals(
            ThumbnailRules.Crop(x = 0, y = 0, width = 720, height = 1280),
            ThumbnailRules.centerPortrait(720, 1280),
        )
    }

    @Test
    fun `a wide frame is cropped from the sides`() {
        assertEquals(
            ThumbnailRules.Crop(x = 437, y = 0, width = 405, height = 720),
            ThumbnailRules.centerPortrait(1280, 720),
        )
    }

    @Test
    fun `a frame taller than 9 to 16 loses its top and bottom`() {
        assertEquals(
            ThumbnailRules.Crop(x = 0, y = 360, width = 720, height = 1280),
            ThumbnailRules.centerPortrait(720, 2000),
        )
    }

    @Test
    fun `the crop always stays inside the frame`() {
        for ((w, h) in listOf(720 to 1280, 1280 to 720, 1081 to 1921, 3 to 1000, 1000 to 3, 1 to 1, 500 to 500)) {
            val c = ThumbnailRules.centerPortrait(w, h)
            assertTrue("$w x $h -> $c", c.x >= 0 && c.y >= 0 && c.width >= 1 && c.height >= 1)
            assertTrue("$w x $h -> $c", c.x + c.width <= w && c.y + c.height <= h)
        }
    }

    @Test
    fun `an empty frame is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ThumbnailRules.centerPortrait(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { ThumbnailRules.centerPortrait(100, 0) }
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

package com.onedayonesecond.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GalleryRulesTest {

    @Test
    fun `capture time wins when it is known`() {
        assertEquals(1_700_000_000_000L, GalleryRules.takenAtMs(1_700_000_000_000L, 5, 6))
    }

    @Test
    fun `falls back to modified time then added time`() {
        assertEquals(5_000L, GalleryRules.takenAtMs(0, 5, 6))
        assertEquals(6_000L, GalleryRules.takenAtMs(0, 0, 6))
    }

    @Test
    fun `no usable timestamp means no date`() {
        assertNull(GalleryRules.takenAtMs(0, 0, 0))
        assertNull(GalleryRules.takenAtMs(-1, -1, -1))
    }

    @Test
    fun `the same instant is a different day in a different time zone`() {
        val instant = Instant.parse("2026-11-07T17:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 7), GalleryRules.dateOf(instant, ZoneId.of("UTC")))
        // 17:30 UTC is already half past midnight on the 8th in Jakarta (UTC+7).
        assertEquals(LocalDate.of(2026, 11, 8), GalleryRules.dateOf(instant, ZoneId.of("Asia/Jakarta")))
        // ...and still the morning of the 7th in Los Angeles.
        assertEquals(LocalDate.of(2026, 11, 7), GalleryRules.dateOf(instant, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun `an ordinary short video is a candidate`() {
        assertTrue(GalleryRules.isCandidate("VID_2026.mp4", 12_000, 20_000_000))
        assertTrue(GalleryRules.isCandidate(null, 12_000, 20_000_000))
    }

    @Test
    fun `reels exported by this app are not source material`() {
        assertFalse(GalleryRules.isCandidate("1day1sec-20261107.mp4", 12_000, 20_000_000))
    }

    @Test
    fun `videos under a second or over five minutes are rejected`() {
        assertFalse(GalleryRules.isCandidate("a.mp4", 999, 1_000))
        assertTrue(GalleryRules.isCandidate("a.mp4", 1_000, 1_000))
        assertTrue(GalleryRules.isCandidate("a.mp4", 5 * 60_000L, 1_000))
        assertFalse(GalleryRules.isCandidate("a.mp4", 5 * 60_000L + 1, 1_000))
    }

    @Test
    fun `videos over one gigabyte are rejected`() {
        assertTrue(GalleryRules.isCandidate("a.mp4", 10_000, 1L shl 30))
        assertFalse(GalleryRules.isCandidate("a.mp4", 10_000, (1L shl 30) + 1))
    }
}

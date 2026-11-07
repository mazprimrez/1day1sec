package com.onedayonesecond.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CaptionSyncTest {

    private val d1 = LocalDate.of(2026, 11, 1)
    private val d2 = LocalDate.of(2026, 11, 2)
    private val d3 = LocalDate.of(2026, 11, 3)
    private val d4 = LocalDate.of(2026, 11, 4)

    private fun merge(
        remote: Map<LocalDate, String> = emptyMap(),
        local: Map<LocalDate, String> = emptyMap(),
        movedAway: Set<LocalDate> = emptySet(),
    ) = CaptionSync.merge(remote, local, movedAway)

    @Test
    fun `nothing anywhere gives nothing`() {
        assertEquals(emptyMap<LocalDate, String>(), merge())
    }

    @Test
    fun `a new caption on the phone is added`() {
        assertEquals(mapOf(d1 to "new"), merge(local = mapOf(d1 to "new")))
    }

    @Test
    fun `a changed caption on the phone wins over Drive`() {
        assertEquals(
            mapOf(d1 to "edited"),
            merge(remote = mapOf(d1 to "old"), local = mapOf(d1 to "edited")),
        )
    }

    @Test
    fun `a caption cleared on the phone is dropped from Drive`() {
        assertEquals(
            emptyMap<LocalDate, String>(),
            merge(remote = mapOf(d1 to "old"), local = mapOf(d1 to "")),
        )
    }

    @Test
    fun `a clip without a caption stays without one`() {
        assertEquals(emptyMap<LocalDate, String>(), merge(local = mapOf(d1 to "", d2 to "")))
    }

    @Test
    fun `captions of days the phone has no clip for are kept`() {
        // A new phone with one clip must not wipe the year of captions in Drive.
        val remote = mapOf(d1 to "a", d2 to "b", d3 to "c")
        assertEquals(remote + (d4 to "mine"), merge(remote = remote, local = mapOf(d4 to "mine")))
    }

    @Test
    fun `a phone with no clips at all changes nothing in Drive`() {
        val remote = mapOf(d1 to "a", d2 to "b")
        assertEquals(remote, merge(remote = remote))
    }

    @Test
    fun `a moved clip's old day loses its caption`() {
        // Clip moved d1 -> d2: d2 now has it locally, d1 has no clip any more.
        assertEquals(
            mapOf(d2 to "the caption"),
            merge(
                remote = mapOf(d1 to "the caption"),
                local = mapOf(d2 to "the caption"),
                movedAway = setOf(d1),
            ),
        )
    }

    @Test
    fun `a day moved away from but filled again takes the new clip's caption`() {
        assertEquals(
            mapOf(d1 to "new clip"),
            merge(remote = mapOf(d1 to "old clip"), local = mapOf(d1 to "new clip"), movedAway = setOf(d1)),
        )
    }

    @Test
    fun `a day moved away from and filled again without a caption has none`() {
        assertEquals(
            emptyMap<LocalDate, String>(),
            merge(remote = mapOf(d1 to "old clip"), local = mapOf(d1 to ""), movedAway = setOf(d1)),
        )
    }

    @Test
    fun `moving away from a day only affects that day`() {
        assertEquals(
            mapOf(d3 to "elsewhere"),
            merge(remote = mapOf(d1 to "gone", d3 to "elsewhere"), movedAway = setOf(d1)),
        )
    }

    @Test
    fun `blank entries in Drive are not carried forward`() {
        assertEquals(emptyMap<LocalDate, String>(), merge(remote = mapOf(d1 to "  ", d2 to "")))
    }

    @Test
    fun `merging twice gives the same answer`() {
        val remote = mapOf(d1 to "a", d2 to "b")
        val local = mapOf(d2 to "b2", d3 to "c")
        val once = merge(remote, local)
        assertEquals(once, merge(once, local))
    }
}

package com.onedayonesecond.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionsTest {

    /** Every character is 10 units wide, so a line of width 100 holds 10 characters. */
    private val ten: (String) -> Float = { it.length * 10f }

    private fun wrap(text: String, width: Float = 100f, maxLines: Int = 3) =
        Captions.wrap(text, width, maxLines, ten)

    // --- normalize ----------------------------------------------------------------

    @Test
    fun `a plain caption is kept as typed`() {
        assertEquals("Lunch with Sam", Captions.normalize("Lunch with Sam"))
    }

    @Test
    fun `the ends are trimmed`() {
        assertEquals("hello", Captions.normalize("   hello \n"))
    }

    @Test
    fun `line breaks and repeated spaces become single spaces`() {
        assertEquals("one two three", Captions.normalize("one\n\ntwo   three"))
        assertEquals("a b", Captions.normalize("a\tb"))
    }

    @Test
    fun `blank means no caption`() {
        assertEquals("", Captions.normalize(""))
        assertEquals("", Captions.normalize("   \n\t "))
    }

    @Test
    fun `an overlong caption is cut to the limit without a dangling space`() {
        val raw = "word ".repeat(40)
        val result = Captions.normalize(raw)
        assertTrue(result.length <= Captions.MAX_LENGTH)
        assertEquals(result, result.trimEnd())
    }

    @Test
    fun `text just under the limit is not touched`() {
        val exact = "x".repeat(Captions.MAX_LENGTH)
        assertEquals(exact, Captions.normalize(exact))
        assertEquals(Captions.MAX_LENGTH, Captions.normalize(exact + "yyy").length)
    }

    @Test
    fun `non-latin text and emoji are kept`() {
        assertEquals("ありがとう 🙂", Captions.normalize("ありがとう  🙂"))
    }

    @Test
    fun `cutting to the limit never leaves half an emoji`() {
        val result = Captions.normalize("x".repeat(Captions.MAX_LENGTH - 1) + "🙂🙂")
        assertEquals("x".repeat(Captions.MAX_LENGTH - 1), result)
        assertTrue(result.none { it.isSurrogate() })
    }

    // --- wrap ---------------------------------------------------------------------

    @Test
    fun `short text stays on one line`() {
        assertEquals(listOf("hello"), wrap("hello"))
    }

    @Test
    fun `blank text has no lines`() {
        assertEquals(emptyList<String>(), wrap(""))
        assertEquals(emptyList<String>(), wrap("   "))
    }

    @Test
    fun `lines break between words`() {
        assertEquals(listOf("one two", "three four"), wrap("one two three four"))
    }

    @Test
    fun `no line is wider than the limit`() {
        val lines = wrap("the quick brown fox jumps over", maxLines = 10)
        assertTrue(lines.all { ten(it) <= 100f })
        assertEquals("the quick brown fox jumps over", lines.joinToString(" "))
    }

    @Test
    fun `a word wider than a line is split instead of overflowing`() {
        val lines = wrap("abcdefghijklmnopqrstuvwxyz", maxLines = 10)
        assertEquals(listOf("abcdefghij", "klmnopqrst", "uvwxyz"), lines)
    }

    @Test
    fun `splitting a long word never cuts an emoji in half`() {
        // Each emoji is two characters, so 20 units wide here. A width of 95 makes the
        // plain cut land at 9 characters, in the middle of the fifth emoji.
        val lines = Captions.wrap("🙂".repeat(12), 95f, 10, ten)

        assertEquals("🙂".repeat(12), lines.joinToString(""))
        for (line in lines) {
            line.forEachIndexed { i, c ->
                if (c.isHighSurrogate()) {
                    assertTrue("half an emoji at the end of '$line'", i + 1 < line.length && line[i + 1].isLowSurrogate())
                }
                if (c.isLowSurrogate()) {
                    assertTrue("half an emoji at the start of '$line'", i > 0 && line[i - 1].isHighSurrogate())
                }
            }
            assertTrue(ten(line) <= 95f)
        }
    }

    @Test
    fun `a long word after short ones starts on its own line`() {
        val lines = wrap("hi abcdefghijklmno", maxLines = 10)
        assertEquals(listOf("hi", "abcdefghij", "klmno"), lines)
    }

    @Test
    fun `too many lines end with an ellipsis and still fit`() {
        val lines = wrap("aaa bbb ccc ddd eee fff ggg hhh iii jjj", maxLines = 2)
        assertEquals(2, lines.size)
        assertTrue(lines.last().endsWith("…"))
        assertTrue(lines.all { ten(it) <= 100f })
    }

    @Test
    fun `text that fits the line count is not shortened`() {
        val lines = wrap("aaa bbb ccc ddd", maxLines = 2)
        assertEquals(listOf("aaa bbb", "ccc ddd"), lines)
        assertTrue(lines.none { it.endsWith("…") })
    }

    @Test
    fun `one line only still works`() {
        val lines = wrap("aaa bbb ccc ddd", maxLines = 1)
        assertEquals(1, lines.size)
        assertTrue(lines.single().endsWith("…"))
        assertTrue(ten(lines.single()) <= 100f)
    }

    @Test
    fun `the default caption length always fits in the default lines`() {
        // Every allowed caption, worst case of short words, at the real-world ratio of
        // roughly 29 characters per line.
        val width = 29 * 10f
        val longest = Captions.normalize("ab ".repeat(60))
        val lines = Captions.wrap(longest, width, measure = ten)
        assertTrue(lines.size <= Captions.MAX_LINES)
        assertTrue(lines.all { ten(it) <= width })
    }

    @Test
    fun `at least one line is required`() {
        assertThrows(IllegalArgumentException::class.java) { wrap("x", maxLines = 0) }
    }
}

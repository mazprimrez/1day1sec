package com.onedayonesecond.data

/**
 * Rules for the caption a user can attach to a day's clip. Free of Android types so
 * they can be unit-tested on a plain JVM; measuring real text is passed in.
 */
object Captions {

    /** Long enough for a sentence, short enough to stay readable in a one-second shot. */
    const val MAX_LENGTH = 80

    /** A caption that would need more lines than this is cut short with an ellipsis. */
    const val MAX_LINES = 3

    private const val ELLIPSIS = "…"

    /**
     * What gets stored for whatever was typed: line breaks and runs of spaces become
     * single spaces (wrapping is decided when the reel is drawn, not by the user's
     * Enter key), the ends are trimmed, and it is cut to [MAX_LENGTH]. Blank in,
     * empty out, which means "no caption".
     */
    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).dropSplitEmoji().trimEnd()

    /**
     * Breaks [text] into lines no wider than [maxWidth], preferring to break between
     * words. A single word wider than a line is split inside the word instead of
     * running off the edge. If more than [maxLines] lines are needed the last one ends
     * with an ellipsis.
     *
     * @param measure the width of a piece of text as it will be drawn, in the same
     * unit as [maxWidth]. Passed in so this can be tested without a screen.
     */
    fun wrap(
        text: String,
        maxWidth: Float,
        maxLines: Int = MAX_LINES,
        measure: (String) -> Float,
    ): List<String> {
        require(maxLines >= 1) { "Need at least one line" }
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var current = ""

        fun flush() {
            if (current.isNotEmpty()) lines += current
            current = ""
        }

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
                continue
            }
            flush()
            // The word does not fit even on a line of its own: split it by characters.
            var rest = word
            while (measure(rest) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && measure(rest.take(cut)) > maxWidth) cut--
                // Never cut between the two halves of an emoji or other 2-unit character.
                if (cut > 1 && rest[cut - 1].isHighSurrogate()) cut--
                lines += rest.take(cut)
                rest = rest.drop(cut)
            }
            current = rest
        }
        flush()

        if (lines.size <= maxLines) return lines
        return lines.take(maxLines - 1) + fitWithEllipsis(lines.drop(maxLines - 1).joinToString(" "), maxWidth, measure)
    }

    /** Drops a final half of a 2-unit character (an emoji) that a cut left dangling. */
    private fun String.dropSplitEmoji(): String =
        if (isNotEmpty() && last().isHighSurrogate()) dropLast(1) else this

    /** [text] shortened, if need be, so that it and a trailing ellipsis fit in [maxWidth]. */
    private fun fitWithEllipsis(text: String, maxWidth: Float, measure: (String) -> Float): String {
        var shown = text
        while (shown.isNotEmpty() && measure(shown + ELLIPSIS) > maxWidth) shown = shown.dropLast(1)
        return shown.trimEnd() + ELLIPSIS
    }
}

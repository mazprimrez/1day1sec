package com.onedayonesecond.data

import java.text.Normalizer
import java.time.LocalDate

/**
 * The small rules of the reading log: where the bookmark goes, what a book is called
 * in the backup, when a summary can be written and when it is out of date. Free of
 * Android types so they can be unit-tested on a plain JVM.
 */
object BookRules {

    /** A summary is written from at least this many thoughts with words. */
    const val MIN_THOUGHTS_FOR_SUMMARY = 3

    /** The longest book the page field accepts when the edition's page count is not known. */
    const val MAX_PAGE = 9999

    /** How many earlier thoughts the book page shows before "Show earlier thoughts". */
    const val THOUGHTS_SHOWN = 5

    /**
     * What a book is called in the Drive backup: the day it was started, its title in
     * plain letters, and a short random [suffix] so two books with one title started
     * on the same day stay apart. "2026-11-02-laut-bercerita-7k2q".
     */
    fun backupKey(startedOn: LocalDate, title: String, suffix: String): String =
        "$startedOn-${slug(title)}-$suffix"

    /** [title] in lowercase letters and digits joined by hyphens, at most 40 characters. */
    fun slug(title: String): String {
        val plain = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return plain.take(MAX_SLUG).trimEnd('-').ifEmpty { "book" }
    }

    /** The last page a thought can be on: the edition's last page, when it is known. */
    fun lastPage(book: Book): Int = book.totalPages?.takeIf { it > 0 } ?: MAX_PAGE

    /** The page "Log a thought" starts on: the one after the bookmark, never past the last page. */
    fun nextPage(book: Book): Int = (book.currentPage + 1).coerceIn(1, lastPage(book))

    /** A page typed or stepped to, kept between the first and last page. */
    fun clampPage(book: Book, page: Int): Int = page.coerceIn(1, lastPage(book))

    /** True if saving a thought on [page] moves the bookmark. Earlier pages never do. */
    fun movesBookmark(book: Book, page: Int): Boolean = page > book.currentPage

    /**
     * Where the bookmark belongs once a logged thought's page is corrected from
     * [oldPage] to [newPage]. [otherPages] are the pages of the book's other thoughts.
     *
     * A later page moves the bookmark forward, as logging does. A thought moved back
     * takes the bookmark back with it only when the bookmark is where that thought put
     * it: a page typed as 300 instead of 30 should not leave the book at page 300. It
     * then rests on the furthest page thought about. A finished book stays finished.
     */
    fun bookmarkAfterPageChange(book: Book, oldPage: Int, newPage: Int, otherPages: List<Int>): Int = when {
        newPage > book.currentPage -> newPage
        book.status == BookStatus.READING && book.currentPage == oldPage && newPage < oldPage ->
            maxOf(newPage, otherPages.maxOrNull() ?: 0)
        else -> book.currentPage
    }

    /** How far through the book the bookmark is, from 0 to 1. Null when the page count is not known. */
    fun progress(book: Book): Float? {
        val total = book.totalPages?.takeIf { it > 0 } ?: return null
        return (book.currentPage.toFloat() / total).coerceIn(0f, 1f)
    }

    /** "Page 214 of 379", or "Page 214" when the page count is not known, or null before the first page. */
    fun pageLine(book: Book): String? {
        val total = book.totalPages?.takeIf { it > 0 }
        return when {
            book.status == BookStatus.FINISHED && total != null -> "$total pages"
            book.currentPage <= 0 && total != null -> "Not started. $total pages"
            book.currentPage <= 0 -> null
            total != null -> "Page ${book.currentPage} of $total"
            else -> "Page ${book.currentPage}"
        }
    }

    /** The thoughts a summary is written from: those with words. Photos alone say nothing yet. */
    fun withWords(thoughts: List<BookThought>): List<BookThought> = thoughts.filter { it.hasWords }

    fun canSummarise(thoughts: List<BookThought>): Boolean = withWords(thoughts).size >= MIN_THOUGHTS_FOR_SUMMARY

    /** How many more thoughts with words a summary needs. Zero when there are enough. */
    fun thoughtsStillNeeded(thoughts: List<BookThought>): Int =
        (MIN_THOUGHTS_FOR_SUMMARY - withWords(thoughts).size).coerceAtLeast(0)

    /**
     * True if [summary] was written from the thoughts the book has now. A new thought
     * makes it stale; so does a deleted one, whose words it may still be quoting.
     */
    fun isFresh(summary: BookSummary?, thoughts: List<BookThought>): Boolean =
        summary != null && summary.thoughtCount == withWords(thoughts).size

    private const val MAX_SLUG = 40
}

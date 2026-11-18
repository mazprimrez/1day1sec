package com.onedayonesecond.books

/**
 * ISBNs, as typed into the search field or read from a barcode. Free of Android
 * types so it can be unit-tested on a plain JVM.
 */
object Isbn {

    /** [text] without the spaces and hyphens ISBNs are printed with, in capitals. */
    fun clean(text: String): String = text.filter { !it.isWhitespace() && it != '-' }.uppercase()

    /** True for thirteen digits that start with 978 or 979 and whose last digit checks out. */
    fun isIsbn13(text: String): Boolean {
        if (text.length != 13 || !text.all { it.isDigit() }) return false
        if (!text.startsWith("978") && !text.startsWith("979")) return false
        return checkDigit13(text.take(12)) == text.last()
    }

    /** True for an older ten-character ISBN, whose last character may be X. */
    fun isIsbn10(text: String): Boolean {
        if (text.length != 10 || !text.take(9).all { it.isDigit() }) return false
        val last = text.last()
        if (!last.isDigit() && last != 'X') return false
        val sum = text.mapIndexed { index, c -> (10 - index) * if (c == 'X') 10 else c.digitToInt() }.sum()
        return sum % 11 == 0
    }

    /** The thirteen-digit form of a valid ISBN of either length, or null. */
    fun normalize(text: String): String? {
        val isbn = clean(text)
        return when {
            isIsbn13(isbn) -> isbn
            isIsbn10(isbn) -> "978${isbn.take(9)}".let { it + checkDigit13(it) }
            else -> null
        }
    }

    /**
     * The ISBN on a book's back cover, from what the scanner read, or null. Other
     * barcodes (a price sticker, a shop's own label) are not ISBNs and are ignored, so
     * the scanner keeps looking instead of searching for nonsense.
     */
    fun fromBarcode(raw: String?): String? = raw?.let(::clean)?.takeIf(::isIsbn13)

    private fun checkDigit13(first12: String): Char {
        val sum = first12.mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum()
        return ((10 - sum % 10) % 10).digitToChar()
    }
}

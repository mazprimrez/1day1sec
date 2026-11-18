package com.onedayonesecond.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {

    @Test
    fun `a thirteen digit ISBN with the right check digit is an ISBN`() {
        assertTrue(Isbn.isIsbn13("9780306406157"))
        assertTrue(Isbn.isIsbn13("9791092148008"))
    }

    @Test
    fun `a wrong check digit, or thirteen digits that are not a book's, is not an ISBN`() {
        assertFalse(Isbn.isIsbn13("9780306406158"))
        // A grocery barcode: thirteen digits, but not in the range books are given.
        assertFalse(Isbn.isIsbn13("8991234567890"))
        assertFalse(Isbn.isIsbn13("978030640615"))
    }

    @Test
    fun `an older ten character ISBN is recognised, with X as its last character`() {
        assertTrue(Isbn.isIsbn10("0306406152"))
        assertTrue(Isbn.isIsbn10("080442957X"))
        assertFalse(Isbn.isIsbn10("0306406153"))
        assertFalse(Isbn.isIsbn10("08044X9570"))
    }

    @Test
    fun `an ISBN typed with hyphens and spaces is read as the same ISBN`() {
        assertEquals("9780306406157", Isbn.normalize("978-0-306-40615-7"))
        assertEquals("9780306406157", Isbn.normalize(" 978 0306406157 "))
    }

    @Test
    fun `a ten character ISBN becomes its thirteen digit form`() {
        assertEquals("9780306406157", Isbn.normalize("0-306-40615-2"))
        assertEquals("9780804429573", Isbn.normalize("080442957x"))
    }

    @Test
    fun `words and numbers that are not an ISBN are left to be searched as words`() {
        assertNull(Isbn.normalize("Laut Bercerita"))
        assertNull(Isbn.normalize("1984"))
        assertNull(Isbn.normalize(""))
    }

    @Test
    fun `the scanner takes an ISBN barcode and ignores any other`() {
        assertEquals("9780306406157", Isbn.fromBarcode("9780306406157"))
        // A price sticker or a shop's own label.
        assertNull(Isbn.fromBarcode("8991234567890"))
        assertNull(Isbn.fromBarcode("2000001234567"))
        assertNull(Isbn.fromBarcode(null))
    }
}

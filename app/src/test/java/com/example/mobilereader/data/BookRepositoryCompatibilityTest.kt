package com.example.mobilereader.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookRepositoryCompatibilityTest {
    @Test
    fun legacyReadingModeDoesNotPreventLoadingBookOrOtherReaderSettings() {
        val storedBooks = """
            [
                {
                    "id": "legacy-book",
                    "title": "Saved book",
                    "format": "EPUB",
                    "addedAtMillis": 1234,
                    "readerSettings": {
                        "readingMode": "SCROLL",
                        "fontSizeSp": 27.0,
                        "pageMargins": "WIDE",
                        "justify": false
                    }
                }
            ]
        """.trimIndent()

        val books = decodeStoredBooks(storedBooks)

        assertEquals(1, books.size)
        assertEquals("legacy-book", books.single().id)
        val settings = books.single().readerSettings
        assertNotNull(settings)
        assertEquals(27f, settings!!.fontSizeSp)
        assertEquals(PageMargins.WIDE, settings.pageMargins)
        assertFalse(settings.justify)
    }

    @Test
    fun legacyPixelOffsetIsIgnoredWhileTextPositionSurvives() {
        val storedBooks = """
            [
                {
                    "id": "legacy-progress",
                    "title": "Saved progress",
                    "format": "EPUB",
                    "addedAtMillis": 1234,
                    "progress": {
                        "chapterIndex": 2,
                        "elementIndex": 14,
                        "scrollOffset": 317,
                        "textPosition": {
                            "chapterIndex": 2,
                            "elementIndex": 4,
                            "charOffset": 173,
                            "elementFingerprint": "saved-fingerprint"
                        }
                    }
                }
            ]
        """.trimIndent()

        val book = decodeStoredBooks(storedBooks).single()

        assertEquals(14, book.progress.elementIndex)
        assertEquals(2, book.progress.textPosition?.chapterIndex)
        assertEquals(4, book.progress.textPosition?.elementIndex)
        assertEquals(173, book.progress.textPosition?.charOffset)
        assertEquals("saved-fingerprint", book.progress.textPosition?.elementFingerprint)
        val rewrittenBooks = bookRepositoryJson.encodeToString(listOf(book))
        assertFalse(rewrittenBooks.contains("\"scrollOffset\""))
    }
}

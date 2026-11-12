package com.example.mobilereader

import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialBookSelectionTest {
    private val older = Book(
        id = "older",
        title = "Older",
        format = BookFormat.EPUB,
        addedAtMillis = 100L,
        lastOpenedAtMillis = 300L,
    )
    private val newer = Book(
        id = "newer",
        title = "Newer",
        format = BookFormat.EPUB,
        addedAtMillis = 200L,
        lastOpenedAtMillis = 400L,
    )

    @Test
    fun coldStartOpensLastBook() {
        assertEquals(BookRoute.Reading("newer"), initialBookRoute(false, null, null, listOf(older, newer)))
    }

    @Test
    fun recreationKeepsSavedBook() {
        assertEquals(BookRoute.Reading("older"), initialBookRoute(true, "older", "reading", listOf(older, newer)))
    }

    @Test
    fun recreationDoesNotOpenMissingBook() {
        assertEquals(BookRoute.Library, initialBookRoute(true, "removed", "reading", listOf(older, newer)))
    }

    @Test
    fun recreationKeepsLibraryOpen() {
        assertEquals(BookRoute.Library, initialBookRoute(true, null, "library", listOf(older, newer)))
    }

    @Test
    fun emptyLibraryHasNoInitialBook() {
        assertEquals(BookRoute.Library, initialBookRoute(false, null, null, emptyList()))
    }

    @Test
    fun pickerRecreationKeepsLoadingRoute() {
        assertEquals(BookRoute.Picking, initialBookRoute(true, null, "picking", listOf(older, newer)))
    }

    @Test
    fun interruptedImportReturnsToLibraryOnRecreation() {
        assertEquals(BookRoute.Library, initialBookRoute(true, null, "importing", listOf(older, newer)))
    }
}

package com.example.mobilereader.ui.preview

import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookFormat
import com.example.mobilereader.data.model.ReadingProgress
import com.example.mobilereader.data.model.ReadingStatus
import com.example.mobilereader.data.model.Shelf


internal object PreviewBooks {
    val current = Book(
        id = "preview-current",
        title = "The Hound of the Baskervilles",
        author = "Arthur Conan Doyle",
        format = BookFormat.EPUB,
        fileName = "preview.epub",
        addedAtMillis = 1_700_000_000_000L,
        lastOpenedAtMillis = 1_700_010_000_000L,
        progress = ReadingProgress(fraction = 0.42f, pagesLeftInChapter = 9, totalPages = 240),
        startedAtMillis = 1_700_000_000_000L,
        readingSeconds = 5_460,
        status = ReadingStatus.READING,
        description = "A mysterious legend draws Sherlock Holmes to the moors of Devonshire.",
        language = "en",
    )

    val second = Book(
        id = "preview-second",
        title = "Pride and Prejudice",
        author = "Jane Austen",
        format = BookFormat.EPUB,
        fileName = "second.epub",
        addedAtMillis = 1_699_000_000_000L,
        progress = ReadingProgress(fraction = 0.12f),
        status = ReadingStatus.READING,
    )

    val third = Book(
        id = "preview-third",
        title = "Dracula",
        author = "Bram Stoker",
        format = BookFormat.EPUB,
        fileName = "third.epub",
        addedAtMillis = 1_698_000_000_000L,
    )

    val all = listOf(current, second, third)

    val shelf = Shelf(
        id = "preview-shelf",
        name = "Classics",
        bookIds = listOf(second.id, third.id),
        createdAtMillis = 1_700_000_000_000L,
    )
}

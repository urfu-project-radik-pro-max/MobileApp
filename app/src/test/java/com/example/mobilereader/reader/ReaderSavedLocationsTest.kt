package com.example.mobilereader.reader

import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookContent
import com.example.mobilereader.data.model.BookFormat
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.data.model.ReadingProgress
import com.example.mobilereader.data.parser.EpubParser
import com.example.mobilereader.ui.reader.ReaderState
import com.example.mobilereader.ui.reader.bookmarkAnchor
import com.example.mobilereader.ui.reader.chapterReaderItems
import com.example.mobilereader.ui.reader.quoteRange
import com.example.mobilereader.ui.reader.resolvePosition
import com.example.mobilereader.ui.reader.savedPosition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderSavedLocationsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `text positions survive epub reparse and persisted record round trip`() {
        val epub = syntheticEpub()
        val first = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("first")))
        val progress = ReadingProgress(textPosition = first.savedPosition(1, 6))
        val bookmark = Bookmark("bookmark", 1, 0, "First paragraph", 1,
            textPosition = first.savedPosition(1, 6))
        val quote = Quote("quote", "Second", 0, 1,
            startPosition = first.savedPosition(2, 0),
            endPosition = first.savedPosition(2, 6))
        val storedProgress = Json.decodeFromString<ReadingProgress>(Json.encodeToString(progress))
        val storedBookmark = Json.decodeFromString<Bookmark>(Json.encodeToString(bookmark))
        val storedQuote = Json.decodeFromString<Quote>(Json.encodeToString(quote))
        val reopened = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("reopened")))

        assertEquals(1, reopened.resolvePosition(storedProgress.textPosition)?.itemIndex)
        assertEquals(6, reopened.resolvePosition(storedProgress.textPosition)?.charOffset)
        assertEquals(reopened.resolvePosition(storedProgress.textPosition),
            reopened.bookmarkAnchor(storedBookmark))
        assertEquals("Second", reopened.quoteRange(storedQuote)?.let { range ->
            com.example.mobilereader.ui.reader.selection.SelectionText.extract(
                range, reopened.items.size) { reopened.items[it].element }
        })
    }

    @Test
    fun `changed text makes saved locations unavailable`() {
        val epub = syntheticEpub()
        val first = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("original")))
        val originalPosition = first.savedPosition(1, 6)
        val changedItems = first.items.toMutableList()
        changedItems[1] = com.example.mobilereader.ui.reader.ReaderItem(0,
            com.example.mobilereader.data.model.ContentElement.Paragraph(
                androidx.compose.ui.text.AnnotatedString("Different paragraph.")))
        val changed = ReaderState.Ready(first.book, changedItems, first.chapterStarts,
            first.chapterTitles, first.chapterDepths, emptyMap())
        assertNull(changed.resolvePosition(originalPosition))
    }

    private fun ready(content: BookContent): ReaderState.Ready {
        val chapter = content.chapters.single()
        return ReaderState.Ready(
            book = Book("book", "Synthetic", format = BookFormat.EPUB, addedAtMillis = 1),
            items = chapterReaderItems(0, chapter.elements),
            chapterStarts = listOf(0),
            chapterTitles = listOf(chapter.title),
            chapterDepths = listOf(chapter.depth),
            notes = emptyMap(),
        )
    }

    private fun syntheticEpub(): File {
        val file = temporaryFolder.newFile("positions.epub")
        ZipOutputStream(file.outputStream()).use { archive ->
            fun entry(path: String, content: String) {
                archive.putNextEntry(ZipEntry(path))
                archive.write(content.toByteArray())
                archive.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml",
                "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>")
            entry("OPS/book.opf", "<package xmlns='http://www.idpf.org/2007/opf'>" +
                "<metadata><title>Synthetic</title></metadata><manifest>" +
                "<item id='chapter' href='chapter.xhtml' media-type='application/xhtml+xml'/>" +
                "</manifest><spine><itemref idref='chapter'/></spine></package>")
            entry("OPS/chapter.xhtml", "<html xmlns='http://www.w3.org/1999/xhtml'>" +
                "<body><h2>Heading</h2><p>First paragraph remains.</p>" +
                "<p>Second paragraph for selection.</p></body></html>")
        }
        return file
    }
}

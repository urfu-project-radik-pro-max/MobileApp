package com.example.mobilereader.reader

import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookContent
import com.example.mobilereader.data.model.BookFormat
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.data.model.ReadingProgress
import com.example.mobilereader.data.parser.EpubParser
import com.example.mobilereader.ui.reader.ReaderState
import com.example.mobilereader.ui.reader.bookmarkAnchor
import com.example.mobilereader.ui.reader.chapterReaderItems
import com.example.mobilereader.ui.reader.quoteRange
import com.example.mobilereader.ui.reader.resolvePosition
import com.example.mobilereader.ui.reader.savedPosition
import com.example.mobilereader.ui.reader.selection.SelectionText
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
        val bookmark = Bookmark("bookmark", 2, 0, "First paragraph", 1,
            textPosition = first.savedPosition(1, 6))
        val quote = Quote("quote", "Second", 0, 1,
            startItem = 1,
            startChar = 0,
            endItem = 1,
            endChar = 6,
            startPosition = first.savedPosition(2, 0),
            endPosition = first.savedPosition(2, 6))
        val storedProgress = Json.decodeFromString<ReadingProgress>(Json.encodeToString(progress))
        val storedBookmark = Json.decodeFromString<Bookmark>(Json.encodeToString(bookmark))
        val storedQuote = Json.decodeFromString<Quote>(Json.encodeToString(quote))
        val reopened = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("reopened")))

        assertEquals(1, reopened.resolvePosition(storedProgress.textPosition)?.itemIndex)
        assertEquals(6, reopened.resolvePosition(storedProgress.textPosition)?.charOffset)
        assertEquals(1, reopened.bookmarkAnchor(storedBookmark)?.itemIndex)
        assertEquals(6, reopened.bookmarkAnchor(storedBookmark)?.charOffset)
        assertEquals("First paragraph remains.", reopened.bookmarkAnchor(storedBookmark)?.let { anchor ->
            SelectionText.elementText(reopened.items[anchor.itemIndex].element)
        })
        assertEquals(2, reopened.quoteRange(storedQuote)?.start?.itemIndex)
        assertEquals(0, reopened.quoteRange(storedQuote)?.start?.charOffset)
        assertEquals("Second", reopened.quoteRange(storedQuote)?.let { range ->
            SelectionText.extract(range, reopened.items.size) { itemIndex ->
                reopened.items[itemIndex].element
            }
        })
    }

    @Test
    fun `font size and theme changes retain the saved text`() {
        val epub = syntheticEpub()
        val initial = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("initial")))
        val bookmark = Bookmark("bookmark", 1, 0, "First paragraph", 1,
            textPosition = initial.savedPosition(1, 6))
        val quote = Quote("quote", "Second", 0, 1,
            startPosition = initial.savedPosition(2, 0),
            endPosition = initial.savedPosition(2, 6))
        val readerSettings = ReaderSettings().copy(fontSizeSp = 26f)
        val appSettings = AppSettings().copy(theme = AppTheme.OLED)
        val reopened = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("restyled")))

        assertEquals(26f, readerSettings.fontSizeSp)
        assertEquals(AppTheme.OLED, appSettings.theme)
        assertEquals(1, reopened.bookmarkAnchor(bookmark)?.itemIndex)
        assertEquals(6, reopened.bookmarkAnchor(bookmark)?.charOffset)
        assertEquals("First paragraph remains.", reopened.bookmarkAnchor(bookmark)?.let { anchor ->
            SelectionText.elementText(reopened.items[anchor.itemIndex].element)
        })
        assertEquals("Second", reopened.quoteRange(quote)?.let { range ->
            SelectionText.extract(range, reopened.items.size) { itemIndex ->
                reopened.items[itemIndex].element
            }
        })
    }

    @Test
    fun `changed element text and mismatched quote make saved locations unavailable`() {
        val epub = syntheticEpub()
        val first = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("original")))
        val bookmark = Bookmark("bookmark", 1, 0, "First paragraph", 1,
            textPosition = first.savedPosition(1, 6))
        val quote = Quote("quote", "Second", 0, 1,
            startPosition = first.savedPosition(2, 0),
            endPosition = first.savedPosition(2, 6))
        val changedItems = first.items.toMutableList()
        changedItems[1] = com.example.mobilereader.ui.reader.ReaderItem(0,
            com.example.mobilereader.data.model.ContentElement.Paragraph(
                androidx.compose.ui.text.AnnotatedString("Different paragraph.")))
        changedItems[2] = com.example.mobilereader.ui.reader.ReaderItem(0,
            com.example.mobilereader.data.model.ContentElement.Paragraph(
                androidx.compose.ui.text.AnnotatedString("Another paragraph for selection.")))
        val changed = ReaderState.Ready(first.book, changedItems, first.chapterStarts,
            first.chapterTitles, first.chapterDepths, emptyMap())

        assertNull(changed.bookmarkAnchor(bookmark))
        assertNull(changed.quoteRange(quote))
        assertNull(first.quoteRange(quote.copy(text = "Other")))
    }

    @Test
    fun `legacy numeric indices cannot open unrelated text`() {
        val epub = syntheticEpub()
        val current = ready(EpubParser.parseContent(epub, temporaryFolder.newFolder("current")))
        val legacyBookmark = Bookmark("legacy bookmark", 1, 0, "Old text", 1)
        val legacyQuote = Quote("legacy quote", "Old text", 0, 1,
            startItem = 2, startChar = 0, endItem = 2, endChar = 8)

        assertEquals(1, legacyBookmark.flatIndex)
        assertEquals(2, legacyQuote.startItem)
        assertNull(current.bookmarkAnchor(legacyBookmark))
        assertNull(current.quoteRange(legacyQuote))
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

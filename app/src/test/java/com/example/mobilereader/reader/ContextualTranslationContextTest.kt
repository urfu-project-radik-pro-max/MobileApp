package com.example.mobilereader.reader

import androidx.compose.ui.text.AnnotatedString
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.BookPage
import com.example.mobilereader.ui.reader.ContextualTranslationContextBuilder
import com.example.mobilereader.ui.reader.PagePart
import com.example.mobilereader.ui.reader.ReaderItem
import com.example.mobilereader.ui.reader.selection.BookAnchor
import com.example.mobilereader.ui.reader.selection.BookSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualTranslationContextTest {
    @Test
    fun includesSelectedPageAndOnePrecedingPage() {
        val items = listOf(
            paragraph("first page"),
            paragraph("second page"),
            paragraph("selected page"),
            paragraph("following page"),
        )
        val pages = items.mapIndexed { index, item ->
            BookPage(
                parts = listOf(PagePart(index, item.element)),
                firstItemIndex = index,
            )
        }
        val selection = BookSelection(BookAnchor(2, 0), BookAnchor(2, 8))

        val result = ContextualTranslationContextBuilder.build(items, selection, 2, pages)

        assertEquals("selected", result.selectedText)
        assertEquals("selected page", result.surroundingParagraph)
        assertEquals("second page\n\nselected page", result.context)
        assertFalse(result.context.contains("first page"))
        assertFalse(result.context.contains("following page"))
    }

    @Test
    fun keepsWholeParagraphWhenSelectionStartsOnLaterPage() {
        val items = listOf(paragraph("The long paragraph continues across pages."))
        val pages = listOf(
            BookPage(
                parts = listOf(PagePart(0, items[0].element, charStart = 0, charEnd = 18)),
                firstItemIndex = 0,
                firstCharOffset = 0,
            ),
            BookPage(
                parts = listOf(PagePart(0, items[0].element, charStart = 18, charEnd = 42)),
                firstItemIndex = 0,
                firstCharOffset = 18,
            ),
        )
        val selection = BookSelection(BookAnchor(0, 19), BookAnchor(0, 28))

        val result = ContextualTranslationContextBuilder.build(items, selection, 1, pages)

        assertEquals("The long paragraph continues across pages.", result.surroundingParagraph)
        assertEquals(result.surroundingParagraph, result.context)
        assertTrue(result.selectedText.isNotBlank())
    }

    @Test
    fun selectionEndingAtNextParagraphStartUsesPreviousPage() {
        val items = listOf(
            paragraph("first page"),
            paragraph("selected page"),
            paragraph("next page"),
        )
        val pages = items.mapIndexed { index, item ->
            BookPage(
                parts = listOf(PagePart(index, item.element)),
                firstItemIndex = index,
            )
        }
        val selection = BookSelection(BookAnchor(1, 0), BookAnchor(2, 0))

        val result = ContextualTranslationContextBuilder.build(items, selection, 1, pages)

        assertEquals("selected page", result.selectedText)
        assertEquals("selected page", result.context)
    }

    private fun paragraph(text: String): ReaderItem = ReaderItem(
        chapterIndex = 0,
        element = ContentElement.Paragraph(AnnotatedString(text)),
    )
}
